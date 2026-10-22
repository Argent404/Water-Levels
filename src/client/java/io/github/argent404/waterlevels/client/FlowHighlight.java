package io.github.argent404.waterlevels.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.WaterLevels;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Water outlines shown while holding the wand. The outline key cycles through:
 *
 * <ul>
 *   <li>flow view: flowing water (orange) and still water sloping into a different height (yellow);</li>
 *   <li>vanilla view: still surface water with no level yet (blue);</li>
 * </ul>
 *
 * <p>Outlines are drawn as a grid one pixel above the water block, in plain GL lines (the vanilla
 * line shader shifts lines toward the camera). Lines behind terrain are drawn faintly. The area
 * is rescanned every RESCAN_TICKS ticks, a few layers per tick.
 */
final class FlowHighlight {
	private FlowHighlight() {}

	enum Mode { OFF, FLOW, VANILLA }

	/** One outlined water block, with its colour and level (0 = none). */
	private record Outline(int x, int y, int z, float[] rgb, int level) {}

	/** A finished scan: line segments (6 coordinates each) and their colours (3 each). */
	private record Drawn(List<Outline> tiles, double[] lines, float[] colors) {
		static final Drawn EMPTY = new Drawn(List.of(), new double[0], new float[0]);
	}

	private static final int RADIUS = 30, UP = 8, DOWN = 40, MAX_OUTLINES = 12000, RESCAN_TICKS = 10;
	private static final int LAYERS = UP + DOWN + 1, LAYERS_PER_TICK = (LAYERS + RESCAN_TICKS - 1) / RESCAN_TICKS;
	/** Outline height: one pixel into the block above the water. */
	private static final float OUTLINE_HEIGHT = 1 + 1 / 32f;
	/** Alpha of lines behind terrain. */
	private static final float HIDDEN_ALPHA = 0.3f;

	private static final float[] ORANGE = {1f, 0.45f, 0.1f}, YELLOW = {1f, 0.9f, 0.1f}, BLUE = {0.25f, 0.55f, 1f};

