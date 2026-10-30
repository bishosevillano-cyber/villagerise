package com.villagerise.goals;

import com.villagerise.Util;
import com.villagerise.Village;
import com.villagerise.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.EnumSet;

/** Los "constructores" (rol 0) reparan y construyen según la cola de trabajos de la aldea. */
public class BuildGoal extends Goal {
    private final Villager v;
    private Village village;
    private Village.Job job;
    private int ticks;

    public BuildGoal(Villager v) {
        this.v = v;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (Util.role(v) != 0 || v.isBaby() || !(v.level() instanceof ServerLevel l) || l.isDarkOutside()) return false;
        if (v.getRandom().nextInt(10) != 0 || v.getTarget() != null) return false;
        village = VillageManager.villageAt(l, v.blockPosition(), false);
        return village != null && !village.jobs.isEmpty();
    }

    @Override
    public void start() {
        job = village.claim();
        ticks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return job != null && v.getTarget() == null && !v.level().isDarkOutside();
    }

    @Override
    public void stop() {
        if (job != null) {
            village.release(job);
            job = null;
        }
    }

    @Override
    public void tick() {
        if (job == null) return;
        BlockPos t = BlockPos.of(job.pos());
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.getLookControl().setLookAt(t.getX() + .5, t.getY() + .5, t.getZ() + .5);
        ticks++;
        double d = v.distanceToSqr(t.getX() + .5, t.getY() + .5, t.getZ() + .5);
        if (d > 25) {
            if (ticks % 20 == 1) v.getNavigation().moveTo(t.getX() + .5, t.getY(), t.getZ() + .5, 0.7);
            if (ticks < 400) return; // si no llega en 20 s, construye "a distancia" (torres altas, etc.)
        }
        ServerLevel l = (ServerLevel) v.level();
        if (job.state().isAir()) {
            l.destroyBlock(t, false);
        } else {
            l.setBlock(t, job.state(), 2);
            l.playSound(null, t, job.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
        }
        v.swing(InteractionHand.MAIN_HAND, net.minecraft.world.entity.SwingAnimation.DEFAULT);
        village.placed(job);
        job = village.claim(); // siguiente trabajo sin parar
        ticks = 0;
    }
}
