package ohkt.engine;

import ohkt.audio.AudioEngine;
import ohkt.combat.CombatSystem;
import ohkt.economy.Economy;
import ohkt.economy.Properties;
import ohkt.graphics.Particles;
import ohkt.graphics.Renderer3D;
import ohkt.mission.Mission;
import ohkt.mission.MissionManager;
import ohkt.mission.RandomEvents;
import ohkt.mission.SideActivities;
import ohkt.network.LocalStatsServer;
import ohkt.npc.NPC;
import ohkt.npc.NPCManager;
import ohkt.player.CameraController;
import ohkt.player.Player;
import ohkt.police.PoliceSystem;
import ohkt.save.GameStats;
import ohkt.save.SaveSystem;
import ohkt.ui.HUD;
import ohkt.ui.Menus;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleManager;
import ohkt.world.Door;
import ohkt.world.InteriorManager;
import ohkt.world.TimeSystem;
import ohkt.world.WeatherSystem;
import ohkt.world.World;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Núcleo do jogo: loop principal com passo fixo, gerenciamento de cenas,
 * integração de todos os sistemas e ciclo de vida (novo jogo/carregar/sair).
 */
public final class Game {

    // engine
    public final Settings settings = new Settings();
    public final Input input = new Input(settings);
    public final EventBus bus = new EventBus();
    public final Renderer3D renderer = new Renderer3D();
    public final SceneManager scenes = new SceneManager();
    public final SaveSystem saveSystem = new SaveSystem("gamedata");
    public final GameStats stats = new GameStats();
    public Window window;
    public AudioEngine audio;
    public final HUD hud = new HUD();
    public final Menus menus = new Menus();
    public final LocalStatsServer statsServer = new LocalStatsServer(this);

    // sistemas do mundo (criados em newGame/load)
    public World world;
    public Player player;
    public final CameraController camera = new CameraController();
    public final VehicleManager vehicles = new VehicleManager();
    public final NPCManager npcs = new NPCManager();
    public final PoliceSystem police = new PoliceSystem();
    public final CombatSystem combat = new CombatSystem();
    public final MissionManager missions = new MissionManager();
    public final SideActivities sideActivities = new SideActivities();
    public final RandomEvents randomEvents = new RandomEvents();
    public final Economy economy = new Economy();
    public final Properties properties = new Properties();
    public final InteriorManager interior = new InteriorManager();
    public final ohkt.graphics.Particles particles = new ohkt.graphics.Particles();

    public boolean inMainMenu = true;
    public boolean running = true;
    public volatile String fpsDisplay = "0";
    public long frame;

    private long lastFpsTime;
    private int fpsCounter;
    private LocalStatsServer localServer;

    private boolean worldReady;

    public Game(boolean headless) {
        settings.load();
        audio = new AudioEngine(settings);
        if (!headless) {
            window = new Window(this);
            window.create("PORTO AURORA — mundo aberto em Java", 1280, 720);
            applyGraphicsSettings();
        } else {
            renderer.init(640, 360);
        }
        input.init();
        registerScenes();
        wireEvents();
    }

    private void registerScenes() {
        scenes.register("menu", new ohkt.scene.MenuScene(this));
        scenes.register("game", new ohkt.scene.GameScene(this));
        scenes.register("interior", new ohkt.scene.InteriorScene(this));
        scenes.switchTo("menu");
    }

    public boolean isWorldReady() {
        return worldReady;
    }

    // ---------------- eventos globais ----------------

    private void wireEvents() {
        bus.subscribe(EventBus.Type.GUNSHOT, e -> {
            Float x = e.get(0), z = e.get(2);
            npcs.onGunshot(this, x, z);
            police.onEvent(this, e);
        });
        bus.subscribe(EventBus.Type.EXPLOSION, e -> {
            Float x = e.get(0), z = e.get(2);
            npcs.onExplosion(this, x, z);
            police.onEvent(this, e);
            stats.add("explosões", 1);
        });
        bus.subscribe(EventBus.Type.PED_KILLED, e -> {
            police.onEvent(this, e);
            stats.add("abates", 1);
        });
        bus.subscribe(EventBus.Type.COP_KILLED, e -> {
            police.onEvent(this, e);
            stats.add("policiaisMortos", 1);
        });
        bus.subscribe(EventBus.Type.CRIME, e -> police.onEvent(this, e));
        bus.subscribe(EventBus.Type.NPC_CALLS_POLICE, e -> police.onEvent(this, e));
        bus.subscribe(EventBus.Type.PLAYER_DIED, e -> stats.add("mortes", 1));
        bus.subscribe(EventBus.Type.PLAYER_BUSTED, e -> stats.add("preso", 1));
        bus.subscribe(EventBus.Type.VEHICLE_DESTROYED, e -> {
        });
        bus.subscribe(EventBus.Type.MISSION_COMPLETED, e -> {
        });
        bus.subscribe(EventBus.Type.WANTED_CHANGED, e -> {
            int stars = e.get(0);
            if (stars > 0) hud.notify("Procurado: " + stars + " estrela" + (stars > 1 ? "s" : ""));
        });
    }

