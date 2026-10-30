package com.villagerise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.*;

/** Estado y lógica de UNA aldea: tesoro, plano de lo construido, cola de trabajos, torretas y memoria del enemigo. */
public class Village {
    public record Job(long pos, BlockState state) {}

    public static class Dto {
        public int cx, cy, cz, wood, stone, food, ammo, projectsDone, wallMask, keeps, waveLevel, escalation, turretKills;
        public long nextWaveTime;
        public int[] attacksBySide, deathsBySide;
        public long[] turrets, towers, farms, bpPos;
        public int[] bpIdx;
        public String[] palette;
    }

    // ---- estado persistente ----
    public BlockPos center;
    public int wood = 40, stone = 20, food = 8, ammo = 16;
    public int projectsDone, wallMask, keeps, waveLevel, escalation, turretKills;
    public long nextWaveTime;
    public final int[] attacksBySide = new int[4], deathsBySide = new int[4];
    public final Map<Long, BlockState> blueprint = new HashMap<>(); // "así debería verse la aldea"
    public final List<Long> turrets = new ArrayList<>(), towers = new ArrayList<>(), farms = new ArrayList<>();

    // ---- estado temporal ----
    public final ArrayDeque<Job> jobs = new ArrayDeque<>();
    public final Set<Long> pending = new HashSet<>();
    public Hive.Wave wave;
    private int tickCount, cursor;
    private boolean projectActive;

    public Village(BlockPos center) { this.center = center; }

    public int level() { return Math.min(5, projectsDone / 3); }

    // ================= tick (1 vez por segundo) =================
    public void tick(ServerLevel lvl) {
        tickCount++;
        List<Villager> vs = lvl.getEntitiesOfClass(Villager.class, new AABB(center).inflate(80));
        if (vs.isEmpty()) return;
        List<Villager> adults = vs.stream().filter(x -> !x.isBaby()).toList();
        boolean calm = lvl.getEntitiesOfClass(Monster.class, new AABB(center).inflate(60), Entity::isAlive).isEmpty();

        if (ammo < 64 && wood >= 8 && tickCount % 5 == 0) { wood--; ammo += 8; } // fabrican flechas
        fireTurrets(lvl);
        Hive.tick(lvl, this, adults);

        if (projectActive && jobs.isEmpty()) { // proyecto terminado
            projectActive = false;
            projectsDone++;
            tell(lvl, "§6Los aldeanos han terminado una obra. Nivel de aldea: " + level());
        }
        if (!calm) return;
        if (tickCount % 30 == 1) scan(lvl, vs);
        if (tickCount % 5 == 0) detectDamage(lvl);
        if (tickCount % 60 == 0 && jobs.isEmpty()) plan(lvl, adults.size());
        if (tickCount % 90 == 0) breed(lvl, adults, vs.size());
    }

    public void tell(ServerLevel lvl, String msg) {
        for (ServerPlayer p : lvl.players())
            if (p.blockPosition().distSqr(center) < 120 * 120) p.displayClientMessage(Component.literal(msg), false);
    }

    // ================= reparación =================
    private void scan(ServerLevel lvl, List<Villager> vs) {
        List<Villager> l = new ArrayList<>(vs);
        Collections.shuffle(l);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = 0;
        for (Villager v : l) {
            if (n++ >= 4 || blueprint.size() > 30000) break;
            BlockPos o = v.blockPosition();
            for (int dx = -8; dx <= 8; dx++)
                for (int dz = -8; dz <= 8; dz++)
                    for (int dy = -2; dy <= 8; dy++) {
                        m.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                        if (!lvl.hasChunkAt(m)) continue;
                        BlockState s = lvl.getBlockState(m);
                        if (Util.structural(lvl, m, s) && !pending.contains(m.asLong())) blueprint.put(m.asLong(), s);
                    }
        }
    }

    private void detectDamage(ServerLevel lvl) {
        if (blueprint.isEmpty()) return;
        long[] keys = blueprint.keySet().stream().mapToLong(Long::longValue).toArray();
        List<Job> found = new ArrayList<>();
        for (int i = 0; i < Math.min(1500, keys.length); i++) {
            long k = keys[(cursor + i) % keys.length];
            BlockPos p = BlockPos.of(k);
            if (!lvl.hasChunkAt(p) || pending.contains(k)) continue;
            BlockState want = blueprint.get(k), cur = lvl.getBlockState(p);
            if (!cur.is(want.getBlock()) && (cur.isAir() || cur.canBeReplaced())) found.add(new Job(k, want));
        }
        cursor = (cursor + 1500) % Math.max(1, keys.length);
        found.sort(Comparator.comparingInt(j -> BlockPos.of(j.pos()).getY())); // de abajo hacia arriba
        for (Job j : found) { pending.add(j.pos()); jobs.addLast(j); }
        if (!found.isEmpty() && found.size() > 20) tell(lvl, "§cLos aldeanos empiezan a reparar los daños...");
    }

