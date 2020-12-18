package io.github.js0mmer.webstatus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;

@Mod("webstatus")
public class Webstatus
{

    private static final Logger LOGGER = LogManager.getLogger();
    private File serverDir;
    private MinecraftServer server;

    public Webstatus() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        server = event.getServer();

        serverDir = new File(server.getDataDirectory(), "webstatus");
        if (!serverDir.exists()) {
            serverDir.mkdir();
            extractFile("index.html");
            extractFile("style.css");
            extractFile("player-card.html");
            extractFile("README.txt");
            extractFile("port.txt");
        }

        int port = 9000;

        File portFile = new File(serverDir, "port.txt");
        try {
            FileReader reader = new FileReader(portFile);
            BufferedReader bufferedReader = new BufferedReader(reader);
            port = Integer.parseInt(bufferedReader.readLine().split("=")[1]);
        } catch (IOException e) {
            LOGGER.error("Invalid port configuration. Please use the following format: port=####");
        }

        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        LOGGER.info("Server started at " + port);
        server.createContext("/", new ServerHandler());
        server.setExecutor(null);
        server.start();
    }

    private void extractFile(String file) {
        try {
            File toWrite = new File(serverDir, file);
            InputStream in = getClass().getResourceAsStream("/server/" + file);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(toWrite));
            byte[] buffer = new byte[2048];
            for (;;) {
                int nBytes = in.read(buffer);
                if (nBytes <= 0) break;
                out.write(buffer, 0, nBytes);
            }
            out.flush();
            out.close();
            in.close();
        } catch (IOException ex) {
            LOGGER.error("Could not extract " + file);
        }
    }

    private String generatePlayerCards() throws IOException {
        FileReader reader = new FileReader(new File(serverDir, "player-card.html"));
        BufferedReader br = new BufferedReader(reader);

        StringBuilder cardBuilder = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            cardBuilder.append(line + "\r\n");
        }

        String card = cardBuilder.toString();

        StringBuilder cards = new StringBuilder();

        for(ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            cards.append(card.replace("%playerName%", player.getName().getString()));
        }

        return cards.toString();
    }

    private class ServerHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            URI uri = he.getRequestURI();
            String file = uri.getPath().equals("/") ? "/index.html" : uri.getPath();

            FileReader reader = new FileReader(new File(serverDir, file));
            BufferedReader br = new BufferedReader(reader);

            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                responseBuilder.append(line + "\r\n");
            }

            String response = responseBuilder.toString()
                    .replace("%playerCards%", generatePlayerCards())
                    .replace("%playerCount%", server.getCurrentPlayerCount() + "")
                    .replace("%maxPlayers%", server.getMaxPlayers() + "");

            he.sendResponseHeaders(200, response.length());
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
