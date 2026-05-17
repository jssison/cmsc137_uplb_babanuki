package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import model.Card;
import model.Player;
import network.GameClient;
import network.GameServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

// multiplayer game view — mirrors singleplayer layout as closely as possible.
// the server owns all logic; this view renders state + hand from server messages
// and sends DRAW / TRAP_CHOICE back.
public class MultiplayerGameView extends StackPane {

    private final GameClient client;
    private final GameServer server; // null if joiner
    private final Runnable   onReturnToMenu;

    // layers (same structure as GameView)
    private final BorderPane tableLayer  = new BorderPane();
    private final AnchorPane hudLayer    = new AnchorPane();
    private final Pane       animLayer   = new Pane();
    private final StackPane  overlayPane = new StackPane();
    private AnimationEngine  animEngine;

    // seats
    private final VBox topSeat    = new VBox();
    private final VBox leftSeat   = new VBox();
    private final VBox rightSeat  = new VBox();
    private final VBox bottomArea = new VBox(8);
    private final EventLog eventLog = new EventLog();
    private final Label turnLabel  = new Label("Waiting for game...");

    // per-slot data arrays — resized when PLAYER_LIST arrives
    private String[]   playerNames = new String[0];
    private int[]      handSizes   = new int[0];
    private String[]   drawStates  = new String[0];
    private boolean[]  isOut       = new boolean[0];

    // my hand cards — entries from server: "displayStr:RED|BLACK:TRAP|NONE"
    private String[] myHandEntries = new String[0];

    // hand/plate views indexed by slot
    private final List<MpHandView>  handViews    = new ArrayList<>();
    private final List<MpPlate>     playerPlates = new ArrayList<>();

    private int mySlot = -1;

    private static final String[] ANIMAL_ICONS = {
        "monkey.png","dragon.png","rat.png","rabbit.png",
        "cow.png","pig.png","bear.png","cat.png","dog.png"
    };

    public MultiplayerGameView(GameClient client, GameServer server, Runnable onReturnToMenu) {
        this.client         = client;
        this.server         = server;
        this.onReturnToMenu = onReturnToMenu;
        this.mySlot         = client.mySlot;
        buildLayout();
        
        if (client.cachedPlayerList != null) onPlayerList(client.cachedPlayerList);
        if (client.cachedState != null) onState(client.cachedState);
        if (client.cachedHand != null) onHand(client.cachedHand);
    }

    // ── layout (mirrors GameView.buildLayout) ─────────────────────────────────

    private void buildLayout() {
        setStyle("-fx-background-color: #122a1e;");
        animEngine = new AnimationEngine(animLayer);

        Label titleLabel = new Label("UPLB Babanuki");
        titleLabel.setStyle(
            "-fx-font-family: 'Playfair Display', serif;" +
            "-fx-font-size: 20px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #e8c87a;"
        );

        Button disconnectBtn = makeButton("Disconnect", "#2e6644", "#e8c87a");
        disconnectBtn.setOnAction(e -> {
            // UI FRICTION: Ask for confirmation before dropping the socket
            showConfirmDialog("Are you sure you want to disconnect and abandon your hand?", () -> {
                client.disconnect();
                if (server != null) server.stop();
                onReturnToMenu.run();
            });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, titleLabel, spacer, disconnectBtn);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(10, 20, 10, 20));
        header.setStyle(
            "-fx-background-color: #0d1f16;" +
            "-fx-border-color: #2e6644;" +
            "-fx-border-width: 0 0 1 0;"
        );

        topSeat.setAlignment(Pos.CENTER);
        topSeat.setPadding(new Insets(4));
        leftSeat.setAlignment(Pos.CENTER);
        leftSeat.setPadding(new Insets(16));
        rightSeat.setAlignment(Pos.CENTER);
        rightSeat.setPadding(new Insets(16));

        turnLabel.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 13px;" +
            "-fx-text-fill: #e8c87a;"
        );
        bottomArea.setPadding(new Insets(4, 16, 8, 16));
        bottomArea.setAlignment(Pos.CENTER);
        bottomArea.setStyle("-fx-background-color: #0d1f16; -fx-border-color: #2e6644; -fx-border-width: 1 0 0 0;");
        bottomArea.getChildren().add(turnLabel);

