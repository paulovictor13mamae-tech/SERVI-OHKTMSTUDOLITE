package ohkt.tools;

import ohkt.audio.MusicPlayer;
import ohkt.audio.SoundSynth;
import ohkt.combat.Weapon;
import ohkt.engine.Game;
import ohkt.graphics.Frustum;
import ohkt.physics.AABB;
import ohkt.physics.PhysicsWorld;
import ohkt.physics.RaycastHit;
import ohkt.utils.Mat4;
import ohkt.utils.Vec3;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Bateria de testes automatizados (CI + `java ohkt.Main --selftest`):
 * matemática, física, cidade determinística, save/load, veículos, áudio e rede.
 */
public final class SelfTest {

    private int passed, failed;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        int code = new SelfTest().run();
        System.exit(code);
    }

    int run() {
        testMath();
        testFrustum();
        testPhysics();
        testWeapons();
        testSynth();
        testMusic();
        testRoadGraphAndCity();
        testGameHeadless();
        testSaveLoad();
        testNetwork();
        System.out.println();
        System.out.println("RESULTADO: " + passed + " passaram, " + failed + " falharam");
        return failed == 0 ? 0 : 1;
    }

    private void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    private void testMath() {
        Vec3 v = new Vec3(3, 4, 0);
        check("Vec3.len", Math.abs(v.len() - 5) < 1e-4);
        Vec3 a = new Vec3(1, 0, 0);
        Vec3 b = new Vec3(0, 1, 0);
        a.cross(b);
        check("Vec3.cross", a.z > 0.99f && a.x == 0 && a.y == 0);
        Mat4 m = new Mat4().perspective(70, 1.6f, 0.3f, 400f);
        float[] out = new float[4];
        Mat4.transform(m.m, 0, 0, -1, 1, out);
        check("Mat4.perspectiveTransform", out[3] > 0.9f && out[3] < 1.1f);
        check("MathX.perlin determinístico", ohkt.utils.MathX.perlin(0.3f, 0.7f) == ohkt.utils.MathX.perlin(0.3f, 0.7f));
        check("MathX.hash", ohkt.utils.MathX.hash(42) != ohkt.utils.MathX.hash(43));
    }

    private void testFrustum() {
        Frustum f = new Frustum();
        Mat4 vp = new Mat4();
        Mat4 view = new Mat4().lookAt(new Vec3(0, 2, 5), new Vec3(0, 2, 0), new Vec3(0, 1, 0));
        Mat4 proj = new Mat4().perspective(70, 1.5f, 0.3f, 400f);
        float[] tmp = new float[16];
        Mat4.mul(tmp, proj.m, view.m);
        System.arraycopy(tmp, 0, vp.m, 0, 16);
        f.fromMatrix(vp.m);
        check("Frustum vê objeto à frente", f.sphereVisible(0, 2, 0, 1));
        check("Frustum corta objeto atrás", !f.sphereVisible(0, 2, 10, 1));
    }

    private void testPhysics() {
        PhysicsWorld pw = new PhysicsWorld();
        Object owner = new Object();
        pw.addStatic(new AABB(-2, 0, -2, 2, 5, 2, owner));
        RaycastHit hit = pw.raycast(0, 1, 10, 0, 0, -1, 50);
        check("Raycast acerta parede", hit.hit && Math.abs(hit.t - 8) < 0.2f);
        PhysicsWorld.Position p = new PhysicsWorld.Position(2.3f, 0);
        float pushed = pw.resolveCircle(p, 0.5f, 0.1f, 1.8f);
        check("resolveCircle empurra para fora", pushed > 0 && p.x > 2.45f);
        pw.removeOwner(owner);
        RaycastHit hit2 = pw.raycast(0, 1, 10, 0, 0, -1, 50);
        check("removeOwner limpa colisores", !hit2.hit);
    }

    private void testWeapons() {
        boolean ok = true;
        for (int i = 0; i < Weapon.CATALOG.length; i++) {
            ok = ok && Weapon.CATALOG[i].id == i;
        }
        check("Catálogo de armas íntegro", ok && Weapon.CATALOG.length >= 7);
        check("Armas fictícias nomeadas", Weapon.byId(2).name.equals("GP-9") && Weapon.byId(6).name.contains("Condor"));
    }

    private void testSynth() {
        short[] shot = SoundSynth.gunshot(0.1f, 0.5f, new java.util.Random(1));
        boolean nonZero = false;
        for (short s : shot) {
            if (s != 0) nonZero = true;
        }
        check("Sintetizador gera tiro", shot.length == (int) (0.1f * SoundSynth.SR) && nonZero);
        check("Sintetizador explosão", SoundSynth.explosion().length > 60000);
        check("Sintetizador sirene", SoundSynth.siren().length == 44100);
    }

    private void testMusic() {
        MusicPlayer mp = new MusicPlayer();
        mp.setStation(0);
        mp.setWantPlaying(true);
        short[] l = new short[256], r = new short[256];
        mp.mixInto(l, r, 256, 0.8f);
        boolean sounding = false;
        for (short s : l) {
            if (Math.abs(s) > 100) sounding = true;
        }
        check("Rádio procedural mistura áudio", sounding);
        check("Estações nomeadas", MusicPlayer.STATIONS.length == 4);
        mp.setStation(1);
        check("Troca de estação", mp.station() == 1 && mp.stationName().equals("Choque FM"));
    }

    private void testRoadGraphAndCity() {
        ohkt.world.World world = new ohkt.world.World(1337L);
        java.util.List<int[]> path = world.roadGraph.path(0, 0, 26, 26);
        check("A* no grafo de ruas", path != null && path.size() == 53);
        // cidade determinística: mesmo chunk gera igual
        ohkt.world.Chunk c1 = new ohkt.world.Chunk(world, 10, 10);
        c1.build(world.physics, true);
        int faces1 = c1.dayMesh.faceCount;
        int coll1 = c1.colliders.size();
        ohkt.world.Chunk c2 = new ohkt.world.Chunk(world, 10, 10);
        c2.build(world.physics, true);
        check("Geração de chunk determinística", faces1 == c2.dayMesh.faceCount && coll1 == c2.colliders.size() && faces1 > 50);
        check("Distritos mapeados", ohkt.world.CityLayout.blockDistrict(13, 13) == ohkt.world.District.CENTRO
                && ohkt.world.CityLayout.blockDistrict(3, 3) == ohkt.world.District.PARQUE);
        check("Água ao sul", ohkt.world.CityLayout.isWater(0, ohkt.world.CityLayout.WATER_Z + 50));
        check("Ilha é terra", !ohkt.world.CityLayout.isWater(ohkt.world.CityLayout.ISLAND_X, ohkt.world.CityLayout.ISLAND_Z));
        // altura do chão
        check("Altura do chão", ohkt.world.CityLayout.groundHeight(ohkt.world.CityLayout.roadCoord(5), 0.1f) == 0f);
    }

    private Game gameForTests() {
        Game g = new Game(true);
        g.startNewGame();
        return g;
    }

    private void testGameHeadless() {
        try {
            Game g = gameForTests();
            // simula 5 segundos de jogo
            for (int i = 0; i < 300; i++) {
                g.step(1 / 60f);
                g.renderFrame(null);
            }
            check("Loop headless roda 300 frames", g.frame >= 300);
            check("Player vivo", g.player.health > 0);
            check("Chunks carregados por streaming", !g.world.loadedChunks().isEmpty());
            check("Missão 1 ativa", g.missions.current != null);

            // veículo dirige (spawn na rua, sem colisão com prédios)
            float roadX = ohkt.world.CityLayout.roadCoord(6);
            float roadZ = ohkt.world.CityLayout.roadCoord(8);
            ohkt.vehicle.Vehicle v = g.vehicles.spawn(ohkt.vehicle.VehicleType.byId("ANDARILHO"),
                    roadX + 2.8f, roadZ, 0, 0xffc02020);
            v.driverInput.throttle = 1f;
            float z0 = v.pos.z;
            for (int i = 0; i < 120; i++) {
                v.update(g, 1 / 60f);
            }
            check("Veículo acelera", v.forwardSpeed() > 4f && Math.abs(v.pos.z - z0) > 2f);

            // combate: dano no jogador por policial
            g.player.takeDamage(g, 30, "teste");
            check("Dano ao jogador", g.player.health < 100);

            g.audio.dispose();
        } catch (Exception e) {
            check("Loop headless sem exceções: " + e, false);
        }
    }

    private void testSaveLoad() {
        try {
            Game g = gameForTests();
            g.economy.earn(1234, "teste");
            g.player.giveWeapon(3, 12);
            g.player.pos.set(111f, 0.2f, -222f);
            g.saveSystem.save(g, 2, "selftest");
            g.economy.setMoney(0);
            g.player.pos.set(0, 0, 0);
            boolean ok = g.saveSystem.loadInto(g, 2);
            check("Save/Load roundtrip", ok && g.economy.money() >= 1234
                    && Math.abs(g.player.pos.x - 111f) < 0.01f
                    && g.player.ownedWeapons[3]
                    && g.player.reserveAmmo[3] >= 12);
            // autosave meta
            g.saveSystem.autosave(g, "auto");
            check("Autosave com meta", g.saveSystem.hasAutosave() && g.saveSystem.meta(99) != null);
            g.audio.dispose();
        } catch (Exception e) {
            check("Save/Load sem exceções: " + e, false);
            e.printStackTrace();
        }
    }

    private void testNetwork() {
        try {
            Game g = gameForTests();
            g.settings.statsServerPort = 8199;
            ohkt.network.LocalStatsServer server = new ohkt.network.LocalStatsServer(g);
            server.start();
            Thread.sleep(300);
            URL url = new URL("http://localhost:8199/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            InputStream in = conn.getInputStream();
            String body = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
            in.close();
            server.stop();
            g.audio.dispose();
            check("Servidor HTTP de stats", code == 200 && body.contains("Porto Aurora") && body.contains("dinheiro"));
        } catch (Exception e) {
            check("Servidor HTTP de stats: " + e, false);
        }
    }
}
