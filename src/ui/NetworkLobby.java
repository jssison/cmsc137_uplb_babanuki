package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import network.GameClient;
import network.GameServer;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * NetworkLobby
 *
 * A JavaFX screen shown when the player picks "Multiplayer".
 * Has two tabs:
 *   HOST — enter port, number of human players, CPU count → start server + auto-connect
 *   JOIN — enter host IP, port → connect as client
 *
 * Callbacks:
 *   onHostReady(GameServer, GameClient) — server is running AND host client is connected
 *   onJoinReady(GameClient)             — client connected to a remote server
 *   onCancel()                          — back to main menu
 */
public class NetworkLobby extends VBox {

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private final BiConsumer<GameServer, GameClient> onHostReady;
    private final Consumer<GameClient>               onJoinReady;
    private final Runnable                           onCancel;
    private final String                             playerName;


    // ── Fields ────────────────────────────────────────────────────────────────

    private final TextArea logArea = new TextArea();

    // HOST tab fields
    private TextField hostPortField;
    private Spinner<Integer> humanSpinner;
    private Spinner<Integer> cpuSpinner;
    private Button    hostStartBtn;

    // JOIN tab fields
    private TextField joinHostField;
    private TextField joinPortField;
    private TextField joinNameField;
    private Button    joinConnectBtn;

    // ── Constructor ───────────────────────────────────────────────────────────