	private static Mode mode = Mode.FLOW;
	/** The last finished scan, which is what gets drawn. */
	private static Drawn drawn = Drawn.EMPTY;
	/** The scan in progress; null when idle. */
	private static List<Outline> pending;
	private static BlockPos pendingCenter;
	private static int pendingLayer;
	/** Whether outlines were active last tick. */
	private static boolean active;
	/** Modes whose colour legend was already shown this session. */
	private static final Set<Mode> explained = EnumSet.noneOf(Mode.class);

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (WandKeys.TOGGLE_OUTLINES.consumeClick()) {
				mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
				if (client.player == null || client.level == null) continue;
				drawn = mode == Mode.OFF ? Drawn.EMPTY : build(scanAll(client));
				pending = null;
				active = mode != Mode.OFF && WaterLevels.holdsWand(client.player);
				announce(client);
			}
			if (mode == Mode.OFF || client.player == null || client.level == null || !WaterLevels.holdsWand(client.player)) {
				drawn = Drawn.EMPTY;
				pending = null;
				active = false;
				return;
			}
			if (!active) {
				active = true;
				drawn = build(scanAll(client)); // show something immediately
				startScan(client);
				return;
			}
			if (pending == null) startScan(client);
			for (int i = 0; i < LAYERS_PER_TICK && pending != null; i++) {
				scanLayer(client.level, pendingCenter, UP - pendingLayer, pending);
				if (++pendingLayer >= LAYERS) {
					drawn = build(pending);
					pending = null;
				}
			}
		});

		// After translucent geometry, so water doesn't tint the lines.
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			Drawn current = drawn;
			if (current.tiles().isEmpty()) return;
			Vec3 cam = context.camera().getPosition();
			Matrix4f m = context.matrixStack().last().pose();

			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.depthMask(false);
			RenderSystem.lineWidth(2f);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			lines(current, m, cam, 1f);
			RenderSystem.depthFunc(GL11.GL_GREATER);
			lines(current, m, cam, HIDDEN_ALPHA);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.lineWidth(1f);
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		});
	}

	private static void lines(Drawn d, Matrix4f m, Vec3 cam, float alpha) {
		double[] l = d.lines();
		float[] c = d.colors();
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
		for (int i = 0, n = c.length / 3; i < n; i++) {
			vertex(buffer, m, l[i * 6] - cam.x, l[i * 6 + 1] - cam.y, l[i * 6 + 2] - cam.z, c, i, alpha);
			vertex(buffer, m, l[i * 6 + 3] - cam.x, l[i * 6 + 4] - cam.y, l[i * 6 + 5] - cam.z, c, i, alpha);
		}
		Tesselator.getInstance().end();
	}

	/**
	 * A camera-relative vertex, moved about 1 cm toward the camera so lines on a block face don't
	 * z-fight with it.
	 */
	private static void vertex(BufferBuilder buffer, Matrix4f m, double x, double y, double z, float[] c, int i, float alpha) {
		double distance = Math.sqrt(x * x + y * y + z * z);
		double scale = distance < 0.05 ? 1 : 1 - (0.01 + distance * 0.0006) / distance;
		buffer.vertex(m, (float) (x * scale), (float) (y * scale), (float) (z * scale))
				.color(c[i * 3], c[i * 3 + 1], c[i * 3 + 2], alpha).endVertex();
	}

	/**
	 * Converts tiles to grid lines. Each tile draws its north and west edges, and its south and
	 * east edges only where the neighbour differs, so shared edges are drawn once.
	 */
	private static Drawn build(List<Outline> tiles) {
		if (tiles.isEmpty()) return Drawn.EMPTY;
		Map<Long, Outline> at = new HashMap<>(tiles.size() * 2);
		for (Outline o : tiles) at.put(BlockPos.asLong(o.x(), o.y(), o.z()), o);
		double[] lines = new double[tiles.size() * 4 * 6];
		float[] colors = new float[tiles.size() * 4 * 3];
		int n = 0;
		for (Outline o : tiles) {
			int x = o.x(), z = o.z();
			double y = o.y() + OUTLINE_HEIGHT;
			n = segment(lines, colors, n, o, x, y, z, x + 1, z); // north
			n = segment(lines, colors, n, o, x, y, z, x, z + 1); // west
			if (!same(o, at.get(BlockPos.asLong(x, o.y(), z + 1)))) n = segment(lines, colors, n, o, x, y, z + 1, x + 1, z + 1); // south
			if (!same(o, at.get(BlockPos.asLong(x + 1, o.y(), z)))) n = segment(lines, colors, n, o, x + 1, y, z, x + 1, z + 1); // east
		}
		return new Drawn(tiles, Arrays.copyOf(lines, n * 6), Arrays.copyOf(colors, n * 3));
	}

	private static int segment(double[] lines, float[] colors, int n, Outline o, double x0, double y, double z0, double x1, double z1) {
		int l = n * 6, c = n * 3;
		lines[l] = x0; lines[l + 1] = y; lines[l + 2] = z0;
		lines[l + 3] = x1; lines[l + 4] = y; lines[l + 5] = z1;
		colors[c] = o.rgb()[0]; colors[c + 1] = o.rgb()[1]; colors[c + 2] = o.rgb()[2];
		return n + 1;
	}

	private static boolean same(Outline a, Outline b) {
		return b != null && a.rgb() == b.rgb();
	}

	/** Shows the mode and tile count; the first time per mode, also the colour legend. */
	private static void announce(Minecraft client) {
		int count = drawn.tiles().size();
		boolean first = explained.add(mode);
		String bar = switch (mode) {
			case OFF -> "Water outlines: off";
			case FLOW -> "Water outlines: flow view (" + count + ")" + (first ? "  §6orange§r = flowing, §eyellow§r = still but sloping" : "");
			case VANILLA -> "Water outlines: vanilla view (" + count + ")" + (first ? "  §9blue§r = no level set yet" : "");
		};
		client.player.displayClientMessage(Component.literal(bar), true);
	}

	private static void startScan(Minecraft client) {
		pending = new ArrayList<>();
		pendingCenter = client.player.blockPosition();
		pendingLayer = 0;
	}

	private static List<Outline> scanAll(Minecraft client) {
		List<Outline> out = new ArrayList<>();
		BlockPos center = client.player.blockPosition();
		for (int dy = UP; dy >= -DOWN; dy--) scanLayer(client.level, center, dy, out);
		return out;
	}

	private static void scanLayer(ClientLevel world, BlockPos center, int dy, List<Outline> out) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), other = new BlockPos.MutableBlockPos();
		int y = center.getY() + dy;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (out.size() >= MAX_OUTLINES) return;
				pos.set(center.getX() + dx, y, center.getZ() + dz);
				FluidState fluid = world.getFluidState(pos);
				if (fluid.isEmpty() || !fluid.is(FluidTags.WATER)) continue;
				if (isWater(world, other.setWithOffset(pos, Direction.UP))) continue; // not a surface
				if (buried(world, pos, other)) continue;
				Outline o = outline(world, pos, fluid, other);
				if (o != null) out.add(o);
			}
		}
	}

	/** Whether the block above and all four sides are full blocks. */
	private static boolean buried(ClientLevel world, BlockPos pos, BlockPos.MutableBlockPos other) {
		if (!world.getBlockState(other.setWithOffset(pos, Direction.UP)).isSolidRender(world, other)) return false;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!world.getBlockState(other.setWithOffset(pos, side)).isSolidRender(world, other)) return false;
		}
		return true;
	}

	private static Outline outline(ClientLevel world, BlockPos pos, FluidState fluid, BlockPos.MutableBlockPos other) {
		float level = Levels.levelHeight(world, pos);
		switch (mode) {
			case FLOW -> {
				if (!fluid.isSource()) return of(pos, ORANGE, 0);
				return slopes(world, pos, height(world, pos, level), other) ? of(pos, YELLOW, 0) : null;
			}
			case VANILLA -> {
				return fluid.isSource() && level < 0 ? of(pos, BLUE, 0) : null;
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
	private static boolean slopes(ClientLevel world, BlockPos pos, float own, BlockPos.MutableBlockPos other) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				other.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
				FluidState n = world.getFluidState(other);
				if (n.isEmpty() || !n.is(FluidTags.WATER)) continue;
				int x = other.getX(), y = other.getY(), z = other.getZ();
				if (isWater(world, other.set(x, y + 1, z))) return true;
				other.set(x, y, z);
				if (Math.abs(height(world, other, Levels.levelHeight(world, other)) - own) > 0.01f) return true;
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
