package ohkt.ui;

import ohkt.engine.Game;
import ohkt.engine.Settings;
import ohkt.save.SaveSystem;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Menus: principal, pausa, configurações (vídeo/áudio/controles),
 * salvar/carregar (slots), estatísticas e lojas.
 */
public final class Menus {

    public enum Screen { MAIN, PAUSE, SETTINGS, CONTROLS, SAVES, SAVE_MODE, LOAD_MODE, STATS, SHOP, CREDITS }

    public Screen screen = Screen.MAIN;
    public int selected;
    public int settingsTab; // 0 video 1 audio 2 geral
    public String shopType;
    public String shopResult = "";
    private final List<Button> buttons = new ArrayList<>();
    public boolean rebindMode;
    public Settings.Action rebindAction;

    private static final class Button {
        final String label;
        final Runnable action;
        final String hint;

        Button(String label, Runnable action, String hint) {
            this.label = label;
            this.action = action;
            this.hint = hint;
        }
    }

    public boolean inMenu() {
        return screen != null;
    }

    public boolean inShop() {
        return screen == Screen.SHOP;
    }

    public void open(Game g, Screen s) {
        screen = s;
        selected = 0;
        shopResult = "";
        build(g);
    }

    public void close() {
        screen = null;
    }

    // ---------------- construção ----------------

    private void build(Game g) {
        buttons.clear();
        SaveSystem save = g.saveSystem;
        switch (screen) {
            case MAIN:
                buttons.add(new Button("NOVO JOGO", () -> g.startNewGame(), "começar a história em Porto Aurora"));
                if (save.hasAutosave() || save.anySlot()) {
                    buttons.add(new Button("CONTINUAR", () -> g.continueGame(), "carregar o último progresso"));
                }
                buttons.add(new Button("CARREGAR", () -> open(g, Screen.LOAD_MODE), "escolher um slot de save"));
                buttons.add(new Button("OPÇÕES", () -> open(g, Screen.SETTINGS), "vídeo, áudio e controles"));
                buttons.add(new Button("SOBRE", () -> open(g, Screen.CREDITS), "créditos do projeto"));
                buttons.add(new Button("SAIR", () -> g.requestExit(), "fechar o jogo"));
                break;
            case PAUSE:
                buttons.add(new Button("VOLTAR AO JOGO", () -> close(), "esc também volta"));
                buttons.add(new Button("SALVAR", () -> open(g, Screen.SAVE_MODE), "3 slots + autosave"));
                buttons.add(new Button("CARREGAR", () -> open(g, Screen.LOAD_MODE), "retomar um save"));
                if (g.missions.current == null && g.missions.nextMission() != null) {
                    final ohkt.mission.Mission m = g.missions.nextMission();
                    buttons.add(new Button("INICIAR MISSÃO: " + m.name, () -> {
                        g.missions.start(g, m);
                        close();
                    }, "missão atual da campanha"));
                } else if (g.missions.current == null) {
                    buttons.add(new Button("CAMPANHA CONCLUÍDA — EXPLORE!", () -> close(), "atividades segundárias continuam"));
                }
                buttons.add(new Button("ATIVIDADES", null, "T=entregas  R=caçada (no mundo)"));
                buttons.add(new Button("OPÇÕES", () -> open(g, Screen.SETTINGS), ""));
                buttons.add(new Button("ESTATÍSTICAS", () -> open(g, Screen.STATS), ""));
                buttons.add(new Button("SAIR PARA O MENU", () -> g.backToMainMenu(), "perde progresso não salvo"));
                break;
            case SETTINGS:
                buildSettings(g);
                break;
            case CONTROLS:
                buildControls(g);
                break;
            case SAVE_MODE:
            case LOAD_MODE: {
                boolean load = screen == Screen.LOAD_MODE;
                for (int i = 0; i < SaveSystem.SLOTS; i++) {
                    final int slot = i;
                    SaveSystem.Meta meta = save.meta(i);
                    String label = (load ? "CARREGAR " : "SALVAR ") + "SLOT " + (i + 1);
                    if (meta != null) {
                        label += "  [" + meta.brief() + "]";
                    } else {
                        label += load ? "  [vazio]" : "  [novo]";
                    }
                    if (load && meta == null) {
                        buttons.add(new Button(label, null, ""));
                    } else {
                        buttons.add(new Button(label, () -> {
                            if (load) {
                                if (g.loadGame(slot)) close();
                            } else {
                                save.save(g, slot, "manual");
                                g.hud.notify("Jogo salvo no slot " + (slot + 1));
                                close();
                            }
                        }, ""));
                    }
                }
                buttons.add(new Button("VOLTAR", () -> open(g, Screen.PAUSE), ""));
                break;
            }
            case STATS:
                buttons.add(new Button("VOLTAR", () -> open(g, Screen.PAUSE), ""));
                break;
            case SHOP: {
                for (ohkt.economy.Shop.Item it : ohkt.economy.Shop.itemsFor(g, shopType)) {
                    final ohkt.economy.Shop.Item item = it;
                    String price = it.price < 0 ? "ADQUIRIDO" : (it.price == 0 ? "" : " — " + Widgets.money(it.price));
                    buttons.add(new Button(it.label + price, () -> shopResult = ohkt.economy.Shop.buy(g, shopType, item), ""));
                }
                buttons.add(new Button("SAIR DA LOJA", () -> close(), ""));
                break;
            }
            case CREDITS:
                buttons.add(new Button("VOLTAR", () -> open(g, Screen.MAIN), ""));
                break;
            default:
                break;
        }
    }

