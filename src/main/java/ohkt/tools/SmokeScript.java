package ohkt.tools;

import ohkt.engine.Game;
import ohkt.engine.Settings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Roteiro de teste automatizado (headless): caminhar, pular, mirar, atirar,
 * dirigir, gerar crimes, entrar em interiores — validando o jogo inteiro sem janela.
 */
public final class SmokeScript {

    private final Game g;
    private final List<Shot> shots = new ArrayList<>();
    private float t;
    private float phaseEnd;
    private int phase;

    private static final class Shot {
        float at;
        final String file;

        Shot(float at, String file) {
            this.at = at;
            this.file = file;
        }
    }

    public SmokeScript(Game g) {
        this.g = g;
    }

    public void shot(float at, String file) {
        shots.add(new Shot(at, file));
    }

    /** Um passo de script; retorna null para continuar ou mensagem de erro. */
    public String step(float dt) {
        t += dt;
        try {
            runPhase(dt);
        } catch (Exception e) {
            return "exceção na fase " + phase + ": " + e;
        }
        // capturas de tela
        for (Shot s : shots) {
            if (s.at > 0 && t >= s.at) {
                s.at = -1;
                capture(s.file);
            }
        }
        return null;
    }

    private void key(String k, boolean down) {
        g.input.setKeyDown(k, down);
    }

    private void runPhase(float dt) {
        if (t >= phaseEnd) {
            nextPhase();
        }
        switch (phase) {
            case 0: // caminhar para frente
                key("W", true);
                break;
            case 1: // pular
                key("W", true);
                key("SPACE", true);
                break;
            case 2: // olhar para o lado (mouse)
                g.input.mouseMove(120 * dt * 60, 10 * dt);
                key("SPACE", false);
                break;
            case 3: // correr
                key("W", true);
                key("SHIFT", true);
                break;
            case 4: // parar, mirar e atirar
                key("W", false);
                key("SHIFT", false);
                key("MOUSE2", true);
                g.input.setMouseButton(1, true);
                g.combat.update(g, dt); // dispara com GP-9 dada no início
                break;
            case 5: // entrar num carro próximo (spawnado pelo script)
                key("MOUSE2", false);
                g.input.setMouseButton(1, false);
                enterCar();
                break;
            case 6: // dirigir
                key("W", true);
                break;
            case 7: // freio de mão + curva
                key("A", true);
                key("SPACE", true);
                break;
            case 8: // sair do carro e gerar crime (atirar)
                key("SPACE", false);
                key("A", false);
                key("W", false);
                if (g.player.state == ohkt.player.Player.State.DRIVING) {
                    g.player.exitVehicle(g, false);
                }
                g.input.setMouseButton(1, true);
                g.combat.update(g, dt);
                break;
            case 9: // ciclo de tempo/clima acelerado
                g.world.time.hour = (g.world.time.hour + 4f * dt) % 24f;
                g.input.setMouseButton(1, false);
                break;
            case 10: // aguardar perseguição policial
                break;
            default:
                key("W", false);
                break;
        }
    }

    private void enterCar() {
        if (g.player.state == ohkt.player.Player.State.DRIVING) return;
        ohkt.vehicle.Vehicle v = g.vehicles.nearest(g.player.pos.x, g.player.pos.z, 999, false);
        if (v == null) {
            v = g.vehicles.spawn(ohkt.vehicle.VehicleType.byId("ESTRELA"),
                    g.player.pos.x + 4, g.player.pos.z + 2, 0, 0xff2050c0);
        } else {
            v.pos.set(g.player.pos.x + 3.5f, v.pos.y, g.player.pos.z);
        }
        g.player.enterVehicle(g, v);
    }

    private void nextPhase() {
        phase++;
        phaseEnd = t + phaseSeconds(phase);
        if (phase == 4) {
            // dá arma e munição para o teste de combate
            g.player.giveWeapon(2, 60);
            g.player.requestWeapon(2);
        }
        if (phase == 10) {
            // força um crime para a polícia agir
            g.bus.post(ohkt.engine.EventBus.Type.CRIME, "PED_KILLED", true);
        }
    }

    private float phaseSeconds(int p) {
        switch (p) {
            case 0: return 3;
            case 1: return 1;
            case 2: return 2;
            case 3: return 2;
            case 4: return 2;
            case 5: return 1;
            case 6: return 6;
            case 7: return 2;
            case 8: return 2;
            case 9: return 4;
            case 10: return 8;
            default: return 4;
        }
    }

    private void capture(String file) {
        try {
            BufferedImage img = g.renderer.image;
            // diagnóstico
            int[] px = g.renderer.pixels();
            long sum = 0;
            int n = 0;
            for (int k = 0; k < px.length; k += 97) {
                int c = px[k];
                sum += ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255);
                n++;
            }
            System.out.println("capture diag: avg=" + (sum / (3 * Math.max(1, n))) + " tris=" + g.renderer.trisDrawn
                    + " cam=" + g.renderer.cam.pos + " hour=" + String.format("%.1f", g.world.time.hour));
            ImageIO.write(img, "png", new File(file));
            System.out.println("captura salva: " + file);
        } catch (Exception e) {
            System.out.println("falha na captura: " + e);
        }
    }

    private static final class Graphics2DCopy {
        static void copy(BufferedImage src, BufferedImage dst) {
            dst.getGraphics().drawImage(src, 0, 0, null);
        }
    }
}
