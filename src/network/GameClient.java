package network;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * GameClient
 *
 * Connects to a GameServer over TCP.
 * Responsibilities:
 *  - Handshake: send player name, receive assigned slot
 *  - Send DRAW and TRAP_CHOICE actions to the server
 *  - Receive STATE, LOG, TRAP_PROMPT, GAME_OVER broadcasts and fire callbacks
 *
 * All callbacks are fired on the reader thread — UI code must
 * wrap them in Platform.runLater() where needed.
 *
 * Usage:
 *   GameClient client = new GameClient(host, port, "Alice", callbacks);
 *   client.connect();
 *   // later:
 *   client.sendDraw(targetSlot, cardIndex);
 *   client.disconnect();
 */
public class GameClient {

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * UI hooks that the client fires when server messages arrive.
     * Implement this in your multiplayer GameView.
     */
    public interface Callbacks {
        /** Called once, right after handshake. slot = your player index. */
        void onWelcome(int slot);

        /** Ordered list of all player names received from server. */
        void onPlayerList(String[] names);

        /**
         * Full state snapshot.
         * Each entry is "name:handSize:drawState" or "name:handSize:drawState:OUT".
         */
        void onState(String[] playerEntries);

        /** A log line to display in the EventLog. */
        void onLog(String message);

        /**
         * Server asks this client to pick a trap target.
         * @param trapName    e.g. "SINGKO"
         * @param targetNames comma-separated eligible names
         */
        void onTrapPrompt(String trapName, String[] targetSlots);

        /** Game finished. orderedNames[0] is 1st place, last is the Babanuki loser. */
        void onGameOver(String[] orderedNames);

        /** A chat message arrived. */
        void onChat(String senderName, String text);

        /** connection dropped or server error. */
        void onDisconnect(String reason);

        /** server sends this client its own hand. each entry: "displayStr:RED|BLACK:trapName|NONE" */
        void onHand(String[] cardEntries);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String    host;
    private final int       port;
    private final String    playerName;
    private volatile Callbacks callbacks;

    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;

    private volatile boolean connected = false;
    public  volatile int     mySlot    = -1; // set after WELCOME
    public volatile String[] cachedPlayerList = null; 
    public volatile String[] cachedState = null;      
    public volatile String[] cachedHand = null;       

    // ── Constructor ───────────────────────────────────────────────────────────

    public GameClient(String host, int port, String playerName, Callbacks callbacks) {
        this.host       = host;
        this.port       = port;
        this.playerName = playerName;
        this.callbacks  = callbacks;
    }

    // swap callbacks after connect (e.g. lobby -> game view)
    public void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Connects to the server, performs handshake, and starts the reader thread.
     * Blocking until the socket is open; reader runs in background.
     */
    public void connect() throws IOException {
        socket    = new Socket(host, port);
        out       = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())), true);
        in        = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
        connected = true;

        // Start background reader
        Thread reader = new Thread(this::readLoop, "Client-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Close the connection. */
    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    // ── Sending actions ───────────────────────────────────────────────────────

    /**
     * Tell the server we want to draw card at cardIndex from the player at targetSlot.
     */
    public void sendDraw(int targetSlot, int cardIndex) {
        send(Message.draw(targetSlot, cardIndex));
    }

    /**
     * Respond to a TRAP_PROMPT with the chosen target's slot number.
     */
    public void sendTrapChoice(int targetSlot) {
        send(Message.trapChoice(targetSlot));
    }

    /** tell server to play a trap pair. */
    public void sendTrapPlay(String trapName) { send(Message.trapPlay(trapName)); }

    /** send a chat message. */
    public void sendChat(String text) {
        send(Message.chat(playerName, text));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void send(String message) {
        if (out != null && connected) {
            out.println(message);
        }
    }

    private void readLoop() {
        try {
            String line;
            boolean nameSet = false;

            while (connected && (line = in.readLine()) != null) {
                Message msg = Message.parse(line);
                if (msg == null) continue;

                switch (msg.type) {

                    case Message.WELCOME -> {
                        mySlot = Integer.parseInt(msg.part(0));
                        callbacks.onWelcome(mySlot);

                        // After receiving WELCOME, send our name to the server
                        if (!nameSet) {
                            out.println(playerName);
                            nameSet = true;
                        }
                    }

                    case Message.PLAYER_LIST -> {
                        String[] names = msg.part(0).split(",", -1);
                        cachedPlayerList = names; // Save to cache!
                        callbacks.onPlayerList(names);
                    }

                    case Message.STATE -> {
                        String[] entries = msg.part(0).split(",", -1);
                        cachedState = entries; // Save to cache!
                        callbacks.onState(entries);
                    }

                    case Message.LOG -> {
                        callbacks.onLog(msg.part(0));
                    }

                    case Message.TRAP_PROMPT -> {
                        String   trapName    = msg.part(0);
                        String[] targetNames = msg.part(1).split(",", -1);
                        callbacks.onTrapPrompt(trapName, targetNames);
                    }

                    case Message.GAME_OVER -> {
                        String[] names = msg.part(0).split(",", -1);
                        callbacks.onGameOver(names);
                    }

                    case Message.CHAT -> {
                        callbacks.onChat(msg.part(0), msg.part(1));
                    }

                    case Message.HAND -> {
	                    String cardsCsv = msg.part(1);
	                    String[] entries = cardsCsv.isEmpty() ? new String[0] : cardsCsv.split(",", -1);
	                    cachedHand = entries; // Save to cache!
	                    callbacks.onHand(entries);
	                }

                    
                    case Message.ERROR -> {
                        callbacks.onLog("[ERROR] " + msg.part(0));
                    }

                    
                    default -> {
                        callbacks.onLog("[Client] Unknown message: " + msg.raw);
                    }
                }
            }
            
            if (connected && callbacks != null) {
                callbacks.onDisconnect("Server closed the connection.");
            }
            
        } catch (IOException e) {
            if (connected) {
                callbacks.onDisconnect("Connection lost: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isConnected() { return connected; }
    public String  getPlayerName() { return playerName; }
    public String  getHost()       { return host; }
    public int     getPort()       { return port; }
}