    // ---------------- ciclo de vida do jogo ----------------

    public synchronized void startNewGame() {
        resetWorldState();
        setupWorld(1337L);
        float[] casa = ohkt.world.CityLayout.specialPos(ohkt.world.CityLayout.Special.CASA_MAE);
        player.pos.set(casa[0] + 1, 0.2f, casa[1] + 5);
        economy.earn(150, "mesada");
        player.giveWeapon(0, 0);
        inMainMenu = false;
        scenes.switchTo("game");
        hud.notify("Bem-vindo a Porto Aurora!");
        // inicia a primeira missão
        Mission m = missions.nextMission();
        if (m != null) missions.start(this, m);
        saveSystem.autosave(this, "novo jogo");
        menus.close();
    }

    /** Cria mundo/player/ouvintes compartilhado entre novo jogo e carga. */
    public synchronized void setupWorld(long seed) {
        world = new World(seed);
        player = new Player();
        world.addChunkListener(vehicles);
        world.time.onNewDay(() -> dailyIncome());
        worldReady = true;
        inMainMenu = false;
        missions.init(this);
    }

    public synchronized boolean loadGame(int slot) {
        boolean ok = saveSystem.loadInto(this, slot);
        if (ok) {
            inMainMenu = false;
            worldReady = true;
            scenes.switchTo("game");
            menus.close();
            hud.notify("Jogo carregado.");
        }
        return ok;
    }

    public synchronized void continueGame() {
        if (saveSystem.hasAutosave()) {
            loadGame(99);
        } else {
            for (int i = 0; i < SaveSystem.SLOTS; i++) {
                if (loadGame(i)) return;
            }
            startNewGame();
        }
    }

    /** Limpa sistemas para novo jogo/carregamento. */
    public synchronized void resetWorldState() {
        if (world != null) {
            for (ohkt.world.Chunk c : new ArrayList<>(world.loadedChunks())) {
                c.unload(world.physics);
            }
        }
        vehicles.clearAll();
        npcs.clear();
        police.clearWanted(true);
        economy.setMoney(0);
        properties.clear();
        interior.exit();
        world = null;
        player = null;
        worldReady = false;
        scenes.byId("game").exit();
    }

    public void backToMainMenu() {
        saveSystem.autosave(this, "menu");
        inMainMenu = true;
        menus.open(this, Menus.Screen.MAIN);
        scenes.switchTo("menu");
    }

    public void requestExit() {
        running = false;
    }

    private void dailyIncome() {
        int income = economy.dailyIncome(properties);
        if (income > 0) {
            economy.earn(income, "renda de propriedades");
            hud.notify("Renda diária: +R$" + income);
        }
    }

    // ---------------- interação com portas ----------------

    public void interactDoor(Door d) {
        if (d.action != null) {
            switch (d.action) {
                case "POSTO":
                    openShop("POSTO");
                    return;
                case "OFICINA":
                    openShop("OFICINA");
                    return;
                default:
                    return;
            }
        }
        if (d.interiorId == null) return;
        // propriedades trancadas até compra
        if (d.interiorId.equals("APARTAMENTO") && !properties.own("apartamento")) {
            hud.notify("Compre o apartamento na Chaves & Filhos.");
            return;
        }
        if (d.interiorId.equals("COBERTURA") && !properties.own("cobertura")) {
            hud.notify("Compre a cobertura na Chaves & Filhos.");
            return;
        }
        if (d.interiorId.equals("GALPAO_CASA") && !properties.own("galpao")) {
            hud.notify("Compre o galpão na Chaves & Filhos.");
            return;
        }
        if (interior.enter(d.interiorId, d.x, d.z + 1.5f, d.yaw) != null) {
            if (player.vehicle != null) player.exitVehicle(this, true);
            scenes.switchTo("interior");
            audio.play("DOOR", player.pos.x, 1, player.pos.z, 0.5f, 1f);
        }
    }

