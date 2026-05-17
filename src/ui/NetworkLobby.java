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

public class NetworkLobby extends StackPane {

    private final BiConsumer<GameServer, GameClient> onHostReady;
    private final Consumer<GameClient> onJoinReady;
    private final Runnable onCancel;

    // ── UI Stages ─────────────────────────────────────────────────────────────
    private final VBox configStage = new VBox(30);
    private final BorderPane waitingRoomStage = new BorderPane();

    // ── Config UI Elements ────────────────────────────────────────────────────
    private final TextField nameInput = new TextField();
    private final TextField hostPortInput = new TextField(String.valueOf(GameServer.DEFAULT_PORT));
    private final TextField joinIpInput = new TextField("localhost");
    private final TextField joinPortInput = new TextField(String.valueOf(GameServer.DEFAULT_PORT));

    // ── Waiting Room UI Elements ──────────────────────────────────────────────
    private final HBox playerCardContainer = new HBox(20);
    private final VBox controlArea = new VBox(20);
    private final TextArea logArea = new TextArea();
    
    private final Spinner<Integer> botSpinner = new Spinner<>(0, 3, 0);
    private Button startMatchBtn;

    private static final String[] ANIMAL_ICONS = {
        "monkey.png","dragon.png","rat.png","rabbit.png",
        "cow.png","pig.png","bear.png","cat.png","dog.png"
    };

