package com.villagerise.goals;

import com.villagerise.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.resources.ResourceLocation;

/** Los guerreros (rol 2) eligen monstruos hostiles como objetivo (ignora neutrales y Endermen). */
public class FighterTargetGoal extends NearestAttackableTargetGoal<Monster> {
    private final Villager v;

    public FighterTargetGoal(Villager v) {
        super(v, Monster.class, true);
        this.v = v;
    }

    @Override
    public boolean canUse() {
        if (Util.role(v) != 2 || v.isBaby() || !super.canUse()) return false;
        return !(this.target instanceof NeutralMob) && this.target.getType() != BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace("enderman"));
    }
}
