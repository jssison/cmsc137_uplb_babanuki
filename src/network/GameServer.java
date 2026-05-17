package network;

import logic.GameLoop;
import logic.TrapCardHandler;
import model.Card;
import model.Deck;
import model.GameState;
import model.Player;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * GameServer
 *
 * Hosts a Babanuki game over a TCP socket.
 * Responsibilities:
 *  - Accept incoming client connections up to MAX_PLAYERS
 *  - Assign each client a Player slot
 *  - Own and run the GameLoop on a background thread
 *  - Broadcast STATE snapshots to all clients after every game event
 *  - Route DRAW and TRAP_CHOICE messages from clients to the GameLoop
 *
 * Usage (from UI or main):
 *   GameServer server = new GameServer(port, humanSlotCount, cpuCount, onLog);
 *   server.start();          // begins accepting connections
 *   // ... wait for players to connect ...
 *   server.startGame();      // deal cards and launch GameLoop
 */
public class GameServer {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int DEFAULT_PORT = 55555;
    public static final int MAX_PLAYERS  = 4;

    // ── State ─────────────────────────────────────────────────────────────────

    private final int port;
    private final int humanSlots;   // how many network human players to wait for
    private final int cpuCount;     // CPU bots to fill remaining seats
    final Consumer<String> onLog; // UI log callback (runs on any thread)

    private ServerSocket serverSocket;
    private final List<ClientHandler> handlers = new CopyOnWriteArrayList<>();

    private GameState  gameState;
    private GameLoop   gameLoop;
    private Thread     gameThread;

    // Players are indexed by their slot number (matches ClientHandler.slot)
    private final List<Player> players = new ArrayList<>();

    // Pending trap choice: which client slot needs to respond, and the callback
    private volatile int               pendingTrapSlot     = -1;
    private volatile Consumer<Player>  pendingTrapCallback = null;
    private volatile List<Player>      pendingTrapTargets  = null;

