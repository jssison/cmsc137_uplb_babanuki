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
        
        default void onAnimSteal(int stealerSlot, int targetSlot, int cardIndex) {}
        default void onAnimDiscard(int slot, String[] cards) {}
        default void onLobbyUpdate(String[] slots) {}
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
    
    /** tell server to shuffle my hand. */
    public void sendShuffle() { 
        send(Message.shuffle()); 
    }
    
    public void send(String message) {
    	if (out != null && connected) {
    		out.println(message);
    	}
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void readLoop() {
        try {
            String line;
            boolean nameSet = false;

            while (connected && (line = in.readLine()) != null) {
                Message msg = Message.parse(line);
                if (msg == null) continue;

                switch (msg.type) {

	                case Message.WELCOME -> {
	                    int slot = Integer.parseInt(msg.part(0));
	                    this.mySlot = slot;
	                    if (callbacks != null) callbacks.onWelcome(slot); 
	                }
	
	                case Message.PLAYER_LIST -> {
	                    String namesCsv = msg.part(0);
	                    String[] names = namesCsv.isEmpty() ? new String[0] : namesCsv.split(",", -1);
	                    cachedPlayerList = names;
	                    if (callbacks != null) callbacks.onPlayerList(names); 
	                }
	
	                case Message.STATE -> {
	                    String payload = msg.part(0);
	                    String[] entries = payload.isEmpty() ? new String[0] : payload.split(",", -1);
	                    cachedState = entries;
	                    if (callbacks != null) callbacks.onState(entries); 
	                }
	
	                case Message.LOG -> {
	                    if (callbacks != null) callbacks.onLog(msg.part(0)); 
	                }
	
	                case Message.TRAP_PROMPT -> {
	                    String trapName = msg.part(0);
	                    String[] opts = msg.part(1).split(",", -1);
	                    if (callbacks != null) callbacks.onTrapPrompt(trapName, opts);
	                }
	
	                case Message.GAME_OVER -> {
	                    String[] lb = msg.part(0).split(",", -1);
	                    if (callbacks != null) callbacks.onGameOver(lb); 
	                }
	                
	                case Message.HAND -> {
	                    String cardsCsv = msg.part(1);
	                    String[] entries = cardsCsv.isEmpty() ? new String[0] : cardsCsv.split(",", -1);
	                    cachedHand = entries; 
	                    if (callbacks != null) callbacks.onHand(entries); 
	                }
	
	                case Message.ERROR -> {
	                    if (callbacks != null) callbacks.onLog("[ERROR] " + msg.part(0)); 
	                }
                    
                    case Message.ANIM_STEAL -> {
                        int stealer = Integer.parseInt(msg.part(0));
                        int target  = Integer.parseInt(msg.part(1));
                        int cardIdx = Integer.parseInt(msg.part(2));
                        callbacks.onAnimSteal(stealer, target, cardIdx);
                    }

                    case Message.ANIM_DISCARD -> {
                        int slot = Integer.parseInt(msg.part(0));
                        String[] cards = msg.part(1).split(",", -1);
                        callbacks.onAnimDiscard(slot, cards);
                    }
                    
                    case Message.LOBBY_STATE -> {
                        String payload = msg.part(0);
                        String[] slots = payload.isEmpty() ? new String[0] : payload.split(",", -1);
                        if (callbacks != null) callbacks.onLobbyUpdate(slots);
                    }
                    // ----------------------------------------------

                    
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