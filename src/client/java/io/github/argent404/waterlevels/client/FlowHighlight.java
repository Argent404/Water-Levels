package io.github.argent404.waterlevels.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.WaterLevels;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Water outlines shown while holding the wand. The outline key cycles through:
 *
 * <ul>
 *   <li>flow view: flowing water (orange) and still water sloping into a different height (yellow);</li>
 *   <li>level view: every still surface, leveled tiles in green with their level number, vanilla tiles in blue.</li>
 * </ul>
 *
 * <p>Outlines are drawn as a grid one pixel above the water block, in plain GL lines (the vanilla
 * line shader shifts lines toward the camera). Lines behind terrain are drawn faintly. The area
 * is rescanned every RESCAN_TICKS ticks, a few layers per tick.
 */
final class FlowHighlight {
	private FlowHighlight() {
	}

	enum Mode { OFF, FLOW, VIEW_LEVELS }

	/** One outlined water block, with its colour and level (0 = none). */
	private record Outline(int x, int y, int z, float[] rgb, int level) {
	}

	/** A finished scan: line segments (6 coordinates each) and their colours (3 each). */
	private record Drawn(List<Outline> tiles, double[] lines, float[] colors) {
		static final Drawn EMPTY = new Drawn(List.of(), new double[0], new float[0]);
	}

	private static final int SCAN_RADIUS = 30, SCAN_UP = 8, SCAN_DOWN = 60, MAX_OUTLINES = 12000, RESCAN_TICKS = 10;
	/** Below this depth, only water open to the sky is outlined. */
	private static final int OPEN_SKY_BELOW = 30;
	private static final int LAYERS = SCAN_UP + SCAN_DOWN + 1, LAYERS_PER_TICK = (LAYERS + RESCAN_TICKS - 1) / RESCAN_TICKS;
	/** Outline height: one pixel into the block above the water. */
	private static final float OUTLINE_HEIGHT = 1 + 1 / 32f;
	/** Alpha of lines behind terrain. */
	private static final float HIDDEN_ALPHA = 0.3f;
	/** Level numbers are only drawn within this distance. */
	private static final double LABEL_DISTANCE = 12;

	private static final Component FLOW_LEGEND = Component.translatable("message.waterlevels.outlines.flow_legend",
			Component.translatable("message.waterlevels.outlines.orange").withStyle(ChatFormatting.GOLD),
			Component.translatable("message.waterlevels.outlines.yellow").withStyle(ChatFormatting.YELLOW));
	private static final Component LEVELS_LEGEND = Component.translatable("message.waterlevels.outlines.levels_legend",
			Component.translatable("message.waterlevels.outlines.darker").withStyle(ChatFormatting.DARK_GREEN),
			Component.translatable("message.waterlevels.outlines.dark_blue").withStyle(ChatFormatting.DARK_BLUE));

	private static final float[] ORANGE = {1f, 0.45f, 0.1f}, YELLOW = {1f, 0.9f, 0.1f}, DARK_BLUE = {0.15f, 0.35f, 0.8f};

