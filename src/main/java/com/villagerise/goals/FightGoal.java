package com.villagerise.goals;

import com.villagerise.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/** Ataque cuerpo a cuerpo (6 de daño ≈ espada de hierro). Los villagers no tienen atributo de ataque, así que se aplica daño directo. */
public class FightGoal extends Goal {
    private static final float DAMAGE = 6.0F;
    private final Villager v;
    private int cooldown;

    public FightGoal(Villager v) {
        this.v = v;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = v.getTarget();
        return Util.role(v) == 2 && !v.isBaby() && t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        if (v.getMainHandItem().isEmpty()) v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        LivingEntity t = v.getTarget();
        if (t == null) return;
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); // el cerebro vanilla quiere huir: lo anulamos
        v.getLookControl().setLookAt(t, 30f, 30f);
        if (v.distanceToSqr(t) > 3.5) {
            if (v.tickCount % 10 == 0) v.getNavigation().moveTo(t, 0.75);
        } else if (--cooldown <= 0) {
            cooldown = 20;
            v.swing(InteractionHand.MAIN_HAND, net.minecraft.world.entity.SwingAnimation.DEFAULT);
            if (v.level() instanceof ServerLevel sl) t.hurtServer(sl, v.damageSources().mobAttack(v), DAMAGE);
        }
    }
}