    private void buildSettings(Game g) {
        Settings s = g.settings;
        buttons.add(new Button("Aba: " + (settingsTab == 0 ? "VÍDEO" : settingsTab == 1 ? "ÁUDIO" : "GERAL"),
                () -> settingsTab = (settingsTab + 1) % 3, ""));
        if (settingsTab == 0) {
            buttons.add(new Button("Resolução interna: " + pct(s.renderScale),
                    () -> {
                        float[] opts = {0.5f, 0.66f, 0.8f, 1f};
                        int i = nearest(opts, s.renderScale);
                        s.renderScale = opts[(i + 1) % opts.length];
                        g.applyGraphicsSettings();
                    }, "menor = mais rápido"));
            buttons.add(new Button("Qualidade: " + (s.quality == 0 ? "BAIXA" : s.quality == 1 ? "MÉDIA" : "ALTA"),
                    () -> {
                        s.quality = (s.quality + 1) % 3;
                        g.worldStreamingQualityChanged();
                    }, "janelas, distância de detalhe"));
            buttons.add(new Button("FOV: " + (int) s.fov, () -> s.fov = s.fov >= 100 ? 60 : s.fov + 5, ""));
            buttons.add(new Button("Limite de FPS: " + (s.fpsCap == 0 ? "sem limite" : s.fpsCap),
                    () -> {
                        int[] caps = {30, 60, 120, 0};
                        int i = 0;
                        for (int k = 0; k < caps.length; k++) if (caps[k] == s.fpsCap) i = k;
                        s.fpsCap = caps[(i + 1) % caps.length];
                    }, ""));
            buttons.add(new Button("Mostrar FPS: " + (s.showFps ? "SIM" : "NÃO"), () -> s.showFps = !s.showFps, ""));
            buttons.add(new Button("Tela cheia: " + (s.fullScreen ? "SIM" : "NÃO"), () -> g.toggleFullscreen(), ""));
        } else if (settingsTab == 1) {
            buttons.add(new Button("Volume geral: " + pct(s.volMaster), () -> s.volMaster = step(s.volMaster), ""));
            buttons.add(new Button("Música/rádio: " + pct(s.volMusic), () -> s.volMusic = step(s.volMusic), ""));
            buttons.add(new Button("Efeitos: " + pct(s.volSfx), () -> s.volSfx = step(s.volSfx), ""));
        } else {
            buttons.add(new Button("Sensibilidade: " + pct(s.mouseSensitivity), () -> s.mouseSensitivity = step(s.mouseSensitivity), ""));
            buttons.add(new Button("Inverter Y: " + (s.invertY ? "SIM" : "NÃO"), () -> s.invertY = !s.invertY, ""));
            buttons.add(new Button("Minimapa: " + (s.showMinimap ? "SIM" : "NÃO"), () -> s.showMinimap = !s.showMinimap, ""));
            buttons.add(new Button("Servidor local de stats: " + (s.localStatsServer ? "SIM" : "NÃO"),
                    () -> g.toggleStatsServer(), "http://localhost:" + s.statsServerPort));
            buttons.add(new Button("Controles...", () -> open(g, Screen.CONTROLS), "redefinir teclas"));
        }
        buttons.add(new Button("VOLTAR", () -> open(g, g.inMainMenu ? Screen.MAIN : Screen.PAUSE), ""));
    }