    public void openShop(String type) {
        menus.shopType = type;
        menus.open(this, Menus.Screen.SHOP);
        audio.playUI("UI", 0.3f);
    }

    public List<Vehicle> policeUnitsForMap() {
        List<Vehicle> out = new ArrayList<>();
        if (world != null) {
            for (Vehicle v : vehicles.list()) {
                if (v.type.kind.equals("POLICE") && (v.sirenOn || !v.destroyed)) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    // ---------------- configurações ----------------

    public void applyGraphicsSettings() {
        if (window == null) return;
        int w = window.screenWidth();
        int h = window.screenHeight();
        renderer.init(Math.max(320, (int) (w * settings.renderScale)), Math.max(200, (int) (h * settings.renderScale)));
    }

    public void worldStreamingQualityChanged() {
        // quality afeta janelas (rebuild de chunks) e distâncias de render
        if (world != null) {
            for (ohkt.world.Chunk c : new ArrayList<>(world.loadedChunks())) {
                c.unload(world.physics);
            }
            world.loadedChunks().clear();
        }
    }

    public void toggleFullscreen() {
        settings.fullScreen = !settings.fullScreen;
        if (window != null) {
            window.setFullScreen(settings.fullScreen);
            applyGraphicsSettings();
        }
    }

    public void toggleStatsServer() {
        settings.localStatsServer = !settings.localStatsServer;
        if (settings.localStatsServer) {
            statsServer.start();
        } else {
            statsServer.stop();
        }
    }

    public void setMouseCapture(boolean capture) {
        if (window != null) window.setMouseCapture(capture);
    }

    // ---------------- loop ----------------

    private static final float STEP = 1f / 60f;

    public void run() {
        long last = System.nanoTime();
        double acc = 0;
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            if (dt > 0.25f) dt = 0.25f;
            acc += dt;
            int steps = 0;
            while (acc >= STEP && steps < 5) {
                step(STEP);
                acc -= STEP;
                steps++;
            }
            if (steps == 5) acc = 0;
            renderFrame(null);
            fpsCounter++;
            if (now - lastFpsTime > 1_000_000_000L) {
                fpsDisplay = String.valueOf(fpsCounter);
                fpsCounter = 0;
                lastFpsTime = now;
                if (window != null) {
                    window.setTitle("PORTO AURORA — " + fpsDisplay + " fps");
                }
            }
            // limite de fps
            if (settings.fpsCap > 0) {
                double frameTime = 1.0 / settings.fpsCap;
                double spent = (System.nanoTime() - now) / 1_000_000_000.0;
                long sleepMs = (long) ((frameTime - spent) * 1000);
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        settings.save();
        audio.dispose();
        statsServer.stop();
        if (window != null) {
            java.awt.EventQueue.invokeLater(() -> System.exit(0));
        }
    }

    /** Um passo de simulação (fixo). */
    public void step(float dt) {
        frame++;
        input.pollGamepad();
        scenes.update(dt);
        if (worldReady && !inMainMenu) {
            stats.add("tempoJogo", dt);
        }
        audio.setListener(renderer.cam.pos.x, renderer.cam.pos.y, renderer.cam.pos.z, renderer.cam.yaw);
        audio.frame();
        input.endFrame();
    }

    /** Renderiza um frame. Se out != null (headless), escreve no BufferedImage dado. */
    public void renderFrame(BufferedImage out) {
        scenes.renderWorld(renderer);
        java.awt.Graphics2D g2;
        int w, h;
        if (out == null && window != null) {
            g2 = window.beginFrame(renderer.image);
            w = window.screenWidth();
            h = window.screenHeight();
        } else {
            if (out == null) out = renderer.image;
            g2 = out.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.drawImage(renderer.image, 0, 0, out.getWidth(), out.getHeight(), null);
            w = out.getWidth();
            h = out.getHeight();
        }
        scenes.render2d(g2, w, h);
        g2.dispose();
        if (out == null && window != null) window.endFrame();
    }

    public void setLocalServer(LocalStatsServer s) {
        this.localServer = s;
    }
}
