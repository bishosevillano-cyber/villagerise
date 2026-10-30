package com.villagerise;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class Hive {
    private Hive() {}

    public static final boolean DEBUG_FAST_WAVES = false;

    public static class Wave {
        public final List<UUID> ids = new ArrayList<>();
        public final Map<UUID, Integer> group = new HashMap<>();
        public final Vec3[] stage = new Vec3[2];
        public final int[] side = new int[2];
        public int phase;
        public long start;
        public int losses;
    }

    public static void tick(ServerLevel lvl, Village v, List<Villager> adults) {
        if (lvl.dimension() != Level.OVERWORLD) return;
        long now = lvl.getGameTime();
        if (v.wave == null) {
            if (adults.size() < 3 || now < v.nextWaveTime) return;
            if (!DEBUG_FAST_WAVES && !lvl.isDarkOutside()) return;
            boolean near = false;
            for (ServerPlayer p : lvl.players()) if (p.blockPosition().distSqr(v.center) < 96 * 96) near = true;
            if (!near) return;
            v.wave = launch(lvl, v);
            v.nextWaveTime = now + (DEBUG_FAST_WAVES ? 600 : 9600 + lvl.getRandom().nextInt(4800));
            return;
        }
        manage(lvl, v, v.wave);
    }

    private static Wave launch(ServerLevel lvl, Village v) {
        int cx = v.center.getX(), cz = v.center.getZ();
        double[] score = new double[4];
        for (long k : v.turrets) { BlockPos p = BlockPos.of(k); score[Util.dirOf(p.getX() - cx, p.getZ() - cz)] += 2; }
        for (long k : v.towers) { BlockPos p = BlockPos.of(k); score[Util.dirOf(p.getX() - cx, p.getZ() - cz)] += 3; }
        for (int i = 0; i < 4; i++) {
            if ((v.wallMask >> i & 1) == 1) score[i] += 4;
            score[i] += v.deathsBySide[i] * 0.5;
        }
        Integer[] order = {0, 1, 2, 3};
        Arrays.sort(order, Comparator.comparingDouble(i -> score[i]));

        int intensity = v.waveLevel + v.escalation;
        v.waveLevel++;
        boolean fort = v.turrets.size() > 2, walled = v.wallMask != 0, split = intensity >= 3;

        Wave w = new Wave();
        w.start = lvl.getGameTime();
        w.side[0] = order[0];
        w.side[1] = split ? order[1] : order[0];
        for (int g = 0; g < 2; g++) {
            int x = cx + Util.DX[w.side[g]] * 40, z = cz + Util.DZ[w.side[g]] * 40;
            w.stage[g] = new Vec3(x, lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        v.attacksBySide[w.side[0]]++;
        if (split) v.attacksBySide[w.side[1]]++;

        int zombies = 3 + intensity * 3 / 2;
        int skeletons = fort ? intensity + 1 : intensity / 2;
        int creepers = (walled ? 1 + intensity / 2 : intensity / 3) + (v.turretKills >= 5 ? 2 : 0);
        int spiders = walled ? intensity / 2 : 0;
        int ravagers = intensity / 5;

        add(lvl, w, EntityType.ZOMBIE, zombies, intensity, split);
        add(lvl, w, EntityType.SKELETON, skeletons, intensity, split);
        add(lvl, w, EntityType.CREEPER, creepers, intensity, split);
        add(lvl, w, EntityType.SPIDER, spiders, intensity, split);
        add(lvl, w, EntityType.RAVAGER, ravagers, intensity, split);

        v.tell(lvl, "§4Un cuerno resuena a lo lejos... algo se organiza en la oscuridad. (amenaza " + intensity + ")");
        return w;
    }

    private static <T extends Mob> void add(ServerLevel lvl, Wave w, EntityType<T> type, int count, int intensity, boolean split) {
        for (int i = 0; i < count && w.ids.size() < 40; i++) {
            int g = split ? w.ids.size() % 2 : 0;
            Vec3 st = w.stage[g];
            int x = (int) st.x + lvl.getRandom().nextInt(9) - 4, z = (int) st.z + lvl.getRandom().nextInt(9) - 4;
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!lvl.hasChunkAt(p)) continue;
            T m = type.create(lvl, EntitySpawnReason.EVENT);
            if (m == null) continue;
            m.setPos(x + .5, y, z + .5);
            m.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
            m.setPersistenceRequired();
            buff(m, intensity);
            lvl.addFreshEntity(m);
            w.ids.add(m.getUUID());
            w.group.put(m.getUUID(), g);
        }
    }

    private static void buff(Mob m, int i) {
        AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(hp.getBaseValue() + i * 2); m.setHealth(m.getMaxHealth()); }
        if (m.getType() == EntityType.ZOMBIE) {
            if (i >= 2) m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            if (i >= 3) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            if (i >= 4) m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        }
    }

    private static void manage(ServerLevel lvl, Village v, Wave w) {
        List<Mob> alive = new ArrayList<>();
        for (UUID id : w.ids) if (lvl.getEntity(id) instanceof Mob m && m.isAlive()) alive.add(m);
        long now = lvl.getGameTime();
        if (alive.isEmpty() || now - w.start > 9000) { end(lvl, v, w, alive); return; }

        if (w.phase == 0) {
            boolean ready = true;
            for (Mob m : alive) {
                Vec3 st = w.stage[w.group.getOrDefault(m.getUUID(), 0)];
                if (m.distanceToSqr(st) > 100) { ready = false; m.getNavigation().moveTo(st.x, st.y, st.z, 1.0); }
            }
            if (ready || now - w.start > 900) {
                w.phase = 1;
                v.tell(lvl, "§c¡Los enemigos avanzan todos a la vez!");
            }
            return;
        }

        double ax = 0, ay = 0, az = 0;
        for (Mob m : alive) { ax += m.getX(); ay += m.getY(); az += m.getZ(); }
        BlockPos c = BlockPos.containing(ax / alive.size(), ay / alive.size(), az / alive.size());
        List<Villager> defenders = new ArrayList<>(lvl.getEntitiesOfClass(Villager.class, new AABB(c).inflate(35), x -> !x.isBaby()));
        defenders.sort(Comparator.<Villager>comparingInt(x -> Util.role(x) == 2 ? 0 : 1).thenComparingDouble(Villager::getHealth));
        Villager prio = defenders.isEmpty() ? null : defenders.get(0);

        for (Mob m : alive) {
            int g = w.group.getOrDefault(m.getUUID(), 0);
            if (m.getType() == EntityType.CREEPER && (v.wallMask >> w.side[g] & 1) == 1) {
                int bx = v.center.getX() + Util.DX[w.side[g]] * 26, bz = v.center.getZ() + Util.DZ[w.side[g]] * 26;
                if (m.distanceToSqr(bx, m.getY(), bz) > 64) { m.getNavigation().moveTo(bx, m.getY(), bz, 1.1); continue; }
            }
            if (prio != null) {
                m.setTarget(prio);
                if (m.getType() != EntityType.SKELETON && m.tickCount % 40 == 0) m.getNavigation().moveTo(prio, 1.1);
            } else if (m.getTarget() == null) {
                ServerPlayer p = (ServerPlayer) lvl.getNearestPlayer(m, 48);
                if (p != null) m.setTarget(p);
                else m.getNavigation().moveTo(v.center.getX(), v.center.getY(), v.center.getZ(), 1.0);
            }
        }
    }

    private static void end(ServerLevel lvl, Village v, Wave w, List<Mob> alive) {
        if (alive.isEmpty() && w.losses == 0) {
            v.escalation++;
            v.tell(lvl, "§aLa aldea resistió sin bajas. §8Los enemigos aprenden y volverán más fuertes.");
        } else if (w.losses >= 3 && v.escalation > 0) {
            v.escalation--;
            v.tell(lvl, "§7La aldea sufrió bajas; el enemigo se confía.");
        }
        for (Mob m : alive) m.discard();
        v.wave = null;
    }
}