	/** Level view colours by level, from teal (1) to yellow-green (8). */
	private static final float[][] LEVEL_GREENS = new float[9][];
	static {
		for (int n = 1; n <= 8; n++) {
			float t = (n - 1) / 7f;
			int rgb = Mth.hsvToRgb(0.51f - 0.34f * t, 0.9f, 0.55f + 0.45f * t);
			LEVEL_GREENS[n] = new float[] {(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
		}
	}

	private static Mode mode = Mode.FLOW;
	/** The last finished scan, which is what gets drawn. */
	private static Drawn drawn = Drawn.EMPTY;
	/** The scan in progress; null when idle. */
	private static List<Outline> pending;
	private static BlockPos pendingCenter;
	private static int pendingLayer;
	private static boolean wasActive;
	/** Modes whose colour legend was already shown this session. */
	private static final Set<Mode> explained = EnumSet.noneOf(Mode.class);

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (WandKeys.TOGGLE_OUTLINES.consumeClick()) {
				mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];

				if (client.player == null || client.level == null) {
					continue;
				}

				drawn = mode == Mode.OFF ? Drawn.EMPTY : build(scanAll(client));
				pending = null;
				wasActive = mode != Mode.OFF && WaterLevels.holdsWand(client.player);
				announce(client);
			}

			if (mode == Mode.OFF
					|| client.player == null
					|| client.level == null
					|| !WaterLevels.holdsWand(client.player)) {
				drawn = Drawn.EMPTY;
				pending = null;
				wasActive = false;
				return;
			}

			if (!wasActive) {
				wasActive = true;
				drawn = build(scanAll(client)); // show something immediately
				startScan(client);
				return;
			}

			if (pending == null) {
				startScan(client);
			}

			for (int i = 0; i < LAYERS_PER_TICK && pending != null; i++) {
				scanLayer(client.level, pendingCenter, SCAN_UP - pendingLayer, pending);

				if (++pendingLayer >= LAYERS) {
					drawn = build(pending);
					pending = null;
				}
			}
		});

		// After translucent geometry, so water doesn't tint the lines.
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			Drawn current = drawn;

			if (current.tiles().isEmpty()) {
				return;
			}

			Vec3 cam = context.camera().getPosition();
			Matrix4f m = context.matrixStack().last().pose();

			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.depthMask(false);
			RenderSystem.lineWidth(2f);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			drawLines(current, m, cam, 1f);
			RenderSystem.depthFunc(GL11.GL_GREATER);
			drawLines(current, m, cam, HIDDEN_ALPHA);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.lineWidth(1f);
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		});

		// Drawn last so shader pack water doesn't cover the numbers.
		WorldRenderEvents.LAST.register(context -> {
			Drawn current = drawn;

			if (mode == Mode.VIEW_LEVELS && !current.tiles().isEmpty()) {
				drawLabels(context, current, context.camera().getPosition());
			}
		});
	}

	/**
	 * The lines are uploaded once per scan relative to a fixed origin and drawn each frame with a
	 * camera offset. They are scaled very slightly toward the camera so lines on a block face don't
	 * z-fight with it.
	 */
	private static VertexBuffer gpu;
	private static Drawn uploaded;
	private static double originX, originY, originZ;
	private static BufferBuilder builder;
	private static final float NUDGE = 1 - 0.0008f;

	private static void drawLines(Drawn d, Matrix4f m, Vec3 cam, float alpha) {
		if (d != uploaded) {
			upload(d);
		}

		Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(m).scale(NUDGE)
				.translate((float) (originX - cam.x), (float) (originY - cam.y), (float) (originZ - cam.z));
		RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
		gpu.bind();
		gpu.drawWithShader(view, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
		VertexBuffer.unbind();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private static void upload(Drawn d) {
		uploaded = d;
		double[] l = d.lines();
		float[] c = d.colors();
		originX = Math.floor(l[0]);
		originY = Math.floor(l[1]);
		originZ = Math.floor(l[2]);

		if (builder == null) {
			builder = new BufferBuilder(1 << 16);
		}

		builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

		for (int i = 0, n = c.length / 3; i < n; i++) {
			builder.vertex(l[i * 6] - originX, l[i * 6 + 1] - originY, l[i * 6 + 2] - originZ).color(c[i * 3], c[i * 3 + 1], c[i * 3 + 2], 1f).endVertex();
			builder.vertex(l[i * 6 + 3] - originX, l[i * 6 + 4] - originY, l[i * 6 + 5] - originZ).color(c[i * 3], c[i * 3 + 1], c[i * 3 + 2], 1f).endVertex();
		}

		if (gpu == null) {
			gpu = new VertexBuffer(VertexBuffer.Usage.STATIC);
		}

		gpu.bind();
		gpu.upload(builder.end());
		VertexBuffer.unbind();
	}

	/** Draws each leveled tile's number above it, facing the camera. */
	private static void drawLabels(WorldRenderContext context, Drawn d, Vec3 cam) {
		Minecraft client = Minecraft.getInstance();
		Font text = client.font;
		MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
		PoseStack poseStack = context.matrixStack();

		for (Outline o : d.tiles()) {
			if (o.level() == 0) {
				continue;
			}

			double x = o.x() + 0.5, y = o.y() + OUTLINE_HEIGHT + 0.25, z = o.z() + 0.5;

			if (cam.distanceToSqr(x, y, z) > LABEL_DISTANCE * LABEL_DISTANCE) {
				continue;
			}

			poseStack.pushPose();
			poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
			poseStack.mulPose(context.camera().rotation());
			poseStack.scale(-0.02f, -0.02f, 0.02f);
			String s = Integer.toString(o.level());
			text.drawInBatch(s, -text.width(s) / 2f, -4, 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource,
					Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
			poseStack.popPose();
		}

		bufferSource.endBatch();
	}

	/**
	 * Converts tiles to grid lines. Each tile draws its north and west edges, and its south and
	 * east edges only where the neighbour differs, so shared edges are drawn once.
	 */
	private static Drawn build(List<Outline> tiles) {
		if (tiles.isEmpty()) {
			return Drawn.EMPTY;
		}

		Map<Long, Outline> at = new HashMap<>(tiles.size() * 2);

		for (Outline o : tiles) {
			at.put(BlockPos.asLong(o.x(), o.y(), o.z()), o);
		}

		double[] lines = new double[tiles.size() * 4 * 6];
		float[] colors = new float[tiles.size() * 4 * 3];
		int n = 0;

		for (Outline o : tiles) {
			int x = o.x(), z = o.z();
			double y = o.y() + OUTLINE_HEIGHT;
			n = addSegment(lines, colors, n, o, x, y, z, x + 1, z); // north
			n = addSegment(lines, colors, n, o, x, y, z, x, z + 1); // west
			Outline south = at.get(BlockPos.asLong(x, o.y(), z + 1));
			Outline east = at.get(BlockPos.asLong(x + 1, o.y(), z));

			if (!isSameColour(o, south)) {
				n = addSegment(lines, colors, n, o, x, y, z + 1, x + 1, z + 1);
			}

			if (!isSameColour(o, east)) {
				n = addSegment(lines, colors, n, o, x + 1, y, z, x + 1, z + 1);
			}
		}

		return new Drawn(tiles, Arrays.copyOf(lines, n * 6), Arrays.copyOf(colors, n * 3));
	}

	private static int addSegment(double[] lines, float[] colors, int n, Outline o, double x0, double y, double z0, double x1, double z1) {
		int l = n * 6, c = n * 3;
		lines[l] = x0;
		lines[l + 1] = y;
		lines[l + 2] = z0;
		lines[l + 3] = x1;
		lines[l + 4] = y;
		lines[l + 5] = z1;
		System.arraycopy(o.rgb(), 0, colors, c, 3);
		return n + 1;
	}

	private static boolean isSameColour(Outline a, Outline b) {
		return b != null && a.rgb() == b.rgb();
	}

	/** Shows the mode and tile count; the first time per mode, also the colour legend. */
	private static void announce(Minecraft client) {
		int count = drawn.tiles().size();
		boolean first = explained.add(mode);
		Component bar = switch (mode) {
			case OFF -> Component.translatable("message.waterlevels.outlines.off");
			case FLOW -> Component.translatable("message.waterlevels.outlines.flow", count).append(first ? FLOW_LEGEND : Component.empty());
			case VIEW_LEVELS -> Component.translatable("message.waterlevels.outlines.levels", count).append(first ? LEVELS_LEGEND : Component.empty());
		};
		client.player.displayClientMessage(bar, true);
	}

	private static void startScan(Minecraft client) {
		pending = new ArrayList<>();
		pendingCenter = client.player.blockPosition();
		pendingLayer = 0;
	}

	private static List<Outline> scanAll(Minecraft client) {
		List<Outline> out = new ArrayList<>();
		BlockPos center = client.player.blockPosition();

		for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
			scanLayer(client.level, center, dy, out);
		}

		return out;
	}

	private static void scanLayer(ClientLevel world, BlockPos center, int dy, List<Outline> out) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), other = new BlockPos.MutableBlockPos();
		int y = center.getY() + dy;

		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
			for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
				if (out.size() >= MAX_OUTLINES) {
					return;
				}

				pos.set(center.getX() + dx, y, center.getZ() + dz);
				FluidState fluid = world.getFluidState(pos);

				if (fluid.isEmpty() || !fluid.is(FluidTags.WATER)) {
					continue;
				}

				// Not a surface.
				if (isWater(world, other.setWithOffset(pos, Direction.UP))) {
					continue;
				}

				if (isBuried(world, pos, other)) {
					continue;
				}

				boolean deep = dy < -OPEN_SKY_BELOW;

				if (deep && world.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) > y + 1) {
					continue;
				}

				Outline o = outline(world, pos, fluid, other);

				if (o != null) {
					out.add(o);
				}
			}
		}
	}

	/** Whether the block above and all four sides are full blocks. */
	private static boolean isBuried(ClientLevel world, BlockPos pos, BlockPos.MutableBlockPos other) {
		if (!world.getBlockState(other.setWithOffset(pos, Direction.UP)).isSolidRender(world, other)) {
			return false;
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!world.getBlockState(other.setWithOffset(pos, side)).isSolidRender(world, other)) {
				return false;
			}
		}

		return true;
	}

	private static Outline outline(ClientLevel world, BlockPos pos, FluidState fluid, BlockPos.MutableBlockPos other) {
		float level = Levels.levelHeight(world, pos);
		switch (mode) {
			case FLOW -> {
				if (!fluid.isSource()) {
					return of(pos, ORANGE, 0);
				}

				return isSloping(world, pos, height(world, pos, level), other) ? of(pos, YELLOW, 0) : null;
			}
			case VIEW_LEVELS -> {
				if (!fluid.isSource()) {
					return null;
				}

				if (level < 0) {
					return of(pos, DARK_BLUE, 0);
				}

				int n = Math.round(level * 8);
				return of(pos, LEVEL_GREENS[n], n);
			}
			default -> {
				return null;
			}
		}
	}

	/** Surface height as rendered. */
	private static float height(ClientLevel world, BlockPos pos, float level) {
		return level >= 0 ? level - Levels.VISUAL_DROP : world.getFluidState(pos).getHeight(world, pos);
	}

	/** Whether still water slopes: a neighbour at a different height, or water above a neighbour. */
	private static boolean isSloping(ClientLevel world, BlockPos pos, float own, BlockPos.MutableBlockPos other) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}

				other.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
				FluidState neighbour = world.getFluidState(other);

				if (neighbour.isEmpty() || !neighbour.is(FluidTags.WATER)) {
					continue;
				}

				int x = other.getX(), y = other.getY(), z = other.getZ();

				if (isWater(world, other.set(x, y + 1, z))) {
					return true;
				}

				other.set(x, y, z);

				if (Math.abs(height(world, other, Levels.levelHeight(world, other)) - own) > 0.01f) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isWater(ClientLevel world, BlockPos pos) {
		return world.getFluidState(pos).is(FluidTags.WATER);
	}

	private static Outline of(BlockPos pos, float[] rgb, int level) {
		return new Outline(pos.getX(), pos.getY(), pos.getZ(), rgb, level);
	}
}
