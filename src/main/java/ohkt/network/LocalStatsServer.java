package ohkt.network;

import ohkt.engine.Game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Servidor HTTP local (opcional) que expõe estatísticas e estado ao vivo
 * do jogo em http://localhost:8123/status — útil para companions/OBS.
 * Liga/desliga nas opções.
 */
public final class LocalStatsServer {

    private final Game game;
    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;

    public LocalStatsServer(Game game) {
        this.game = game;
    }

    public int port() {
        return game.settings.statsServerPort;
    }

    public synchronized void start() {
        if (running) return;
        try {
            server = new ServerSocket(port());
            running = true;
            thread = new Thread(this::loop, "stats-server");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            game.hud.notify("Não foi possível abrir o servidor de stats: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try (Socket sock = server.accept()) {
                handle(sock);
            } catch (IOException e) {
                if (running) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void handle(Socket sock) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        String req = in.readLine(); // ex: GET /status HTTP/1.1
        while (in.ready()) {
            in.readLine();
        }
        PrintWriter out = new PrintWriter(sock.getOutputStream());
        String path = req == null ? "/" : req.split(" ")[1];
        String body;
        String type;
        if (path.startsWith("/status")) {
            type = "application/json; charset=utf-8";
            body = statusJson();
        } else {
            type = "text/html; charset=utf-8";
            body = indexHtml();
        }
        out.print("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\n"
                + "Content-Length: " + body.getBytes("UTF-8").length + "\r\n"
                + "Connection: close\r\n\r\n");
        out.print(body);
        out.flush();
        sock.close();
    }

    private String statusJson() {
        if (game.player == null || game.world == null) {
            return "{\"status\":\"menu\"}";
        }
        return "{"
                + "\"jogo\":\"Porto Aurora\","
                + "\"dinheiro\":" + game.economy.money() + ","
                + "\"vida\":" + (int) game.player.health + ","
                + "\"procurado\":" + game.police.wantedSystem.stars + ","
                + "\"missões_concluídas\":" + game.missions.completed.size() + ","
                + "\"hora\":\"" + game.world.time.clockString() + "\","
                + "\"clima\":\"" + game.world.weather.label() + "\","
                + "\"distrito\":\"" + ohkt.world.CityLayout.districtAt(game.player.pos.x, game.player.pos.z).label + "\","
                + "\"posição\":[" + String.format("%.1f,%.1f", game.player.pos.x, game.player.pos.z) + "],"
                + "\"tempo_jogo\":\"" + game.stats.timeString() + "\""
                + "}";
    }

    private String indexHtml() {
        return "<!doctype html><html><head><meta charset='utf-8'><title>Porto Aurora — stats</title>"
                + "<body style='font-family:sans-serif;background:#12141a;color:#ddd'>"
                + "<h1>Porto Aurora — servidor local</h1>"
                + "<p>Endpoint: <a href='/status' style='color:#ffd060'>/status</a> (JSON ao vivo)</p>"
                + "</body></html>";
    }
}