    public void forget(BlockPos p) { blueprint.remove(p.asLong()); }

    // ================= trabajos =================
    /** Añade un bloque al plano y a la cola de construcción. */
    public void put(BlockPos p, BlockState s) {
        long k = p.asLong();
        blueprint.put(k, s);
        if (pending.add(k)) jobs.addLast(new Job(k, s));
    }

    /** Encola la retirada de lo que estorbe (árboles, hierba alta...). No se guarda en el plano. */
    public void clear(ServerLevel lvl, BlockPos p) {
        if (!lvl.getBlockState(p).isAir()) jobs.addLast(new Job(p.asLong(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
    }

    private boolean afford(Util.Cost c) {
        return switch (c) { case NONE -> true; case WOOD -> wood > 0; case STONE -> stone > 0; };
    }

    public Job claim() {
        Iterator<Job> it = jobs.iterator();
        for (int n = 0; it.hasNext() && n < 30; n++) {
            Job j = it.next();
            if (afford(Util.cost(j.state()))) { it.remove(); return j; }
        }
        return null;
    }

    public void placed(Job j) {
        pending.remove(j.pos());
        switch (Util.cost(j.state())) {
            case WOOD -> wood--;
            case STONE -> stone--;
            default -> {}
        }
    }

    public void release(Job j) { jobs.addLast(j); }

    // ================= planificador de obras =================
    public int beds() {
        int n = 0;
        for (BlockState s : blueprint.values())
            if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) n++;
        return n;
    }

    public int mostAttackedSide() {
        int best = -1, bv = 0;
        for (int i = 0; i < 4; i++) if (attacksBySide[i] > bv) { bv = attacksBySide[i]; best = i; }
        return best;
    }

    private void plan(ServerLevel lvl, int pop) {
        if (pop < 3) return;
        int lv = level();
        boolean ok = false;
        if (beds() < pop + 2) ok = Blueprints.house(lvl, this);
        if (!ok && farms.size() < Math.max(1, pop / 4)) ok = Blueprints.farm(lvl, this);
        if (!ok && lv >= 1 && towers.size() < 1 + lv / 2) ok = Blueprints.tower(lvl, this, 5, 10, false);
        if (!ok && lv >= 2 && wallMask != 15) ok = Blueprints.wall(lvl, this);
        if (!ok && lv >= 3 && keeps < 1) ok = Blueprints.tower(lvl, this, 9, 14, true);
        if (!ok && lv >= 1) ok = Blueprints.upgrade(lvl, this);
        projectActive = ok;
        if (ok) tell(lvl, "§eLos aldeanos empiezan una nueva obra (necesitan madera/piedra).");
    }

    /** Busca un solar llano. side: 0-3 para preferir ese lado, -1 cualquiera. Devuelve (x, yMáx, z) o null. */
    public BlockPos findSite(ServerLevel lvl, int half, int minR, int maxR, int maxSlope, int side) {
        RandomSource r = lvl.getRandom();
        for (int i = 0; i < 40; i++) {
            double a = side < 0 ? r.nextDouble() * Math.PI * 2 : (side * Math.PI / 2 - Math.PI / 2) + (r.nextDouble() - 0.5) * 1.2;
            int rad = minR + r.nextInt(maxR - minR + 1);
            int x = center.getX() + (int) Math.round(Math.cos(a) * rad);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * rad);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            boolean bad = false;
            outer:
            for (int dx = -half - 1; dx <= half + 1; dx++)
                for (int dz = -half - 1; dz <= half + 1; dz++) {
                    if (!lvl.hasChunkAt(new BlockPos(x + dx, 64, z + dz))) { bad = true; break outer; }
                    int h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                    if (!lvl.getFluidState(new BlockPos(x + dx, h - 1, z + dz)).isEmpty()) { bad = true; break outer; }
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                }
            if (bad || max - min > maxSlope) continue;
            outer2:
            for (int dx = -half - 1; dx <= half + 1; dx++)
                for (int dz = -half - 1; dz <= half + 1; dz++)
                    for (int y = max - 1; y <= max + 3; y++) {
                        BlockPos p = new BlockPos(x + dx, y, z + dz);
                        if (blueprint.containsKey(p.asLong())) { bad = true; break outer2; }
                        if (y >= max && y <= max + 2) {
                            BlockState s = lvl.getBlockState(p);
                            if (!(s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS))) { bad = true; break outer2; }
                        }
                    }
            if (bad) continue;
            return new BlockPos(x, max, z);
        }
        return null;
    }

