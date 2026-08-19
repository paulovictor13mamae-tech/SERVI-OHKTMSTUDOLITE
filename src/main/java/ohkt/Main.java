package ohkt;

import ohkt.engine.Game;
import ohkt.tools.SelfTest;
import ohkt.tools.SmokeScript;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * PORTO AURORA — jogo 3D de mundo aberto em Java puro.
 *
 * Execução normal:      java ohkt.Main
 * Testes automatizados: java ohkt.Main --selftest
 * Smoke + screenshots:  java ohkt.Main --shot pasta [segundos]
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "--play";
        switch (mode) {
            case "--selftest":
                SelfTest.main(args);
                return;
            case "--smoke":
            case "--shot": {
                System.setProperty("java.awt.headless", "true");
                float seconds = args.length > 2 ? Float.parseFloat(args[2]) : 40f;
                String outDir = args.length > 1 ? args[1] : "ci-out";
                new File(outDir).mkdirs();
                System.out.println("Modo headless: " + seconds + "s de jogo simulado");
                Game g = new Game(true);
                g.startNewGame();
                SmokeScript script = new SmokeScript(g);
                script.shot(4, outDir + "/shot1_andando.png");
                script.shot(12, outDir + "/shot2_combate.png");
                script.shot(20, outDir + "/shot3_dirigindo.png");
                script.shot(28, outDir + "/shot4_policia.png");
                script.shot(seconds - 2, outDir + "/shot5_final.png");
                float dt = 1 / 60f;
                int frames = (int) (seconds / dt);
                long t0 = System.currentTimeMillis();
                String err = null;
                for (int i = 0; i < frames && err == null; i++) {
                    g.step(dt);
                    err = script.step(dt);
                    g.renderFrame(null);
                }
                g.audio.dispose();
                if (err != null) {
                    System.out.println("SMOKE FALHOU: " + err);
                    System.exit(1);
                }
                long ms = System.currentTimeMillis() - t0;
                System.out.printf("SMOKE OK — %d frames em %dms (%.1f fps lógico)%n", frames, ms, frames / (ms / 1000f));
                System.out.println("posição final: " + g.player.pos + " | vida " + (int) g.player.health
                        + " | procurado " + g.police.wantedSystem.stars + " | NPCs " + g.npcs.aliveCount()
                        + " | veículos " + g.vehicles.count());
                System.exit(0);
                return;
            }
            default:
                new Game(false).run();
                return;
        }
    }

    private Main() {
    }
}