    private void buildControls(Game g) {
        Settings s = g.settings;
        for (Settings.Action a : Settings.Action.values()) {
            if (a == Settings.Action.WEAPON_1 && false) continue;
            final Settings.Action act = a;
            String label = prettyAction(a) + ":  " + s.bind(a) + (rebindMode && rebindAction == a ? "  (pressione uma tecla)" : "");
            buttons.add(new Button(label, () -> {
                rebindMode = true;
                rebindAction = act;
            }, ""));
        }
        buttons.add(new Button("RESTAURAR PADRÕES", () -> {
            for (Settings.Action a : Settings.Action.values()) s.setBind(a, a.def);
        }, ""));
        buttons.add(new Button("VOLTAR", () -> {
            g.settings.save();
            open(g, Screen.SETTINGS);
        }, ""));
    }

    private static String prettyAction(Settings.Action a) {
        String n = a.name().replace('_', ' ');
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    private static String pct(float f) {
        return Math.round(f * 100) + "%";
    }

    private static float step(float v) {
        return v >= 0.99f ? 0.1f : Math.min(1f, v + 0.1f);
    }

    private static int nearest(float[] opts, float v) {
        int best = 0;
        for (int i = 0; i < opts.length; i++) {
            if (Math.abs(opts[i] - v) < Math.abs(opts[best] - v)) best = i;
        }
        return best;
    }

    // ---------------- update ----------------

    public void update(Game g, float dt) {
        if (rebindMode) {
            String key = g.input.pendingKey;
            if (key != null && !"ESC".equals(key)) {
                if (rebindAction != null) g.settings.setBind(rebindAction, key);
            } else if ("ESC".equals(key)) {
                rebindMode = false;
            }
            if (key != null) {
                g.input.pendingKey = null;
                if (!"ESC".equals(key)) rebindMode = false;
                build(g);
            }
            return;
        }
        // captura de mouse na janela
        if (g.input.isMouseJustPressed(1) && !g.inMainMenu) {
            // clique tratado no render por hover — armazenado abaixo
        }
        // navegação por teclado
        if (navUp(g)) {
            selected = (selected - 1 + buttons.size()) % buttons.size();
            g.audio.playUI("UI", 0.25f);
        }
        if (navDown(g)) {
            selected = (selected + 1) % buttons.size();
            g.audio.playUI("UI", 0.25f);
        }
        if (g.input.justPressed(Settings.Action.INTERACT) || g.input.isMouseJustPressed(1)) {
            activate(g, selected);
        }
        if (g.input.justPressed(Settings.Action.PAUSE) && screen != Screen.MAIN && screen != Screen.CREDITS) {
            if (screen == Screen.PAUSE) close();
            else if (g.inMainMenu) open(g, Screen.MAIN);
            else open(g, Screen.PAUSE);
        }
        // mouse hover → seleciona
        float mx = g.input.mouseX, my = g.input.mouseY;
        int my0 = 150;
        for (int i = 0; i < buttons.size(); i++) {
            float y = menuY(g, i);
            if (y < 0) continue;
            if (mx > 80 && mx < 80 + 620 && my > y - 12 && my < y + 16 && my > my0 - 40) {
                if (selected != i) {
                    selected = i;
                }
            }
        }
        if (g.input.isMouseJustPressed(1)) {
            float y = menuY(g, selected);
            if (y >= 0 && my > y - 12 && my < y + 16) activate(g, selected);
        }
    }

    private boolean navUp(Game g) {
        return justKey(g, "UP") || justKey(g, "W");
    }

    private boolean navDown(Game g) {
        return justKey(g, "DOWN") || justKey(g, "S");
    }

    private boolean justKey(Game g, String k) {
        return g.input.justPressedRaw(k);
    }

    private float menuY(Game g, int i) {
        return 170 + i * 44;
    }

    private void activate(Game g, int i) {
        if (i < 0 || i >= buttons.size()) return;
        Button b = buttons.get(i);
        g.audio.playUI("UI", 0.35f);
        if (b.action != null) b.action.run();
    }

    // ---------------- render ----------------

    public void render(Game g, Graphics2D gg, int w, int h) {
        Font title = new Font("SansSerif", Font.BOLD, Math.max(26, h / 16));
        Font med = new Font("SansSerif", Font.BOLD, Math.max(15, h / 40));
        Font small = new Font("SansSerif", Font.PLAIN, Math.max(12, h / 48));

        gg.setColor(new Color(0.06f, 0.06f, 0.09f, 0.86f));
        gg.fillRect(0, 0, w, h);

        String heading = "PORTO AURORA";
        String sub = "um jogo de mundo aberto";
        if (screen == Screen.PAUSE) {
            heading = "PAUSA";
            sub = "Porto Aurora";
        } else if (screen == Screen.SETTINGS) {
            heading = "OPÇÕES";
            sub = "configurações";
        } else if (screen == Screen.CONTROLS) {
            heading = "CONTROLES";
            sub = "clique para redefinir";
        } else if (screen == Screen.SAVE_MODE) {
            heading = "SALVAR";
            sub = "escolha um slot";
        } else if (screen == Screen.LOAD_MODE) {
            heading = "CARREGAR";
            sub = "escolha um slot";
        } else if (screen == Screen.STATS) {
            heading = "ESTATÍSTICAS";
            sub = "sua carreira no crime";
        } else if (screen == Screen.SHOP) {
            heading = ohkt.economy.Shop.title(shopType);
            sub = "dinheiro: " + Widgets.money(g.economy.money());
        } else if (screen == Screen.CREDITS) {
            heading = "SOBRE";
            sub = "Porto Aurora";
        }

        Widgets.text(gg, heading, w / 2, 70, new Color(0xffd060), true, title);
        Widgets.text(gg, sub, w / 2, 96, Color.LIGHT_GRAY, true, small);

        if (screen == Screen.STATS) {
            renderStats(g, gg, w, h);
            return;
        }
        if (screen == Screen.CREDITS) {
            renderCredits(gg, w, h);
        }

        int i = 0;
        for (Button b : buttons) {
            float y = menuY(g, i);
            boolean sel = i == selected;
            Widgets.panel(gg, 80, (int) y - 16, 620, 34,
                    sel ? 0xff28303c : 0x90101418, sel ? 0xffffd060 : 0xff303840);
            Widgets.text(gg, b.label, 100, (int) y + 7, sel ? Color.WHITE : Color.LIGHT_GRAY, false, med);
            if (b.hint.length() > 0) {
                Widgets.text(gg, b.hint, 685, (int) y + 7, Color.GRAY, false, small);
            }
            i++;
        }

        if (screen == Screen.SHOP && shopResult.length() > 0) {
            Widgets.panel(gg, w - 420, h - 90, 400, 40, 0x90101810, 0xff405838);
            Widgets.text(gg, shopResult, w - 410, h - 64, new Color(0x90e8a0), false, med);
        }
        if (screen == Screen.MAIN) {
            Widgets.text(gg, "setas/W-S + ENTER ou clique do mouse", w / 2, h - 40, Color.GRAY, true, small);
        }
    }

    private void renderStats(Game g, Graphics2D gg, int w, int h) {
        Font med = new Font("SansSerif", Font.BOLD, Math.max(14, h / 42));
        ohkt.save.GameStats s = g.stats;
        String[] lines = {
                "Tempo de jogo: " + s.timeString(),
                "Dinheiro atual: " + Widgets.money(g.economy.money()),
                "Total ganho: " + Widgets.money(g.economy.totalEarned()),
                "Total gasto: " + Widgets.money(g.economy.totalSpent()),
                "Mortes: " + s.getInt("mortes"),
                "Vezes preso: " + s.getInt("preso"),
                "Mortes causadas: " + s.getInt("abates"),
                "Policiais mortos: " + s.getInt("policiaisMortos"),
                "Missões concluídas: " + g.missions.completed.size(),
                "Veículos comprados: " + s.getInt("veiculosComprados"),
                "Distância a pé: " + (int) s.getFloat("distPe") + " m",
                "Distância dirigindo: " + (int) (s.getFloat("distCarro") / 1000f) + " km",
                "Propriedades: " + g.properties.owned().size() + "/3",
        };
        int y = 150;
        for (String line : lines) {
            Widgets.text(gg, line, w / 2 - 200, y, Color.LIGHT_GRAY, false, med);
            y += 26;
        }
        superBrief(g, gg, w, h);
    }

    private void superBrief(Game g, Graphics2D gg, int w, int h) {
        // slot de voltar fica no build()
    }

    private void renderCredits(Graphics2D gg, int w, int h) {
        Font med = new Font("SansSerif", Font.PLAIN, Math.max(13, h / 44));
        String[] lines = {
                "PORTO AURORA — projeto de jogo open-world em Java puro",
                "",
                "Engine própria: game loop, cenas, ECS leve, física, streaming",
                "Renderizador 3D por software (rasterização com z-buffer)",
                "Áudio 100%% sintetizado — rádio procedural com 4 estações",
                "",
                "Cidade: 676 quadras, 8 bairros, porto, ilha e calçadão",
                "Sistemas: polícia com 5 estrelas, tráfego, rotinas de NPCs,",
                "clima, ciclo dia/noite, economia, propriedades e 10 missões.",
        };
        int y = h / 2 - lines.length * 14;
        for (String line : lines) {
            Widgets.text(gg, line, w / 2, y, Color.LIGHT_GRAY, true, med);
            y += 28;
        }
    }
}
