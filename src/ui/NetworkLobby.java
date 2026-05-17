package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import network.GameClient;
import network.GameServer;
import network.Message;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NetworkLobby extends BorderPane {

    private final String playerName;
    private final BiConsumer<GameServer, GameClient> onHostReady;
    private final Consumer<GameClient> onJoinReady;
    private final Runnable onCancel;

    // Visual Containers
    private final HBox playerCardContainer = new HBox(20);
    private final VBox controlArea = new VBox(20);
    private final TextArea logArea = new TextArea();
    
    private final TextField ipInput = new TextField("localhost");

    private static final String[] ANIMAL_ICONS = {
        "bear.png", "cat.png", "cow.png", "dog.png", "dragon.png", 
        "monkey.png", "pig.png", "rabbit.png", "rat.png"
    };
    
    public NetworkLobby(String playerName, BiConsumer<GameServer, GameClient> onHostReady, Consumer<GameClient> onJoinReady, Runnable onCancel) {
        this.playerName = playerName;
        this.onHostReady = onHostReady;
        this.onJoinReady = onJoinReady;
        this.onCancel = onCancel;

        setStyle("-fx-background-color: #122a1e;");
        setPadding(new Insets(30));

        // ── Top: Header ───────────────────────────────────────────────────────
        Label title = new Label("Multiplayer Staging Area");
        title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #e8c87a;");
        
        Label userLabel = new Label("Playing as: " + playerName);
        userLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 16px; -fx-text-fill: #8ca898;");
        
        VBox header = new VBox(5, title, userLabel);
        header.setAlignment(Pos.CENTER);
        setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 40, 0));

        // ── Center: Visual Player Slots ───────────────────────────────────────
        playerCardContainer.setAlignment(Pos.CENTER);
        // Add 4 empty placeholders initially
        for (int i = 0; i < 4; i++) {
            playerCardContainer.getChildren().add(buildEmptyCard());
        }
        setCenter(playerCardContainer);

        // ── Bottom: Controls & Network Log ────────────────────────────────────
        buildControlArea();
        
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setStyle("-fx-control-inner-background: #0d1f16; -fx-font-family: 'DM Mono', monospace; -fx-text-fill: #a8d5b5; -fx-border-color: #2e6644;");
        appendLog("Welcome to the Lobby, " + playerName + "!");

        HBox bottomSection = new HBox(30, controlArea, logArea);
        bottomSection.setAlignment(Pos.CENTER);
        HBox.setHgrow(logArea, Priority.ALWAYS);
        setBottom(bottomSection);
        BorderPane.setMargin(bottomSection, new Insets(40, 0, 0, 0));
    }

    private void buildControlArea() {
        controlArea.getChildren().clear();
        controlArea.setAlignment(Pos.TOP_LEFT);
        controlArea.setPrefWidth(320);

        // --- HOST SETTINGS ---
        VBox hostBox = new VBox(10);
        hostBox.setStyle("-fx-background-color: #1a3a2a; -fx-padding: 15; -fx-border-color: #2e6644; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label hostTitle = styledLabel("HOST A GAME");
        
        HBox hostPortRow = new HBox(10, styledLabel("Port:"), createInput(String.valueOf(GameServer.DEFAULT_PORT), 80));
        hostPortRow.setAlignment(Pos.CENTER_LEFT);
        TextField hostPortInput = (TextField) hostPortRow.getChildren().get(1);

        HBox botRow = new HBox(10, styledLabel("CPU Bots:"));
        botRow.setAlignment(Pos.CENTER_LEFT);
        Spinner<Integer> botSpinner = new Spinner<>(0, 3, 3);
        botSpinner.setPrefWidth(80);
        botSpinner.setStyle("-fx-base: #0d1f16; -fx-control-inner-background: #0d1f16; -fx-text-fill: #fdf6e3;");
        botRow.getChildren().add(botSpinner);

        Button hostBtn = makeButton("Start Server", "#2e6644", "#e8c87a");
        hostBtn.setOnAction(e -> startHosting(hostPortInput.getText(), botSpinner.getValue()));
        hostBox.getChildren().addAll(hostTitle, hostPortRow, botRow, hostBtn);

        // --- JOIN SETTINGS ---
        VBox joinBox = new VBox(10);
        joinBox.setStyle("-fx-background-color: #1a3a2a; -fx-padding: 15; -fx-border-color: #2e6644; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label joinTitle = styledLabel("JOIN A GAME");
        
        HBox ipRow = new HBox(10, styledLabel("IP:"), createInput("localhost", 120));
        ipRow.setAlignment(Pos.CENTER_LEFT);
        TextField ipInput = (TextField) ipRow.getChildren().get(1);

        HBox joinPortRow = new HBox(10, styledLabel("Port:"), createInput(String.valueOf(GameServer.DEFAULT_PORT), 80));
        joinPortRow.setAlignment(Pos.CENTER_LEFT);
        TextField joinPortInput = (TextField) joinPortRow.getChildren().get(1);

        Button joinBtn = makeButton("Connect", "#1c4d8c", "#e8c87a");
        joinBtn.setOnAction(e -> startJoining(ipInput.getText(), joinPortInput.getText()));
        joinBox.getChildren().addAll(joinTitle, ipRow, joinPortRow, joinBtn);

        // --- BACK BUTTON ---
        Button backBtn = makeButton("Leave Lobby", "#4a2e2e", "#e05555");
        backBtn.setOnAction(e -> onCancel.run());
        VBox.setMargin(backBtn, new Insets(10, 0, 0, 0));

        controlArea.getChildren().addAll(hostBox, joinBox, backBtn);
    }
    
    // --- Helper for the new inputs ---
    private TextField createInput(String defaultText, int width) {
        TextField field = new TextField(defaultText);
        field.setPrefWidth(width);
        field.setStyle("-fx-background-color: #0d1f16; -fx-text-fill: #fdf6e3; -fx-border-color: #2e6644; -fx-border-radius: 4; -fx-padding: 4 8;");
        return field;
    }

    private Label styledLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-weight: bold; -fx-text-fill: #8ca898; -fx-font-size: 12px;");
        lbl.setPrefWidth(65); // Align the inputs cleanly
        return lbl;
    }

    private void startHosting(String portStr, int botCount) {
        int port = parsePort(portStr, GameServer.DEFAULT_PORT);
        int humanSlots = GameServer.MAX_PLAYERS - botCount; 
        
        appendLog("Starting local server on port " + port + " with " + botCount + " bots...");
        try {
            GameServer srv = new GameServer(port, humanSlots, botCount, this::appendLog);
            srv.start();

            GameClient cli = new GameClient("localhost", port, playerName, null);
            
            // THE FIX: Set up the UI and the Callbacks FIRST!
            enterWaitingRoom(cli, srv, true);
            
            // THEN open the network connection!
            cli.connect();
            
        } catch (IOException e) {
            appendLog("[ERROR] Could not start Host: " + e.getMessage());
        }
    }

    private void startJoining(String ip, String portStr) {
        if (ip.isEmpty()) ip = "localhost";
        int port = parsePort(portStr, GameServer.DEFAULT_PORT);
        appendLog("Attempting to connect to " + ip + ":" + port + "...");
        
        try {
            GameClient cli = new GameClient(ip, port, playerName, null);
            
            // THE FIX: Set up the UI and the Callbacks FIRST!
            enterWaitingRoom(cli, null, false);
            
            // THEN open the network connection!
            cli.connect();
            
        } catch (IOException e) {
            appendLog("[ERROR] Could not Join: " + e.getMessage());
        }
    }

    private int parsePort(String portStr, int defaultPort) {
        try { return Integer.parseInt(portStr.trim()); } 
        catch (NumberFormatException e) { return defaultPort; }
    }
    
    private void enterWaitingRoom(GameClient cli, GameServer srv, boolean isHost) {
        // 1. Swap the UI Controls
        controlArea.getChildren().clear();
        controlArea.setAlignment(Pos.CENTER);

        if (isHost) {
            Button startBtn = makeButton("Start Match", "#2e6644", "#e8c87a");
            startBtn.setOnAction(e -> cli.send(Message.startGame())); // Tells server to begin!
            controlArea.getChildren().add(startBtn);
        } else {
            Label waitLbl = new Label("Waiting for Host to start match...");
            waitLbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-text-fill: #8ca898; -fx-font-size: 16px; -fx-font-weight: bold;");
            controlArea.getChildren().add(waitLbl);
        }

        Button leaveBtn = makeButton("Leave Lobby", "#4a2e2e", "#e05555");
        leaveBtn.setOnAction(e -> {
            cli.disconnect();
            if (srv != null) srv.stop();
            onCancel.run();
        });
        controlArea.getChildren().add(leaveBtn);

        // 2. Wire up temporary Network Listeners for the Lobby
        cli.setCallbacks(new GameClient.Callbacks() {
            @Override public void onWelcome(int slot) {
                cli.mySlot = slot;
                cli.send(Message.setName(playerName)); // Send our real name!
            }
            @Override public void onLobbyUpdate(String[] slots) {
                Platform.runLater(() -> updatePlayerCards(slots)); // Draw the Avatars!
            }
            @Override public void onPlayerList(String[] names) {
                // The Server yelled START! Hand control over to Main.java and swap scenes!
                Platform.runLater(() -> {
                    if (isHost) onHostReady.accept(srv, cli);
                    else onJoinReady.accept(cli);
                });
            }
            @Override public void onLog(String msg) { appendLog(msg); }
            @Override public void onState(String[] entries) {}
            @Override public void onHand(String[] entries) {}
            @Override public void onTrapPrompt(String t, String[] opts) {}
            @Override public void onGameOver(String[] names) {}
            @Override public void onChat(String s, String txt) {}
            @Override public void onDisconnect(String reason) {
                Platform.runLater(() -> appendLog("[Disconnect] " + reason));
            }
        });
    }

    private void updatePlayerCards(String[] slots) {
        playerCardContainer.getChildren().clear();
        for (int i = 0; i < 4; i++) {
            if (i < slots.length && !slots[i].isEmpty()) {
                // Parse "slot:name:type"
                String[] parts = slots[i].split(":");
                String name = parts.length > 1 ? parts[1] : "Unknown";
                String type = parts.length > 2 ? parts[2] : "Human";
                playerCardContainer.getChildren().add(buildPlayerCard(name, type, i));
            } else {
                playerCardContainer.getChildren().add(buildEmptyCard());
            }
        }
    }

    private VBox buildPlayerCard(String name, String type, int slotId) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(180, 240);
        card.setStyle("-fx-background-color: #1a3a2a; -fx-border-color: #2e6644; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12;");

        // Use deterministic Avatars!
        String iconFilename = type.equals("Bot") ? "dog.png" : ANIMAL_ICONS[slotId % ANIMAL_ICONS.length];
        String fullImagePath = "/assets/avatars/" + iconFilename;

        javafx.scene.image.ImageView avatar = new javafx.scene.image.ImageView();
        try { avatar.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream(fullImagePath))); } catch (Exception ignored) {}
        avatar.setFitWidth(64); avatar.setFitHeight(64);
        avatar.setClip(new javafx.scene.shape.Circle(32, 32, 32));

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-text-fill: #e8c87a; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label typeLbl = new Label(type);
        typeLbl.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-text-fill: #8ca898; -fx-font-size: 12px;");

        card.getChildren().addAll(avatar, nameLbl, typeLbl);
        return card;
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private VBox buildEmptyCard() {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(180, 240);
        card.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: #2e6644;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 12;" +
            "-fx-border-style: dashed;"
        );

        Label lbl = new Label("Empty Slot");
        lbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-text-fill: #8ca898; -fx-font-size: 14px;");
        card.getChildren().add(lbl);
        return card;
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setPrefWidth(220);
        btn.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + fg + "44;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 10;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }
}