    private volatile boolean running = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param port        TCP port to listen on
     * @param humanSlots  number of human (networked) player seats
     * @param cpuCount    number of CPU bot seats
     * @param onLog       callback to receive server-side log messages
     */
    public GameServer(int port, int humanSlots, int cpuCount, Consumer<String> onLog) {
        this.port       = port;
        this.humanSlots = Math.min(humanSlots, MAX_PLAYERS);
        this.cpuCount   = cpuCount;
        this.onLog      = (onLog != null) ? onLog : System.out::println;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens the server socket and spawns an acceptor thread.
     * Returns immediately; connections are handled in the background.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running      = true;
        onLog.accept("[Server] Listening on port " + port
                + " | Waiting for " + humanSlots + " player(s)...");

        Thread acceptor = new Thread(this::acceptLoop, "Server-Acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** Stop the server and close all connections. */
    public void stop() {
        running = false;
        if (gameLoop  != null) gameLoop.stop();
        if (gameThread != null) gameThread.interrupt();
        for (ClientHandler h : handlers) h.close();
        try { if (serverSocket != null) serverSocket.close(); }
        catch (IOException ignored) {}
        onLog.accept("[Server] Stopped.");
    }

    /** Deal cards and start the GameLoop. Call after all humans have connected. */
    public void startGame() {
        if (players.isEmpty()) buildPlayers();

        Deck deck = new Deck();
        deck.shuffle();
        deck.dealTo(players);

        // Discard initial pairs
        for (Player p : players) {
            List<Card> discarded = p.discardNonTrapPairs();
            if (!discarded.isEmpty()) {
                gameState.log(p.getName() + " discarded " + (discarded.size() / 2) + " pair(s) at start");
            }
        }

        gameState.addLogListener(msg -> broadcast(Message.log(msg)));
        gameState.addStateChangeListener(this::broadcastState);

        // Broadcast initial player list
        String[] names = players.stream().map(Player::getName).toArray(String[]::new);
        broadcast(Message.playerList(names));
        broadcastState();

        // Wire animation callbacks (server-side: no real animations, just countdowns)
        GameLoop.AnimationCallback animCb = buildAnimationCallback();

        // Wire trap chooser
        TrapCardHandler.TargetChooser trapChooser = buildTrapChooser();

        gameLoop   = new GameLoop(gameState, trapChooser, animCb);
        gameThread = new Thread(gameLoop, "Server-GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();

        onLog.accept("[Server] Game started with " + players.size() + " players.");
    }

    // ── Internal: build players ───────────────────────────────────────────────

    private void buildPlayers() {
        players.clear();

        // Network human players (one per connected client)
        for (int i = 0; i < handlers.size(); i++) {
            ClientHandler h = handlers.get(i);
            Player p = new Player(h.playerName, true);
            players.add(p);
            h.slot = i;
        }

        // Fill remaining seats with CPUs
        int totalHumans = handlers.size();
        for (int i = 0; i < cpuCount; i++) {
            players.add(new Player("CPU " + (i + 1), false));
        }

        gameState = new GameState(players);
    }

    // ── Internal: accept loop ─────────────────────────────────────────────────
    
    private void acceptLoop() {
        while (running && handlers.size() < humanSlots) {
            try {
                Socket socket = serverSocket.accept();
                int slot = handlers.size();
                ClientHandler handler = new ClientHandler(socket, slot, this);
                handlers.add(handler);
                Thread t = new Thread(handler, "Client-" + slot);
                t.setDaemon(true);
                t.start();
                onLog.accept("[Server] Player connected: slot " + slot
                        + " from " + socket.getInetAddress().getHostAddress());

         
                if (handlers.size() == humanSlots) {
                    onLog.accept("[Server] All players connected — starting game in a moment...");
                    
                    // Spawn a thread to wait 800ms for all players
                    new Thread(() -> {
                        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                        startGame();
                    }).start();
                }
            } catch (IOException e) {
                if (running) onLog.accept("[Server] Accept error: " + e.getMessage());
            }
        }
    }

    // ── Internal: handle incoming messages from a client ─────────────────────

    void onMessage(ClientHandler sender, Message msg) {
        switch (msg.type) {
            case Message.DRAW      -> handleDraw(sender, msg);
            case Message.TRAP_CHOICE -> handleTrapChoice(sender, msg);
            case Message.TRAP_PLAY -> handleTrapPlay(sender, msg);
            case Message.CHAT      -> broadcast(Message.chat(sender.playerName, msg.part(0)));
            default -> sender.send(Message.error("Unknown message type: " + msg.type));
        }
    }

    private void handleTrapPlay(ClientHandler sender, Message msg) {
        if (gameLoop == null || sender.slot >= players.size()) return;
        String trapName = msg.part(0);
        model.Card.Trap trap;
        try { trap = model.Card.Trap.valueOf(trapName); }
        catch (IllegalArgumentException e) { sender.send(Message.error("unknown trap: " + trapName)); return; }
        Player human = players.get(sender.slot);
        gameLoop.submitHumanTrapPlay(trap);
        gameLoop.processHumanTrapPlay(human);
    }

    private void handleDraw(ClientHandler sender, Message msg) {
        if (gameState == null || gameLoop == null) return;

        // Parse: DRAW|targetSlot|cardIndex
        try {
            int targetSlot = Integer.parseInt(msg.part(0));
            int cardIndex  = Integer.parseInt(msg.part(1));

            if (targetSlot < 0 || targetSlot >= players.size()) {
                sender.send(Message.error("Invalid target slot: " + targetSlot));
                return;
            }

            Player target = players.get(targetSlot);
            Player drawer = players.get(sender.slot);

            // Let the GameLoop process it
            gameLoop.submitHumanDraw(target, cardIndex);
            onLog.accept("[Server] " + drawer.getName()
                    + " drew card " + cardIndex + " from " + target.getName());
        } catch (NumberFormatException e) {
            sender.send(Message.error("Malformed DRAW message: " + msg.raw));
        }
    }

    private void handleTrapChoice(ClientHandler sender, Message msg) {
        if (pendingTrapSlot != sender.slot || pendingTrapCallback == null) {
            sender.send(Message.error("No trap choice pending for you."));
            return;
        }

        try {
            int chosenSlot = Integer.parseInt(msg.part(0));
            if (chosenSlot < 0 || chosenSlot >= players.size()) {
                sender.send(Message.error("Invalid trap target slot: " + chosenSlot));
                return;
            }

            Player chosen = players.get(chosenSlot);

            // Make sure the chosen player is actually in the valid targets list
            if (pendingTrapTargets != null && !pendingTrapTargets.contains(chosen)) {
                sender.send(Message.error("Player not a valid trap target."));
                return;
            }

            Consumer<Player> cb = pendingTrapCallback;
            pendingTrapCallback = null;
            pendingTrapSlot     = -1;
            pendingTrapTargets  = null;

            cb.accept(chosen);

        } catch (NumberFormatException e) {
            sender.send(Message.error("Malformed TRAP_CHOICE: " + msg.raw));
        }
    }

    // ── Internal: broadcast helpers ───────────────────────────────────────────

    void broadcast(String message) {
        for (ClientHandler h : handlers) h.send(message);
    }

    /** Send to one specific slot only. */
    void sendTo(int slot, String message) {
        for (ClientHandler h : handlers) {
            if (h.slot == slot) { h.send(message); return; }
        }
    }

    private void broadcastState() {
        if (gameState == null) return;
        List<Player> ps = gameState.getPlayers();

        // build shared STATE: name:handSize:drawState[:OUT] — same for everyone
        StringBuilder stateSb = new StringBuilder();
        for (int i = 0; i < ps.size(); i++) {
            Player p = ps.get(i);
            if (i > 0) stateSb.append(",");
            stateSb.append(p.getName())
                   .append(":").append(p.handSize())
                   .append(":").append(p.getDrawState().name());
            if (p.getIsOut()) stateSb.append(":OUT");
        }
        broadcast(Message.state(stateSb.toString()));

        // send each human client their own hand as a separate HAND message
        // format per card: displayStr:RED|BLACK:TRAPNAME|NONE
        for (ClientHandler h : handlers) {
            if (h.slot < 0 || h.slot >= ps.size()) continue;
            List<model.Card> hand = ps.get(h.slot).getHand();
            StringBuilder handSb = new StringBuilder();
            for (int j = 0; j < hand.size(); j++) {
                if (j > 0) handSb.append(",");
                model.Card c = hand.get(j);
                handSb.append(c.toString())
                      .append(":").append(c.getSuit().isRed() ? "RED" : "BLACK")
                      .append(":").append(c.isTrap() ? c.getTrap().name() : "NONE");
            }
            h.send(Message.hand(h.slot, handSb.toString()));
        }

        if (gameState.isFinished()) broadcastGameOver();
    }

    private void broadcastGameOver() {
        List<Player> lb = gameState.getLeaderboard();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lb.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(lb.get(i).getName());
        }
        broadcast(Message.gameOver(sb.toString()));
        onLog.accept("[Server] Game over. Leaderboard: " + sb);
    }

    // ── Internal: animation callback (server has no real UI) ─────────────────

    private GameLoop.AnimationCallback buildAnimationCallback() {
        return new GameLoop.AnimationCallback() {
            @Override
            public void playStealAnimation(Player stealer, Player target,
                                            int cardIndex, Runnable onComplete) {
                // Server has no UI — fire onComplete immediately
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void playDiscardAnimation(Player player, List<Card> discardedCards) {
                // No-op on server
            }
        };
    }

    // ── Internal: trap chooser ────────────────────────────────────────────────

    private TrapCardHandler.TargetChooser buildTrapChooser() {
        return (prompt, targets, onChosen) -> {
            if (targets.isEmpty()) return;

            // Find the human client who activated the trap
            // We identify by checking which human player just acted.
            // Simplification: find the first non-CPU human client that is still in
            // A more robust approach stores the "activator" — wired below.
            // For now, we ask client slot 0 if they're human, else auto-pick.

            // Find the activating human client
            ClientHandler humanClient = handlers.stream()
                .filter(h -> h.slot < players.size() && players.get(h.slot).getIsHuman())
                .findFirst()
                .orElse(null);

            if (humanClient == null) {
                // All humans gone or no clients — auto pick first target
                onChosen.accept(targets.get(0));
                return;
            }

            // Build target name list for the client
            StringBuilder targetSlots = new StringBuilder();
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) targetSlots.append(",");
                // Ask the master players list for this exact target's slot ID!
                targetSlots.append(players.indexOf(targets.get(i)));
            }
            // Extract trap name from prompt (e.g. "SINGKO! Choose...")
            String trapName = prompt.split("!")[0].trim();

            // Set pending state so handleTrapChoice can resolve it
            pendingTrapSlot     = humanClient.slot;
            pendingTrapCallback = onChosen;
            pendingTrapTargets  = new ArrayList<>(targets);

            humanClient.send(Message.trapPrompt(trapName, targetSlots.toString()));
            onLog.accept("[Server] Trap prompt sent to " + humanClient.playerName);
        };
    }

    // ── Getters (for UI to poll) ──────────────────────────────────────────────

    public int getConnectedCount() { return handlers.size(); }
    public int getHumanSlots()     { return humanSlots; }
    public boolean isRunning()     { return running; }
}