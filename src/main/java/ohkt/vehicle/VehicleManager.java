package ohkt.vehicle;

import ohkt.engine.Game;
import ohkt.utils.MathX;
import ohkt.world.Chunk;
import ohkt.world.ParkedSlot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Pool de veículos: carros estacionados por chunk (streaming), tráfego,
 * veículos do jogador (persistentes) e limpeza por distância.
 */
public final class VehicleManager implements ohkt.world.World.ChunkListener {

    private final List<Vehicle> vehicles = new ArrayList<>();
    private final Random rnd = new Random(47);
    private static final int MAX_PARKED = 30;
    private int parkedCount;

    public List<Vehicle> list() { return vehicles; }

    public int count() { return vehicles.size(); }

    public Vehicle spawn(VehicleType type, float x, float z, float yaw, int paint) {
        Vehicle v = new Vehicle(type, x, z, yaw, paint);
        vehicles.add(v);
        return v;
    }

    public void onChunkLoaded(Chunk c) {
        for (ParkedSlot slot : c.parkedSlots) {
            if (slot.occupied) continue;
            if (parkedCount >= MAX_PARKED) return;
            VehicleType type = VehicleType.byKindHint(slot.typeHint);
            int paint = varyPaint(type);
            Vehicle v = spawn(type, slot.x, slot.z, slot.yaw, paint);
            v.parked = true;
            v.chunkI = c.i;
            v.chunkJ = c.j;
            slot.occupied = true;
            parkedCount++;
            if (type.kind.equals("POLICE")) v.sirenOn = false;
        }
    }

    public void onChunkUnloaded(Chunk c) {
        Iterator<Vehicle> it = vehicles.iterator();
        while (it.hasNext()) {
            Vehicle v = it.next();
            if (v.parked && !v.persist && !v.mission && v.chunkI == c.i && v.chunkJ == c.j) {
                if (v.driver == null) {
                    it.remove();
                    parkedCount--;
                } else {
                    v.parked = false; // foi levado; vira veículo solto
                }
            }
        }
    }

    private int varyPaint(VehicleType t) {
        if (t.kind.equals("POLICE")) return t.defaultPaint;
        if (t.kind.equals("TAXI")) return t.defaultPaint;
        int[] palette = {0xffc02020, 0xff2050c0, 0xffe8e8e8, 0xff18181c, 0xff30a040, 0xffd8b028, 0xff8898b0, 0xff7030a0, 0xffc8c0b0};
        return palette[rnd.nextInt(palette.length)];
    }

    public void update(Game g, float dt) {
        Iterator<Vehicle> it = vehicles.iterator();
        float px = g.player.pos.x, pz = g.player.pos.z;
        while (it.hasNext()) {
            Vehicle v = it.next();
            v.update(g, dt);
            boolean far = Math.abs(v.pos.x - px) > 240 || Math.abs(v.pos.z - pz) > 240;
            if (v.wantsDelete() && (far || v.persist)) {
                it.remove();
                if (v.parked) parkedCount--;
                continue;
            }
            if (v.parked && far && v.driver == null && !v.persist && !v.mission) {
                it.remove();
                parkedCount--;
            }
        }
    }

    public Vehicle nearest(float x, float z, float radius, boolean requireEnterable) {
        Vehicle best = null;
        float bestD = radius * radius;
        for (Vehicle v : vehicles) {
            if (requireEnterable && !v.canEnter()) continue;
            float dx = v.pos.x - x, dz = v.pos.z - z;
            float d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    public void render(Game g, ohkt.graphics.Renderer3D r) {
        for (Vehicle v : vehicles) {
            if (!r.sphereVisible(v.pos.x, v.pos.y + 0.5f, v.pos.z, v.type.hz + 2)) continue;
            float dx = v.pos.x - r.cam.pos.x, dz = v.pos.z - r.cam.pos.z;
            if (dx * dx + dz * dz > 260 * 260) continue;
            v.render(g, r);
        }
    }

    /** Especificação de veículo do jogador para salvar. */
    public static final class Spec {
        public String typeId;
        public int paint, engineLevel, tireLevel;
        public float x, z, yaw;

        public Spec(String typeId, int paint, int engineLevel, int tireLevel, float x, float z, float yaw) {
            this.typeId = typeId;
            this.paint = paint;
            this.engineLevel = engineLevel;
            this.tireLevel = tireLevel;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
        }
    }

    public List<Spec> ownedSpecs() {
        List<Spec> out = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.persist) {
                out.add(new Spec(v.type.id, v.paint, v.engineLevel, v.tireLevel, v.pos.x, v.pos.z, v.yaw));
            }
        }
        return out;
    }

    public void restoreOwned(List<Spec> specs) {
        for (Spec s : specs) {
            VehicleType t = VehicleType.byId(s.typeId);
            Vehicle v = spawn(t, s.x, s.z, s.yaw, s.paint);
            v.persist = true;
            v.engineLevel = s.engineLevel;
            v.tireLevel = s.tireLevel;
            v.fuel = t.fuelCap;
        }
    }

    /** Entrega de veículo comprado: aparece na concessionária. */
    public Vehicle deliverPurchased(Game g, VehicleType type, int paint) {
        float[] p = ohkt.world.CityLayout.specialPos(ohkt.world.CityLayout.Special.CONCESSIONARIA);
        Vehicle v = spawn(type, p[0], p[1] + 6, (float) Math.PI, paint);
        v.persist = true;
        v.fuel = type.fuelCap;
        return v;
    }

    public void clearAll() {
        vehicles.clear();
        parkedCount = 0;
    }
}
