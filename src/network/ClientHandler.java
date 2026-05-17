package network;

import java.io.*;
import java.net.Socket;

/**
 * ClientHandler
 *
 * Runs on the server, one per connected player.
 * Owns the socket I/O for that player.
 * Reads incoming messages and forwards them to GameServer.onMessage().
 */
class ClientHandler implements Runnable {

    final Socket socket;
    int    slot;        // player index in GameState.players (set after handshake)
    String playerName;  // set during handshake (first message from client)

    private final GameServer    server;
    private PrintWriter         out;
    private BufferedReader      in;
    private volatile boolean    alive = true;

    ClientHandler(Socket socket, int slot, GameServer server) {
        this.socket = socket;
        this.slot   = slot;
        this.server = server;
        this.playerName = "Player " + (slot + 1); // temporary default
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new BufferedWriter(
                      new OutputStreamWriter(socket.getOutputStream())), true);
            in  = new BufferedReader(
                      new InputStreamReader(socket.getInputStream()));

            // ── Handshake ─────────────────────────────────────────────────
            // First thing we do: send WELCOME with their assigned slot number
            send(Message.welcome(slot));

            // Then wait for client to send their name as plain text
            String nameLine = in.readLine();
            if (nameLine != null && !nameLine.isBlank()) {
                // THE FIX: Safely parse the SET_NAME command!
                if (nameLine.startsWith("SET_NAME|")) {
                    playerName = nameLine.substring(9).trim(); // strips out "SET_NAME|"
                } else {
                    playerName = nameLine.trim(); // fallback for raw text
                }
            }

            server.onLog.accept("[Server] Slot " + slot
                    + " identified as \"" + playerName + "\"");
            server.broadcastLobby();

            // Notify other clients that someone joined
            server.broadcast(Message.log(playerName + " joined the game."));

            // ── Message loop ──────────────────────────────────────────────
            String line;
            while (alive && (line = in.readLine()) != null) {
                Message msg = Message.parse(line);
                if (msg != null) {
                    server.onMessage(this, msg);
                }
            }

        } catch (IOException e) {
            if (alive) {
                server.onLog.accept("[Server] Client " + playerName
                        + " disconnected: " + e.getMessage());
            }
        } finally {
            close();
            server.onClientDisconnect(this);
        }
    }

    /** Thread-safe send. Silently drops if the connection is closed. */
    synchronized void send(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    void close() {
        alive = false;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