    public NetworkLobby(String ignoredName, BiConsumer<GameServer, GameClient> onHostReady, Consumer<GameClient> onJoinReady, Runnable onCancel) {
        this.onHostReady = onHostReady;
        this.onJoinReady = onJoinReady;
        this.onCancel = onCancel;

        setStyle("-fx-background-color: #122a1e;");
        
        buildConfigStage();
        buildWaitingRoomStage();

        // Start by showing only the Config Stage
        waitingRoomStage.setVisible(false);
        this.getChildren().addAll(waitingRoomStage, configStage);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STAGE 1: CONFIGURATION (Name, Ports, IP)
    // ══════════════════════════════════════════════════════════════════════════

    private void buildConfigStage() {
        configStage.setAlignment(Pos.CENTER);
        configStage.setMaxWidth(400);

        Label title = new Label("Multiplayer Setup");
        title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #e8c87a;");

        // Global Name Input
        VBox nameBox = new VBox(5, styledLabel("ENTER YOUR NAME:"), nameInput);
        nameInput.setPromptText("e.g. Oble");
        nameInput.setStyle("-fx-background-color: #0d1f16; -fx-text-fill: #fdf6e3; -fx-border-color: #2e6644; -fx-padding: 10; -fx-font-size: 16px;");

        // Host Panel
        VBox hostBox = new VBox(10, styledLabel("HOST A GAME"));
        hostBox.setStyle("-fx-background-color: #1a3a2a; -fx-padding: 15; -fx-border-color: #2e6644; -fx-border-radius: 8;");
        HBox hPortRow = new HBox(10, styledLabel("Port:"), hostPortInput);
        hPortRow.setAlignment(Pos.CENTER_LEFT);
        Button hostBtn = makeButton("Start Server", "#2e6644", "#e8c87a");
        hostBtn.setOnAction(e -> startHosting());
        hostBox.getChildren().addAll(hPortRow, hostBtn);

        // Join Panel
        VBox joinBox = new VBox(10, styledLabel("JOIN A GAME"));
        joinBox.setStyle("-fx-background-color: #1a3a2a; -fx-padding: 15; -fx-border-color: #2e6644; -fx-border-radius: 8;");
        HBox jIpRow = new HBox(10, styledLabel("IP:"), joinIpInput);
        HBox jPortRow = new HBox(10, styledLabel("Port:"), joinPortInput);
        jIpRow.setAlignment(Pos.CENTER_LEFT); jPortRow.setAlignment(Pos.CENTER_LEFT);
        Button joinBtn = makeButton("Connect", "#1c4d8c", "#e8c87a");
        joinBtn.setOnAction(e -> startJoining());
        joinBox.getChildren().addAll(jIpRow, jPortRow, joinBtn);

        Button backBtn = makeButton("Back to Menu", "#4a2e2e", "#e05555");
        backBtn.setOnAction(e -> onCancel.run());

        configStage.getChildren().addAll(title, nameBox, hostBox, joinBox, backBtn);
    }

    private void startHosting() {
        int port = parsePort(hostPortInput.getText(), GameServer.DEFAULT_PORT);
        try {
            // Start server with 0 bots initially. We add them in the Waiting Room!
            GameServer srv = new GameServer(port, GameServer.MAX_PLAYERS, 0, this::appendLog);
            srv.start();
            GameClient cli = new GameClient("localhost", port, getPlayerName(), null);
            enterWaitingRoom(cli, srv, true);
            cli.connect();
        } catch (IOException e) { System.out.println("Host error: " + e); }
    }

    private void startJoining() {
        String ip = joinIpInput.getText().trim();
        int port = parsePort(joinPortInput.getText(), GameServer.DEFAULT_PORT);
        try {
            GameClient cli = new GameClient(ip.isEmpty() ? "localhost" : ip, port, getPlayerName(), null);
            enterWaitingRoom(cli, null, false);
            cli.connect();
        } catch (IOException e) { System.out.println("Join error: " + e); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STAGE 2: WAITING ROOM (Avatars, Chat, Bot Spinner)
    // ══════════════════════════════════════════════════════════════════════════

    private void buildWaitingRoomStage() {
        waitingRoomStage.setPadding(new Insets(30));

        Label title = new Label("Lobby Waiting Room");
        title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #e8c87a;");
        VBox header = new VBox(title); header.setAlignment(Pos.CENTER);
        waitingRoomStage.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 40, 0));

        playerCardContainer.setAlignment(Pos.CENTER);
        waitingRoomStage.setCenter(playerCardContainer);

        logArea.setEditable(false);
        logArea.setPrefHeight(140);
        logArea.setStyle("-fx-control-inner-background: #0d1f16; -fx-font-family: 'DM Mono', monospace; -fx-text-fill: #a8d5b5; -fx-border-color: #2e6644;");

        HBox bottomSection = new HBox(30, controlArea, logArea);
        bottomSection.setAlignment(Pos.CENTER);
        HBox.setHgrow(logArea, Priority.ALWAYS);
        waitingRoomStage.setBottom(bottomSection);
        BorderPane.setMargin(bottomSection, new Insets(40, 0, 0, 0));
    }

    private void enterWaitingRoom(GameClient cli, GameServer srv, boolean isHost) {
        // Swap UI visibility
        configStage.setVisible(false);
        waitingRoomStage.setVisible(true);

        controlArea.getChildren().clear();
        controlArea.setAlignment(Pos.CENTER_LEFT);

        if (isHost) {
            Label botLbl = styledLabel("Fill Empty Slots with Bots:");
            botSpinner.setStyle("-fx-base: #0d1f16; -fx-control-inner-background: #0d1f16; -fx-text-fill: #fdf6e3;");
            
            botSpinner.valueProperty().removeListener((obs, oldVal, newVal) -> srv.setCpuCount(newVal));
            botSpinner.valueProperty().addListener((obs, oldVal, newVal) -> srv.setCpuCount(newVal));

            // THE FIX: Use the class field and disable it by default
            startMatchBtn = makeButton("Start Match", "#2e6644", "#e8c87a");
            startMatchBtn.setDisable(true); 
            startMatchBtn.setOnAction(e -> cli.send(Message.startGame()));
            
            controlArea.getChildren().addAll(botLbl, botSpinner, startMatchBtn);
        } else {
            Label waitLbl = styledLabel("Waiting for Host to start match...");
            controlArea.getChildren().add(waitLbl);
        }

        Button leaveBtn = makeButton("Leave Lobby", "#4a2e2e", "#e05555");
        leaveBtn.setOnAction(e -> {
            cli.disconnect();
            if (srv != null) srv.stop();
            waitingRoomStage.setVisible(false);
            configStage.setVisible(true); // Go back to config
        });
        controlArea.getChildren().add(leaveBtn);

        // Network Listeners
        cli.setCallbacks(new GameClient.Callbacks() {
            @Override public void onWelcome(int slot) {
                cli.mySlot = slot;
                cli.send(Message.setName(getPlayerName()));
            }
            @Override public void onLobbyUpdate(String[] slots) { Platform.runLater(() -> updatePlayerCards(slots)); }
            @Override public void onPlayerList(String[] names) {
                Platform.runLater(() -> {
                    if (isHost) onHostReady.accept(srv, cli);
                    else onJoinReady.accept(cli);
                });
            }
            @Override public void onLog(String msg) { Platform.runLater(() -> logArea.appendText(msg + "\n")); }
            @Override public void onState(String[] entries) {}
            @Override public void onHand(String[] entries) {}
            @Override public void onTrapPrompt(String t, String[] opts) {}
            @Override public void onGameOver(String[] names) {}
            @Override public void onChat(String s, String txt) {}
            @Override public void onDisconnect(String reason) { Platform.runLater(() -> logArea.appendText("[Disconnect] " + reason + "\n")); }
        });
    }

    private void updatePlayerCards(String[] slots) {
    	playerCardContainer.getChildren().clear();
        
        int humanCount = 0;
        int botCount = 0;
        
        for (int i = 0; i < GameServer.MAX_PLAYERS; i++) {
            if (i < slots.length && !slots[i].isEmpty()) {
                String[] parts = slots[i].split(":");
                String name = parts.length > 1 ? parts[1] : "Unknown";
                String type = parts.length > 2 ? parts[2] : "Human";
                
                // THE MISSING PIECE: Actually count them!
                if (type.equals("Human")) humanCount++;
                if (type.equals("Bot")) botCount++;
                
                playerCardContainer.getChildren().add(buildPlayerCard(name, type, i));
            } else {
                playerCardContainer.getChildren().add(buildEmptyCard());
            }
        }
        
        if (botSpinner.getValueFactory() != null) {
            SpinnerValueFactory.IntegerSpinnerValueFactory factory = 
                (SpinnerValueFactory.IntegerSpinnerValueFactory) botSpinner.getValueFactory();
            
            int newMax = Math.max(0, GameServer.MAX_PLAYERS - humanCount);
            
            // 1. Only change the Max if it is actually different
            if (factory.getMax() != newMax) {
                factory.setMax(newMax);
            }
            
            // 2. Only change the Value if it doesn't match what the Server says
            if (botSpinner.getValue() != botCount) {
                factory.setValue(botCount);
            }
        }
        
        if (startMatchBtn != null) {
            startMatchBtn.setDisable((humanCount + botCount) < 2);
        }
    }

    private VBox buildPlayerCard(String name, String type, int slotId) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(180, 240);
        card.setStyle("-fx-background-color: #1a3a2a; -fx-border-color: #2e6644; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12;");

        String iconFilename = type.equals("Bot") ? "dog.png" : ANIMAL_ICONS[slotId % ANIMAL_ICONS.length];
        javafx.scene.image.ImageView avatar = new javafx.scene.image.ImageView();
        try { avatar.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/avatars/" + iconFilename))); } catch (Exception ignored) {}
        avatar.setFitWidth(64); avatar.setFitHeight(64);
        avatar.setClip(new javafx.scene.shape.Circle(32, 32, 32));

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-text-fill: #e8c87a; -fx-font-size: 16px; -fx-font-weight: bold;");
        Label typeLbl = new Label(type);
        typeLbl.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-text-fill: #8ca898; -fx-font-size: 12px;");

        card.getChildren().addAll(avatar, nameLbl, typeLbl);
        return card;
    }

    private VBox buildEmptyCard() {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(180, 240);
        card.setStyle("-fx-background-color: transparent; -fx-border-color: #2e6644; -fx-border-width: 2; -fx-border-radius: 12; -fx-border-style: dashed;");
        Label lbl = styledLabel("Empty Slot");
        card.getChildren().add(lbl);
        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getPlayerName() {
        String n = nameInput.getText().trim();
        return n.isEmpty() ? "Player" : n;
    }

    private void appendLog(String msg) { Platform.runLater(() -> logArea.appendText(msg + "\n")); }
    private int parsePort(String text, int fallback) { try { return Integer.parseInt(text.trim()); } catch (Exception e) { return fallback; } }
    private Label styledLabel(String text) { 
        Label l = new Label(text); 
        l.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #8ca898;"); 
        l.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return l; 
    }
    private Button makeButton(String text, String bg, String fg) { 
    	Button btn = new Button(text); 
    	btn.setPrefWidth(200); 
    	btn.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + fg + "; -fx-background-color: " + bg + "; -fx-border-color: " + fg + "44; -fx-border-width: 2; -fx-border-radius: 8; -fx-padding: 8; -fx-cursor: hand;"); 
    	return btn; 
	}
}