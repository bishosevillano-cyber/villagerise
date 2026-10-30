package com.villagerise.mixin;

import com.villagerise.goals.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Añade las nuevas IAs a TODOS los aldeanos. */
@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager {
    protected VillagerMixin(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void villagerise$addGoals(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        this.targetSelector.addGoal(2, new FighterTargetGoal(self));
        this.goalSelector.addGoal(1, new FightGoal(self));
        this.goalSelector.addGoal(5, new BuildGoal(self));
        this.goalSelector.addGoal(6, new GatherGoal(self));
    }
}