    // ================= crecimiento =================
    private void breed(ServerLevel lvl, List<Villager> adults, int total) {
        if (adults.size() < 2 || adults.size() >= 40 || total >= beds() || food < 4) return;
        Villager baby = EntityType.VILLAGER.create(lvl, EntitySpawnReason.BREEDING);
        if (baby == null) return;
        food -= 4;
        Villager parent = adults.get(lvl.getRandom().nextInt(adults.size()));
        baby.setAge(-24000);
        baby.setPos(parent.getX(), parent.getY(), parent.getZ());
        lvl.addFreshEntity(baby);
        lvl.sendParticles(ParticleTypes.HEART, parent.getX(), parent.getY() + 1.5, parent.getZ(), 6, .3, .3, .3, 0);
    }

    // ================= torretas (dispensadores en torres) =================
    private void fireTurrets(ServerLevel lvl) {
        for (long k : turrets) {
            if (ammo <= 0) return;
            BlockPos p = BlockPos.of(k);
            if (!lvl.hasChunkAt(p) || !lvl.getBlockState(p).is(net.minecraft.world.level.block.Blocks.DISPENSER)) continue;
            Monster t = null;
            double best = Double.MAX_VALUE;
            for (Monster m : lvl.getEntitiesOfClass(Monster.class, new AABB(p).inflate(18), Entity::isAlive)) {
                double d = m.distanceToSqr(p.getX() + .5, p.getY(), p.getZ() + .5);
                if (d < best) { best = d; t = m; }
            }
            if (t == null) continue;
            var arrow = EntityType.ARROW.create(lvl, EntitySpawnReason.TRIGGERED);
            if (arrow == null) continue;
            arrow.setPos(p.getX() + .5, p.getY() + 1.2, p.getZ() + .5);
            double dx = t.getX() - arrow.getX(), dy = t.getY(0.33) - arrow.getY(), dz = t.getZ() - arrow.getZ();
            double hd = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + hd * 0.2, dz, 1.9f, 2.0f);
            arrow.addTag("vr_turret");
            lvl.addFreshEntity(arrow);
            lvl.playSound(null, p, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1f, 1.2f);
            ammo--;
        }
    }

    // ================= persistencia =================
    public Dto toDto() {
        Dto d = new Dto();
        d.cx = center.getX(); d.cy = center.getY(); d.cz = center.getZ();
        d.wood = wood; d.stone = stone; d.food = food; d.ammo = ammo;
        d.projectsDone = projectsDone; d.wallMask = wallMask; d.keeps = keeps;
        d.waveLevel = waveLevel; d.escalation = escalation; d.turretKills = turretKills; d.nextWaveTime = nextWaveTime;
        d.attacksBySide = attacksBySide.clone(); d.deathsBySide = deathsBySide.clone();
        d.turrets = turrets.stream().mapToLong(Long::longValue).toArray();
        d.towers = towers.stream().mapToLong(Long::longValue).toArray();
        d.farms = farms.stream().mapToLong(Long::longValue).toArray();
        Map<String, Integer> pal = new LinkedHashMap<>();
        d.bpPos = new long[blueprint.size()];
        d.bpIdx = new int[blueprint.size()];
        int i = 0;
        for (var e : blueprint.entrySet()) {
            d.bpPos[i] = e.getKey();
            d.bpIdx[i++] = pal.computeIfAbsent(Util.encode(e.getValue()), k -> pal.size());
        }
        d.palette = pal.keySet().toArray(new String[0]);
        return d;
    }

    public static Village fromDto(Dto d) {
        Village v = new Village(new BlockPos(d.cx, d.cy, d.cz));
        v.wood = d.wood; v.stone = d.stone; v.food = d.food; v.ammo = d.ammo;
        v.projectsDone = d.projectsDone; v.wallMask = d.wallMask; v.keeps = d.keeps;
        v.waveLevel = d.waveLevel; v.escalation = d.escalation; v.turretKills = d.turretKills; v.nextWaveTime = d.nextWaveTime;
        if (d.attacksBySide != null) System.arraycopy(d.attacksBySide, 0, v.attacksBySide, 0, 4);
        if (d.deathsBySide != null) System.arraycopy(d.deathsBySide, 0, v.deathsBySide, 0, 4);
        if (d.turrets != null) for (long l : d.turrets) v.turrets.add(l);
        if (d.towers != null) for (long l : d.towers) v.towers.add(l);
        if (d.farms != null) for (long l : d.farms) v.farms.add(l);
        if (d.palette != null && d.bpPos != null) {
            BlockState[] states = new BlockState[d.palette.length];
            for (int i = 0; i < states.length; i++) states[i] = Util.decode(d.palette[i]);
            for (int i = 0; i < d.bpPos.length; i++) v.blueprint.put(d.bpPos[i], states[d.bpIdx[i]]);
        }
        return v;
    }
}
