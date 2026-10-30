package com.villagerise;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageRise implements ModInitializer {
    public static final String MOD_ID = "villagerise";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(VillageManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(VillageManager::save);
        ServerTickEvents.END_WORLD_TICK.register(VillageManager::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(VillageManager::onDeath);
        // Si un jugador rompe un bloque a propósito, los aldeanos NO lo reconstruyen.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl) VillageManager.forget(sl, pos);
        });
        LOG.info("Village Rise cargado");
    }
}