        VBox logBox = new VBox(2, eventLog);
        logBox.setPadding(new Insets(4, 12, 4, 12));
        logBox.setMaxWidth(450);
        logBox.setMaxHeight(60);
        logBox.setStyle("-fx-background-color: #1a3a2add; -fx-border-color: #2e6644; -fx-border-radius: 8; -fx-background-radius: 8;");

        tableLayer.setPadding(new Insets(55, 0, 0, 0));
        tableLayer.setTop(topSeat);
        tableLayer.setLeft(leftSeat);
        tableLayer.setRight(rightSeat);
        tableLayer.setBottom(bottomArea);
        BorderPane.setAlignment(logBox, Pos.BOTTOM_CENTER);
        BorderPane.setMargin(logBox, new Insets(0, 0, 10, 0));
        tableLayer.setCenter(logBox);

        hudLayer.setPickOnBounds(false);
        AnchorPane.setTopAnchor(header, 0.0);
        AnchorPane.setLeftAnchor(header, 0.0);
        AnchorPane.setRightAnchor(header, 0.0);
        hudLayer.getChildren().add(header);

        overlayPane.setVisible(false);
        overlayPane.setStyle("-fx-background-color: rgba(0,0,0,0.72);");
        animLayer.setMouseTransparent(true);

        getChildren().addAll(tableLayer, hudLayer, animLayer, overlayPane);
    }

    // ── callbacks from Main (called after setCallbacks) ───────────────────────

    public void onPlayerList(String[] names) {
    	Platform.runLater(() -> {
            this.mySlot = client.mySlot; 
            playerNames = names;
            handSizes   = new int[names.length];
            drawStates  = new String[names.length];
            isOut       = new boolean[names.length];
            Arrays.fill(drawStates, "READY");
            rebuildSeats();
        });
    }

    // STATE entry format: "name:handSize:drawState[:OUT]"
    public void onState(String[] entries) {
        Platform.runLater(() -> {
            for (int i = 0; i < entries.length && i < playerNames.length; i++) {
                String[] p = entries[i].split(":", -1);
                if (p.length >= 3) {
                    handSizes[i]  = parseInt(p[1]);
                    drawStates[i] = p[2];
                    isOut[i]      = p.length >= 4 && p[3].equals("OUT");
                }
            }
            refreshPlates();
            refreshMyHand();
            updateTurnLabel();
        });
    }

    // HAND entry format: "displayStr:RED|BLACK:TRAP|NONE"
    public void onHand(String[] cardEntries) {
        Platform.runLater(() -> {
            myHandEntries = cardEntries;
            refreshMyHand();
        });
    }

    public void onLog(String message) {
        Platform.runLater(() -> eventLog.addEntry(message));
    }

    public void onTrapPrompt(String trapName, String[] targetNames) {
        Platform.runLater(() -> showTrapDialog(trapName, targetNames));
    }

    public void onGameOver(String[] names) {
        Platform.runLater(() -> showGameOver(names));
    }

    // ── seat construction ─────────────────────────────────────────────────────

    private void rebuildSeats() {
        topSeat.getChildren().clear();
        leftSeat.getChildren().clear();
        rightSeat.getChildren().clear();
        bottomArea.getChildren().clear();
        bottomArea.getChildren().add(turnLabel);
        handViews.clear();
        playerPlates.clear();

        List<String> icons = new ArrayList<>(Arrays.asList(ANIMAL_ICONS));
        Collections.shuffle(icons);

        for (int i = 0; i < playerNames.length; i++) {
            String icon = "/assets/avatars/" + icons.remove(0);
            boolean isMe = i == mySlot;

            // build a lightweight proxy Player so we can reuse MpHandView / MpPlate
            MpPlate   plate = new MpPlate(i, icon);
            MpHandView view = new MpHandView(i, !isMe); // reveal=false for opponents
            handViews.add(view);
            playerPlates.add(plate);

            if (isMe) {
                bottomArea.getChildren().add(0, view);
                bottomArea.getChildren().add(1, plate);
            } else {
                int relative = (i - mySlot + playerNames.length) % playerNames.length;
                switch (relative) {
                    case 1 -> {
                        view.setRotate(90);
                        view.setMaxWidth(300);
                        leftSeat.setSpacing(12);
                        leftSeat.getChildren().addAll(plate, new Group(view));
                    }
                    case 2 -> {
                        topSeat.setSpacing(12);
                        topSeat.getChildren().addAll(plate, new Group(view));
                    }
                    case 3 -> {
                        view.setRotate(-90);
                        view.setMaxWidth(300);
                        rightSeat.setSpacing(12);
                        rightSeat.getChildren().addAll(plate, new Group(view));
                    }
                    default -> topSeat.getChildren().addAll(plate, new Group(view));
                }
            }
        }

        wireDrawClicks();
        refreshPlates();
    }

    // ── card click wiring ─────────────────────────────────────────────────────

    private void wireDrawClicks() {
    	MpPlate myPlate = (mySlot >= 0 && mySlot < playerPlates.size()) ? playerPlates.get(mySlot) : null;

        for (int i = 0; i < handViews.size(); i++) {
            if (i == mySlot) continue; // can't draw from yourself
            final int targetSlot = i;
            MpHandView view = handViews.get(i);
            view.setOnCardClicked(cardIndex -> {
                if (!isMyTurn()) return;
                if (myPlate != null) {
                    animEngine.animateSteal(view.getCardNode(cardIndex), myPlate, () ->
                        client.sendDraw(targetSlot, cardIndex)
                    );
                } else {
                    client.sendDraw(targetSlot, cardIndex);
                }
            });
        }

        // wire trap play on my hand view
        if (mySlot < handViews.size()) {
            handViews.get(mySlot).setOnTrapPlayed(trapName ->
                client.sendTrapPlay(trapName)
            );
        }
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    private void refreshPlates() {
        for (MpPlate plate : playerPlates) plate.refresh();
    }

    private void refreshMyHand() {
        if (mySlot < 0 || mySlot >= handViews.size()) return;
        MpHandView myView = handViews.get(mySlot);
        myView.setHandEntries(myHandEntries);
        myView.refresh(false); // my hand is never a "target"

        // opponents: show correct card count as hidden cards
        for (int i = 0; i < handViews.size(); i++) {
            if (i == mySlot) continue;
            boolean canTarget = isMyTurn() && !isOut[i] && handSizes[i] > 0;
            handViews.get(i).setCardCount(handSizes[i]);
            handViews.get(i).refresh(canTarget);
        }
    }

    private void updateTurnLabel() {
        if (mySlot >= 0 && mySlot < isOut.length && isOut[mySlot]) {
            turnLabel.setText("You're safe! Watching the rest play out...");
            turnLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 13px; -fx-text-fill: #4dc880;");
            return;
        }
        if (isMyTurn()) {
            turnLabel.setText("Your turn — click any opponent's card to draw.");
            turnLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 13px; -fx-text-fill: #e8c87a;");
        } else {
            turnLabel.setText("Waiting for your cooldown...");
            turnLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 13px; -fx-text-fill: #e05555;");
        }
    }

    private boolean isMyTurn() {
        if (mySlot < 0 || mySlot >= drawStates.length) return false;
        return "READY".equals(drawStates[mySlot]) && !isOut[mySlot];
    }

    // ── trap dialog ───────────────────────────────────────────────────────────

    private void showTrapDialog(String trapName, String[] targetNames) {
        VBox dialog = new VBox(12);
        dialog.setAlignment(Pos.CENTER);
        dialog.setPadding(new Insets(28));
        dialog.setMaxWidth(360);
        dialog.setStyle(
            "-fx-background-color: #1a3a2a;" +
            "-fx-border-color: #e8c87a;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;"
        );
        Label prompt = new Label(trapName + "! Choose a target:");
        prompt.setStyle(
            "-fx-font-family: 'Playfair Display', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #e8c87a;"
        );
        dialog.getChildren().add(prompt);

        // iterate over slot IDs not names
        for (String slotStr : targetNames) { 
            int targetSlot = parseInt(slotStr);

            String name = (targetSlot >= 0 && targetSlot < playerNames.length) ? playerNames[targetSlot] : "Unknown";
            int cards = targetSlot < handSizes.length ? handSizes[targetSlot] : 0;
            
            Button btn = makeButton(name + " (" + cards + " cards)", "#2e5a44", "#e8d8a0");
            btn.setPrefWidth(280);
            
            btn.setOnAction(e -> {
                hideOverlay();
                client.sendTrapChoice(targetSlot); 
            });
            dialog.getChildren().add(btn);
        }

        overlayPane.getChildren().setAll(dialog);
        showOverlay();
    }
    
    private void showConfirmDialog(String message, Runnable onConfirm) {
        VBox dialog = new VBox(15);
        dialog.setAlignment(Pos.CENTER);
        dialog.setPadding(new Insets(28));
        dialog.setMaxWidth(340);
        dialog.setStyle("-fx-background-color: #1a3a2a; -fx-border-color: #e05555; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12;");

        Label prompt = new Label(message);
        prompt.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 15px; -fx-text-fill: #fdf6e3; -fx-wrap-text: true; -fx-alignment: center;");
        
        Button yesBtn = makeButton("Yes, Disconnect", "#cc3333", "#ffffff");
        yesBtn.setOnAction(e -> { hideOverlay(); onConfirm.run(); });
        
        Button noBtn = makeButton("Cancel", "#2e6644", "#e8c87a");
        noBtn.setOnAction(e -> hideOverlay());

        HBox btnBox = new HBox(15, noBtn, yesBtn);
        btnBox.setAlignment(Pos.CENTER);
        dialog.getChildren().addAll(prompt, btnBox);
        
        overlayPane.getChildren().setAll(dialog);
        showOverlay();
    }

    // ── game over ─────────────────────────────────────────────────────────────

    private void showGameOver(String[] names) {
        VBox box = new VBox(15);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(400);
        box.setPadding(new Insets(30, 40, 30, 40));
        box.setStyle(
            "-fx-background-color: #0d1f16;" +
            "-fx-border-color: #e8c87a;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 20, 0, 0, 10);"
        );

        Label title = new Label("GAME OVER");
        title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #e8c87a;");
        box.getChildren().add(title);

        String myName = mySlot >= 0 && mySlot < playerNames.length ? playerNames[mySlot] : "";
        String[] rankLabels = {"1ST","2ND","3RD","4TH"};

        for (int i = 0; i < names.length; i++) {
            boolean loser = i == names.length - 1;
            boolean isMe  = names[i].equals(myName);

            HBox row = new HBox(15);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 20, 10, 20));

            Label rank = new Label(loser ? "LSR" : (i < rankLabels.length ? rankLabels[i] : (i+1)+"TH"));
            rank.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (loser ? "#e05555" : i == 0 ? "#e8c87a" : "#8ca898") + ";");

            Label name = new Label(names[i] + (loser ? " (Babanuki!)" : "") + (isMe ? " ← you" : ""));
            name.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 15px; -fx-text-fill: " + (loser ? "#e05555" : "#fdf6e3") + ";");

            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            row.getChildren().addAll(rank, sp, name);

            String rowStyle = "-fx-background-color: " + (i == 0 ? "#2e6644" : "#1a3a2a") + "; -fx-background-radius: 8;";
            if (isMe) rowStyle += " -fx-border-color: #4a90d9; -fx-border-radius: 8; -fx-border-width: 2;";
            row.setStyle(rowStyle);
            box.getChildren().add(row);
        }

        Button menuBtn = makeButton("Back to Menu", "#2e6644", "#e8c87a");
        menuBtn.setOnAction(e -> {
            client.disconnect();
            if (server != null) server.stop();
            onReturnToMenu.run();
        });
        box.getChildren().add(menuBtn);

        overlayPane.getChildren().setAll(box);
        showOverlay();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void showOverlay() {
        overlayPane.setVisible(true);
        overlayPane.setMouseTransparent(false);
        overlayPane.toFront();
    }

    private void hideOverlay() {
        overlayPane.setVisible(false);
        overlayPane.setMouseTransparent(true);
    }

    private int findSlotByName(String name) {
        for (int i = 0; i < playerNames.length; i++)
            if (playerNames[i].equals(name)) return i;
        return 0;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + fg + "44;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 16 6 16;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.75));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }

    // ── inner: MpPlate (mirrors PlayerPlate in GameView) ─────────────────────

    class MpPlate extends HBox {
        private final int slot;
        private final Label countLabel  = new Label();
        private final Label statusLabel = new Label();

        MpPlate(int slot, String imagePath) {
            this.slot = slot;
            setSpacing(12);
            setAlignment(Pos.CENTER);
            setPadding(new Insets(6, 16, 6, 16));
            setMaxWidth(Region.USE_PREF_SIZE);
            setStyle(
                "-fx-background-color: #0d1f16;" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: #2e6644;" +
                "-fx-border-radius: 20;"
            );

            ImageView avatar = new ImageView();
            try {
                avatar.setImage(new Image(getClass().getResourceAsStream(imagePath)));
            } catch (Exception ignored) {}
            avatar.setFitWidth(32); avatar.setFitHeight(32);
            avatar.setPreserveRatio(true); avatar.setSmooth(true);
            Circle clip = new Circle(16, 16, 16);
            avatar.setClip(clip);

            String name = slot < playerNames.length ? playerNames[slot] : "?";
            Label nameLabel = new Label(name + (slot == mySlot ? " (you)" : ""));
            nameLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-text-fill: #e8c87a; -fx-font-weight: bold; -fx-font-size: 13px;");

            countLabel.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-text-fill: #8ca898; -fx-font-size: 12px;");
            statusLabel.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-font-weight: bold; -fx-font-size: 13px;");
            statusLabel.setPrefWidth(60);

            getChildren().addAll(avatar, nameLabel, countLabel, statusLabel);
            refresh();
        }

        void refresh() {
            int cards = slot < handSizes.length ? handSizes[slot] : 0;
            countLabel.setText(cards + " cards");

            if (slot < isOut.length && isOut[slot]) {
                statusLabel.setText("SAFE");
                statusLabel.setStyle("-fx-text-fill: #555555;");
                return;
            }
            String state = slot < drawStates.length ? drawStates[slot] : "READY";
            switch (state) {
                case "SKIPPED"  -> { statusLabel.setText("SKIPPED"); statusLabel.setStyle("-fx-text-fill: #e05555;"); }
                case "COOLDOWN" -> { statusLabel.setText("WAIT");    statusLabel.setStyle("-fx-text-fill: #e05555;"); }
                default         -> { statusLabel.setText("READY");   statusLabel.setStyle("-fx-text-fill: #4dc880;"); }
            }
        }
    }

    // ── inner: MpHandView — renders hand for one player slot ──────────────────

    class MpHandView extends VBox {
        private final int     slot;
        private final boolean hideCards; // true for opponents

        private final javafx.scene.layout.FlowPane cardRow = new javafx.scene.layout.FlowPane(6, 6);

        // for my slot: full card entries from server
        private String[] handEntries = new String[0];
        // for opponent slots: just count
        private int cardCount = 0;

        private Consumer<Integer>    onCardClicked;
        private Consumer<String>     onTrapPlayed; // trapName string

        MpHandView(int slot, boolean hideCards) {
            this.slot      = slot;
            this.hideCards = hideCards;

            setMaxWidth(600);
            setSpacing(6);
            setPadding(new Insets(10));
            setStyle(
                "-fx-background-color: #1a3a2a;" +
                "-fx-border-color: #2e6644;" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 8;" +
                "-fx-background-radius: 8;"
            );
            cardRow.setPrefWrapLength(500);
            cardRow.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(cardRow);
            refresh(false);
        }

        void setHandEntries(String[] entries) { this.handEntries = entries != null ? entries : new String[0]; }
        void setCardCount(int count)          { this.cardCount   = count; }

        void refresh(boolean isTarget) {
            cardRow.getChildren().clear();

            if (hideCards) {
                // opponent — show hidden card backs or clickable ?-cards
                for (int i = 0; i < cardCount; i++) {
                    final int idx = i;
                    Button btn = new Button(isTarget ? "?" : "▪");
                    btn.setStyle(
                        "-fx-font-family: 'DM Mono', monospace;" +
                        "-fx-font-size: " + (isTarget ? "18" : "18") + "px;" +
                        "-fx-text-fill: " + (isTarget ? "#e8c87a" : "#3a6e8a") + ";" +
                        "-fx-background-color: " + (isTarget ? "#1c4d8c" : "#1a3a55") + ";" +
                        "-fx-border-color: " + (isTarget ? "#4a90d9" : "#2e5a75") + ";" +
                        "-fx-border-width: " + (isTarget ? "2" : "1") + ";" +
                        "-fx-border-radius: 6; -fx-background-radius: 6;" +
                        "-fx-min-width: 44px; -fx-min-height: 60px;" +
                        "-fx-cursor: " + (isTarget ? "hand" : "default") + ";"
                    );
                    if (isTarget) {
                        btn.setOnMousePressed(e -> { if (onCardClicked != null) onCardClicked.accept(idx); });
                        btn.setOnMouseEntered(e -> btn.setOpacity(0.7));
                        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
                    }
                    cardRow.getChildren().add(btn);
                }
                return;
            }

            // my own hand — show face-up cards, trap pairs get PLAY button
            // collect pending trap types (those appearing as a pair)
            List<String> pendingTraps = new ArrayList<>();
            List<String> displayList = new ArrayList<>(Arrays.asList(handEntries));
            // count trap types
            java.util.Map<String, Integer> trapCounts = new java.util.HashMap<>();
            for (String entry : displayList) {
                String[] p = entry.split(":", -1);
                String trap = p.length >= 3 ? p[2] : "NONE";
                if (!"NONE".equals(trap)) trapCounts.merge(trap, 1, Integer::sum);
            }
            for (var kv : trapCounts.entrySet()) {
                if (kv.getValue() >= 2) pendingTraps.add(kv.getKey());
            }

            for (int i = 0; i < handEntries.length; i++) {
                String[] p = handEntries[i].split(":", -1);
                String display = p.length >= 1 ? p[0] : "?";
                boolean isRed  = p.length >= 2 && "RED".equals(p[1]);
                String  trap   = p.length >= 3 ? p[2] : "NONE";

                if (!"NONE".equals(trap) && pendingTraps.contains(trap)) {
                    // check if play button already added for this trap
                    boolean playAdded = cardRow.getChildren().stream()
                        .anyMatch(n -> trap.equals(n.getUserData()));
                    if (!playAdded) {
                        // play button (first card of pair)
                        Button btn = new Button(display + "\n▶ PLAY");
                        btn.setUserData(trap);
                        btn.setStyle(
                            "-fx-font-family: 'DM Mono', monospace;" +
                            "-fx-font-size: 11px; -fx-font-weight: bold;" +
                            "-fx-text-fill: #122a1e; -fx-background-color: #e8c87a;" +
                            "-fx-border-color: #f0a030; -fx-border-width: 2;" +
                            "-fx-border-radius: 6; -fx-background-radius: 6;" +
                            "-fx-min-width: 44px; -fx-min-height: 60px; -fx-cursor: hand;" +
                            "-fx-effect: dropshadow(three-pass-box, #f0a03099, 8, 0, 0, 0);"
                        );
                        final String trapCapture = trap;
                        btn.setOnMousePressed(e -> { if (onTrapPlayed != null) onTrapPlayed.accept(trapCapture); });
                        btn.setOnMouseEntered(e -> btn.setOpacity(0.75));
                        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
                        cardRow.getChildren().add(btn);
                    } else {
                        // second card of pair — faded
                        Button btn = new Button(display);
                        btn.setStyle(
                            "-fx-font-family: 'DM Mono', monospace;" +
                            "-fx-font-size: 13px; -fx-font-weight: bold;" +
                            "-fx-text-fill: " + (isRed ? "#cc3333" : "#1a1a2e") + ";" +
                            "-fx-background-color: #fdf6e3; -fx-border-color: #f0a030;" +
                            "-fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;" +
                            "-fx-min-width: 44px; -fx-min-height: 60px; -fx-opacity: 0.5; -fx-cursor: default;"
                        );
                        cardRow.getChildren().add(btn);
                    }
                } else {
                    // normal face-up card
                    Button btn = new Button(display);
                    btn.setStyle(
                        "-fx-font-family: 'DM Mono', monospace;" +
                        "-fx-font-size: 13px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + (isRed ? "#cc3333" : "#1a1a2e") + ";" +
                        "-fx-background-color: #fdf6e3; -fx-border-color: #c8b870;" +
                        "-fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;" +
                        "-fx-min-width: 44px; -fx-min-height: 60px; -fx-cursor: default;"
                    );
                    cardRow.getChildren().add(btn);
                }
            }
        }

        javafx.scene.Node getCardNode(int index) {
            if (index >= 0 && index < cardRow.getChildren().size())
                return cardRow.getChildren().get(index);
            return this;
        }

        void setOnCardClicked(Consumer<Integer> h) { onCardClicked = h; }
        void setOnTrapPlayed(Consumer<String> h)   { onTrapPlayed  = h; }
    }
}