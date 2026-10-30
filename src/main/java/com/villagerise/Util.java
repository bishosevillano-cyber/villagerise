package com.villagerise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class Util {
    private Util() {}

    /** 0=Norte 1=Este 2=Sur 3=Oeste */
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};

    public static int dirOf(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    /** Rol estable por aldeano: 0 = constructor, 1 = recolector, 2 = guerrero. */
    public static int role(Villager v) {
        return Math.floorMod(v.getUUID().hashCode(), 3);
    }

    public enum Cost { NONE, WOOD, STONE }

    public static Cost cost(BlockState s) {
        if (s.isAir()) return Cost.NONE;
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_FENCES))
            return Cost.WOOD;
        if (s.is(BlockTags.STONE_BRICKS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.DISPENSER)) return Cost.STONE;
        return Cost.NONE;
    }

    /** ¿Es un bloque que forma parte de una construcción (y por tanto se debe reparar)? */
    public static boolean structural(Level lvl, BlockPos p, BlockState s) {
        if (s.isAir()) return false;
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
                || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.BEDS)
                || s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.WALLS) || s.is(Blocks.COBBLESTONE)
                || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.LADDER) || s.is(Blocks.DISPENSER))
            return true;
        // Un tronco solo cuenta si toca una pared (para no "reparar" árboles).
        if (s.is(BlockTags.LOGS)) {
            for (Direction d : Direction.values()) {
                BlockState n = lvl.getBlockState(p.relative(d));
                if (n.is(BlockTags.PLANKS) || n.is(BlockTags.STONE_BRICKS)) return true;
            }
        }
        return false;
    }

    // ---- (de)serialización de BlockState como texto ----
    private static <T extends Comparable<T>> String val(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    public static String encode(BlockState s) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        for (Property<?> p : s.getProperties()) sb.append('|').append(p.getName()).append('=').append(val(s, p));
        return sb.toString();
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(x -> s.setValue(p, x)).orElse(s);
    }

    public static BlockState decode(String str) {
        String[] a = str.split("\\|");
        Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(a[0]));
        BlockState s = b.defaultBlockState();
        for (int i = 1; i < a.length; i++) {
            int e = a[i].indexOf('=');
            Property<?> p = b.getStateDefinition().getProperty(a[i].substring(0, e));
            if (p != null) s = with(s, p, a[i].substring(e + 1));
        }
        return s;
    }
}
