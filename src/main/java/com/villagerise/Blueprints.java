package com.villagerise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Map;

/** Generadores de construcciones. Cada uno encola bloques (de abajo hacia arriba) en la aldea. */
public final class Blueprints {
    private Blueprints() {}

    private static final BlockState BRICK = Blocks.STONE_BRICKS.defaultBlockState();

    private static void foundation(ServerLevel lvl, Village v, int x, int z, int yTop, BlockState fill) {
        int h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = h; y <= yTop; y++) v.put(new BlockPos(x, y, z), fill);
    }

    // ---------------- casa 5x5 con cama ----------------
    public static boolean house(ServerLevel lvl, Village v) {
        BlockPos o = v.findSite(lvl, 2, 9, 20, 3, -1);
        if (o == null) return false;
        int cx = o.getX(), cz = o.getZ(), y0 = o.getY();
        for (int y = y0; y <= y0 + 4; y++)
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) v.clear(lvl, new BlockPos(cx + dx, y, cz + dz));
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                foundation(lvl, v, cx + dx, cz + dz, y0 - 2, Blocks.COBBLESTONE.defaultBlockState());
                v.put(new BlockPos(cx + dx, y0 - 1, cz + dz), planks);
            }
        for (int y = 0; y <= 2; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;      // solo el perímetro
                    if (dx == 0 && dz == 2 && y <= 1) continue;                // hueco de la puerta
                    BlockState s;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) s = Blocks.OAK_LOG.defaultBlockState();
                    else if (y == 1 && (dx == 0 || dz == 0)) s = Blocks.GLASS_PANE.defaultBlockState();
                    else s = planks;
                    v.put(new BlockPos(cx + dx, y0 + y, cz + dz), s);
                }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        v.put(new BlockPos(cx, y0, cz + 2), door);
        v.put(new BlockPos(cx, y0 + 1, cz + 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        v.put(new BlockPos(cx, y0, cz), bed.setValue(BedBlock.PART, BedPart.FOOT));
        v.put(new BlockPos(cx, y0, cz - 1), bed.setValue(BedBlock.PART, BedPart.HEAD));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) v.put(new BlockPos(cx + dx, y0 + 3, cz + dz), planks);
        return true;
    }

    // ---------------- huerto 5x5 con agua central ----------------
    public static boolean farm(ServerLevel lvl, Village v) {
        BlockPos o = v.findSite(lvl, 2, 6, 16, 1, -1);
        if (o == null) return false;
        int cx = o.getX(), cz = o.getZ(), y0 = o.getY();
        for (int y = y0; y <= y0 + 1; y++)
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) v.clear(lvl, new BlockPos(cx + dx, y, cz + dz));
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx, z = cz + dz;
                foundation(lvl, v, x, z, y0 - 2, Blocks.DIRT.defaultBlockState());
                if (dx == 0 && dz == 0) v.put(new BlockPos(x, y0 - 1, z), Blocks.WATER.defaultBlockState());
                else {
                    v.put(new BlockPos(x, y0 - 1, z), Blocks.FARMLAND.defaultBlockState());
                    v.put(new BlockPos(x, y0, z), Blocks.WHEAT.defaultBlockState());
                }
            }
        v.farms.add(o.asLong());
        return true;
    }

    // ---------------- torre / torreón con escalera y torretas ----------------
    public static boolean tower(ServerLevel lvl, Village v, int size, int h, boolean keep) {
        int half = size / 2;
        BlockPos o = keep ? v.findSite(lvl, half, 0, 8, 3, -1)
                          : v.findSite(lvl, half, 14, 24, 3, v.mostAttackedSide());
        if (o == null) return false;
        int cx = o.getX(), cz = o.getZ(), y0 = o.getY();
        for (int y = y0; y <= y0 + h + 2; y++)
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) v.clear(lvl, new BlockPos(cx + dx, y, cz + dz));
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++) {
                foundation(lvl, v, cx + dx, cz + dz, y0 - 2, BRICK);
                v.put(new BlockPos(cx + dx, y0 - 1, cz + dz), BRICK);
            }
        int midFloor = h >= 12 ? h / 2 : -1;
        for (int yy = 0; yy < h; yy++)
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) {
                    boolean ring = Math.abs(dx) == half || Math.abs(dz) == half;
                    boolean floor = (yy == h - 1 || yy == midFloor);
                    if (!ring && !floor) continue;
                    if (ring && dx == 0 && dz == half && yy < 2) continue;   // puerta
                    if (!ring && dx == 0 && dz == -half + 1) continue;       // hueco de la escalera
                    v.put(new BlockPos(cx + dx, y0 + yy, cz + dz), BRICK);
                }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int yy = 0; yy < h; yy++) v.put(new BlockPos(cx, y0 + yy, cz - half + 1), ladder);
        // torretas en el centro de cada lado + almenas
        BlockPos[] tp = {new BlockPos(cx, y0 + h, cz - half), new BlockPos(cx, y0 + h, cz + half),
                         new BlockPos(cx + half, y0 + h, cz), new BlockPos(cx - half, y0 + h, cz)};
        Direction[] td = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++) {
                if (Math.abs(dx) != half && Math.abs(dz) != half) continue;
                BlockPos p = new BlockPos(cx + dx, y0 + h, cz + dz);
                boolean isTurret = false;
                for (BlockPos t : tp) if (t.equals(p)) isTurret = true;
                if (!isTurret && Math.floorMod(dx + dz, 2) == 0) v.put(p, BRICK);
                if (Math.abs(dx) == half && Math.abs(dz) == half) v.put(p.above(), BRICK);
            }
        for (int i = 0; i < 4; i++) {
            v.put(tp[i], Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, td[i]));
            v.turrets.add(tp[i].asLong());
        }
        if (keep) v.keeps++; else v.towers.add(o.asLong());
        return true;
    }

    // ---------------- muralla: un lado completo con puerta ----------------
    public static boolean wall(ServerLevel lvl, Village v) {
        final int R = 26;
        int side = -1, best = -1;
        for (int i = 0; i < 4; i++)
            if ((v.wallMask >> i & 1) == 0 && v.attacksBySide[i] > best) { best = v.attacksBySide[i]; side = i; }
        if (side < 0) return false;
        int cx = v.center.getX(), cz = v.center.getZ();
        for (int t = -R; t <= R; t++)
            if (!lvl.hasChunkAt(new BlockPos(wx(side, cx, cz, R, t), 64, wz(side, cx, cz, R, t)))) return false;
        for (int t = -R; t <= R; t++) {
            int x = wx(side, cx, cz, R, t), z = wz(side, cx, cz, R, t);
            int h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!lvl.getFluidState(new BlockPos(x, h - 1, z)).isEmpty()) continue;
            boolean gate = Math.abs(t) <= 1;
            for (int y = h - 1; y <= h + 3; y++) {
                if (gate && y >= h && y <= h + 2) continue;
                v.put(new BlockPos(x, y, z), BRICK);
            }
            if (Math.floorMod(t, 2) == 0) v.put(new BlockPos(x, h + 4, z), BRICK);
        }
        v.wallMask |= 1 << side;
        return true;
    }

    private static int wx(int side, int cx, int cz, int R, int t) {
        return switch (side) { case 0, 2 -> cx + t; case 1 -> cx + R; default -> cx - R; };
    }

    private static int wz(int side, int cx, int cz, int R, int t) {
        return switch (side) { case 0 -> cz - R; case 2 -> cz + R; default -> cz + t; };
    }

    // ---------------- mejora: casas de madera -> piedra (castillo) ----------------
    public static boolean upgrade(ServerLevel lvl, Village v) {
        if (v.stone < 40) return false;
        int n = 0;
        for (Map.Entry<Long, BlockState> e : new ArrayList<>(v.blueprint.entrySet())) {
            if (!e.getValue().is(BlockTags.PLANKS) || v.pending.contains(e.getKey())) continue;
            v.put(BlockPos.of(e.getKey()), BRICK);
            if (++n >= 60) break;
        }
        return n > 0;
    }
}
