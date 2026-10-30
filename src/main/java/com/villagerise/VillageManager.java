package com.villagerise;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Registro de aldeas por dimensión + guardado/carga en JSON dentro de la carpeta del mundo. */
public final class VillageManager {
    private VillageManager() {}

    private static final Gson GSON = new Gson();
    private static final Map<String, List<Village>> DATA = new HashMap<>();

    private static String key(ServerLevel l) { return l.dimension().toString().replaceAll("[^A-Za-z0-9]+", "_"); }

    public static List<Village> villages(ServerLevel l) { return DATA.computeIfAbsent(key(l), k -> new ArrayList<>()); }

    public static Village villageAt(ServerLevel l, BlockPos p, boolean create) {
        Village best = null;
        double bd = 72 * 72;
        for (Village v : villages(l)) {
            double d = v.center.distSqr(p);
            if (d < bd) { bd = d; best = v; }
        }
        if (best == null && create) { best = new Village(p.immutable()); villages(l).add(best); }
        return best;
    }

    public static void tick(ServerLevel l) {
        long t = l.getGameTime();
        if (t % 20 != 0) return;
        if (t % 100 == 0)
            for (ServerPlayer p : l.players())
                for (Villager v : l.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(64)))
                    villageAt(l, v.blockPosition(), true);
        for (Village v : new ArrayList<>(villages(l))) if (l.hasChunkAt(v.center)) v.tick(l);
        if (t % 6000 == 0) saveLevel(l);
    }

    public static void forget(ServerLevel l, BlockPos p) { for (Village v : villages(l)) v.forget(p); }

    public static void onDeath(LivingEntity e, DamageSource src) {
        if (!(e.level() instanceof ServerLevel l)) return;
        Village v = villageAt(l, e.blockPosition(), false);
        if (v == null) return;
        if (e.getTags().contains("vr_attacker")) {
            v.deathsBySide[Util.dirOf(e.getX() - v.center.getX(), e.getZ() - v.center.getZ())]++;
            Entity direct = src.getDirectEntity();
            if (direct != null && direct.getTags().contains("vr_turret")) v.turretKills++;
        } else if (e instanceof Villager && v.wave != null) {
            v.wave.losses++;
        }
    }

    // ---------- persistencia ----------
    private static Path file(MinecraftServer s, ServerLevel l) {
        return s.getWorldPath(LevelResource.ROOT).resolve("villagerise_" + key(l) + ".json");
    }

    public static void save(MinecraftServer s) { for (ServerLevel l : s.getAllLevels()) saveLevel(l); }

    private static void saveLevel(ServerLevel l) {
        List<Village> list = villages(l);
        if (list.isEmpty()) return;
        List<Village.Dto> dtos = new ArrayList<>();
        for (Village v : list) dtos.add(v.toDto());
        try (Writer w = Files.newBufferedWriter(file(l.getServer(), l))) {
            GSON.toJson(dtos, w);
        } catch (Exception ex) {
            VillageRise.LOG.error("No se pudo guardar las aldeas", ex);
        }
    }

    public static void load(MinecraftServer s) {
        DATA.clear();
        for (ServerLevel l : s.getAllLevels()) {
            Path f = file(s, l);
            if (!Files.exists(f)) continue;
            try (Reader r = Files.newBufferedReader(f)) {
                Village.Dto[] dtos = GSON.fromJson(r, Village.Dto[].class);
                for (Village.Dto d : dtos) villages(l).add(Village.fromDto(d));
                VillageRise.LOG.info("Cargadas {} aldeas en {}", dtos.length, key(l));
            } catch (Exception ex) {
                VillageRise.LOG.error("No se pudo cargar las aldeas", ex);
            }
        }
    }
}
