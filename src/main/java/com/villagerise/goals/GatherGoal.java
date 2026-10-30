package com.villagerise.goals;

import com.villagerise.Util;
import com.villagerise.Village;
import com.villagerise.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/** Los recolectores (rol 1) cosechan cultivos, talan árboles y pican piedra para la aldea. */
public class GatherGoal extends Goal {
    private final Villager v;
    private Village village;
    private BlockPos target;
    private int type; // 0 cultivo, 1 madera, 2 piedra
    private int ticks;

    public GatherGoal(Villager v) {
        this.v = v;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (Util.role(v) != 1 || v.isBaby() || !(v.level() instanceof ServerLevel l) || l.isDarkOutside()) return false;
        if (v.getRandom().nextInt(40) != 0 || v.getTarget() != null) return false;
        village = VillageManager.villageAt(l, v.blockPosition(), false);
        if (village == null) return false;
        target = find(l);
        return target != null;
    }

    private BlockPos find(ServerLevel lvl) {
        RandomSource r = v.getRandom();
        BlockPos o = v.blockPosition();
        boolean needWood = village.wood <= village.stone;
        for (int i = 0; i < 60; i++) {
            BlockPos p = o.offset(r.nextInt(33) - 16, r.nextInt(13) - 6, r.nextInt(33) - 16);
            if (!lvl.hasChunkAt(p) || village.blueprint.containsKey(p.asLong())) continue;
            BlockState s = lvl.getBlockState(p);
            if (s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) { type = 0; return p; }
            if (Util.structural(lvl, p, s)) continue;
            if (needWood && s.is(BlockTags.LOGS)) { type = 1; return p; }
            if (!needWood && s.is(BlockTags.BASE_STONE_OVERWORLD)) {
                for (Direction d : Direction.values())
                    if (lvl.getBlockState(p.relative(d)).isAir()) { type = 2; return p; }
            }
        }
        return null;
    }

    @Override
    public void start() {
        ticks = 0;
        v.getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, 0.7);
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < 300 && v.getTarget() == null;
    }

    @Override
    public void stop() { target = null; }

    @Override
    public void tick() {
        ticks++;
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (v.distanceToSqr(target.getX() + .5, target.getY() + .5, target.getZ() + .5) > 9) {
            if (ticks % 20 == 0) v.getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, 0.7);
            return;
        }
        ServerLevel l = (ServerLevel) v.level();
        BlockState s = l.getBlockState(target);
        switch (type) {
            case 0 -> {
                if (s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) {
                    l.setBlock(target, c.getStateForAge(0), 2); // replanta
                    village.food += 2;
                }
            }
            case 1 -> { if (s.is(BlockTags.LOGS)) { l.destroyBlock(target, false); village.wood += 2; } }
            case 2 -> { if (s.is(BlockTags.BASE_STONE_OVERWORLD)) { l.destroyBlock(target, false); village.stone += 1; } }
        }
        v.swing(InteractionHand.MAIN_HAND);
        target = null;
    }
}