    public NetworkLobby(
            String                             playerName,
            BiConsumer<GameServer, GameClient> onHostReady,
            Consumer<GameClient>               onJoinReady,
            Runnable                           onCancel) {

        this.playerName  = playerName;
        this.onHostReady = onHostReady;
        this.onJoinReady = onJoinReady;
        this.onCancel    = onCancel;

        setAlignment(Pos.CENTER);
        setSpacing(20);
        setPadding(new Insets(40));
        setStyle("-fx-background-color: #122a1e;");

        Label title = new Label("Multiplayer Lobby");
        title.setStyle(
            "-fx-font-family: 'Playfair Display', serif;" +
            "-fx-font-size: 36px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #e8c87a;"
        );

        TabPane tabs = new TabPane();
        tabs.setMaxWidth(480);
        tabs.setStyle("-fx-background-color: #1a3a2a;");
        tabs.getTabs().addAll(buildHostTab(), buildJoinTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Log area
        logArea.setEditable(false);
        logArea.setMaxWidth(480);
        logArea.setPrefHeight(120);
        logArea.setStyle(
            "-fx-font-family: 'DM Mono', monospace;" +
            "-fx-font-size: 12px;" +
            "-fx-background-color: #0d1f16;" +
            "-fx-text-fill: #a8d5b5;" +
            "-fx-control-inner-background: #0d1f16;"
        );

        Button cancelBtn = makeButton("← Back to Menu", "#2e6644", "#e8c87a");
        cancelBtn.setOnAction(e -> onCancel.run());

        getChildren().addAll(title, tabs, logArea, cancelBtn);
    }

    // ── HOST tab ──────────────────────────────────────────────────────────────

    private Tab buildHostTab() {
        Tab tab = new Tab("  Host Game  ");

        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #1a3a2a;");

        // Port
        hostPortField = new TextField(String.valueOf(GameServer.DEFAULT_PORT));
        hostPortField.setMaxWidth(200);

        // Human players spinner (2–4)
        humanSpinner = new Spinner<>(2, GameServer.MAX_PLAYERS, 2);
        humanSpinner.setEditable(true);
        humanSpinner.setMaxWidth(100);

        // CPU count spinner (0–3)
        cpuSpinner = new Spinner<>(0, 3, 2);
        cpuSpinner.setEditable(true);
        cpuSpinner.setMaxWidth(100);

        // pre-filled from main menu
        TextField hostNameField = new TextField(playerName != null && !playerName.isBlank() ? playerName : "Player 1");
        hostNameField.setMaxWidth(200);

        hostStartBtn = makeButton("Start Server & Host", "#2e6644", "#e8c87a");
        hostStartBtn.setPrefWidth(220);
        hostStartBtn.setOnAction(e -> {
            hostStartBtn.setDisable(true);
            String name = hostNameField.getText().trim();
            if (name.isEmpty()) name = "Player 1";
            final String finalName = name;

            int port      = parsePort(hostPortField.getText(), GameServer.DEFAULT_PORT);
            int humans    = humanSpinner.getValue();
            int cpus      = cpuSpinner.getValue();

            new Thread(() -> startAsHost(finalName, port, humans, cpus), "Host-Thread").start();
        });

        box.getChildren().addAll(
            styledLabel("Your Name:"),    hostNameField,
            styledLabel("Port:"),          hostPortField,
            styledLabel("Human Players:"), humanSpinner,
            styledLabel("CPU Bots:"),      cpuSpinner,
            hostStartBtn
        );

        tab.setContent(box);
        return tab;
    }

    // ── JOIN tab ──────────────────────────────────────────────────────────────

    private Tab buildJoinTab() {
        Tab tab = new Tab("  Join Game  ");

        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #1a3a2a;");

        joinHostField = new TextField("localhost");
        joinHostField.setMaxWidth(260);

        joinPortField = new TextField(String.valueOf(GameServer.DEFAULT_PORT));
        joinPortField.setMaxWidth(120);

        joinNameField = new TextField(playerName != null && !playerName.isBlank() ? playerName : "Player 2");
        joinNameField.setMaxWidth(200);

        joinConnectBtn = makeButton("Connect", "#2e6644", "#e8c87a");
        joinConnectBtn.setPrefWidth(160);
        joinConnectBtn.setOnAction(e -> {
            joinConnectBtn.setDisable(true);
            String name = joinNameField.getText().trim();
            if (name.isEmpty()) name = "Player 2";
            final String finalName = name;

            String host = joinHostField.getText().trim();
            int    port = parsePort(joinPortField.getText(), GameServer.DEFAULT_PORT);

            new Thread(() -> joinAsClient(finalName, host, port), "Join-Thread").start();
        });

        box.getChildren().addAll(
            styledLabel("Your Name:"),  joinNameField,
            styledLabel("Server IP:"),  joinHostField,
            styledLabel("Port:"),        joinPortField,
            joinConnectBtn
        );

        tab.setContent(box);
        return tab;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void startAsHost(String name, int port, int humans, int cpus) {
        appendLog("[Host] Starting server on port " + port + "...");

        GameServer server = new GameServer(port, humans, cpus, this::appendLog);
        try {
            server.start();
            appendLog("[Host] Server started. Connecting as host player...");

            // Small delay so the server socket is ready
            Thread.sleep(300);

            // Host also connects as a client to their own server
            GameClient client = buildClient(name, "localhost", port);
            client.connect();

            appendLog("[Host] Connected as \"" + name + "\"! Waiting for other players...");

            // Fire callback on FX thread
            Platform.runLater(() -> onHostReady.accept(server, client));

        } catch (IOException | InterruptedException ex) {
            appendLog("[Host] Error: " + ex.getMessage());
            Platform.runLater(() -> hostStartBtn.setDisable(false));
        }
    }

    private void joinAsClient(String name, String host, int port) {
        appendLog("[Join] Connecting to " + host + ":" + port + " as \"" + name + "\"...");

        GameClient client = buildClient(name, host, port);
        try {
            client.connect();
            appendLog("[Join] Connected! Waiting for game to start...");
            Platform.runLater(() -> onJoinReady.accept(client));
        } catch (IOException ex) {
            appendLog("[Join] Failed to connect: " + ex.getMessage());
            Platform.runLater(() -> joinConnectBtn.setDisable(false));
        }
    }

    // ── Client builder with lobby-level callbacks ─────────────────────────────

    private GameClient buildClient(String name, String host, int port) {
        return new GameClient(host, port, name, new GameClient.Callbacks() {
            @Override public void onWelcome(int slot)              { appendLog("Assigned slot " + slot); }
            @Override public void onPlayerList(String[] names)     { appendLog("Players: " + String.join(", ", names)); }
            @Override public void onState(String[] entries)        { /* lobby doesn't render state */ }
            @Override public void onLog(String message)            { appendLog(message); }
            @Override public void onTrapPrompt(String t, String[] opts) {}
            @Override public void onGameOver(String[] names)       {}
            @Override public void onChat(String s, String txt)     { appendLog(s + ": " + txt); }
            @Override public void onDisconnect(String reason)      { appendLog("[Disconnected] " + reason); }
            @Override public void onHand(String[] entries)       { /* lobby ignores hand */ }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void appendLog(String msg) {
        Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
        });
    }

    private int parsePort(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #8ca898;"
        );
        return l;
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + fg + "44;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 10 20 10 20;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }
}