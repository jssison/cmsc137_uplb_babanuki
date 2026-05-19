package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import model.Card;

public class Instructions extends StackPane {

    private final Runnable onBack;

    public Instructions(Runnable onBack) {
        this.onBack = onBack;
        buildLayout();
    }

    private void buildLayout() {
        setStyle("-fx-background-color: #122a1e;");

        VBox mainContainer = new VBox(20);
        mainContainer.setAlignment(Pos.TOP_CENTER);
        mainContainer.setPadding(new Insets(40, 20, 40, 20));
        mainContainer.setMaxWidth(800);

        Label title = new Label("HOW TO PLAY");
        title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #e8c87a;");

        VBox contentBox = new VBox(30);
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.setPadding(new Insets(20));
        contentBox.setStyle("-fx-background-color: #0d1f16; -fx-border-color: #2e6644; -fx-border-radius: 12; -fx-background-radius: 12;");

        contentBox.getChildren().addAll(
            buildSectionTitle("THE BASICS"),
            buildText("UPLB Babanuki is a free-for-all take on the classic Babanuki card game. There are no turns! " +
                      "The only goal is to empty your hand by finding pairs from other players. " +
                      "The player ending up with the single un-paired Queen loses!"),
            
            // THE FIX: Added the visual Queen display here!
            buildQueenDisplay(),
            
            buildSectionTitle("THE TRAP CARDS"),
            buildText("Trap cards are dangerous weapons. They do not auto-discard. " +
                      "When you collect a PAIR of identical trap cards, they will glow. Click either card to activate its effect!"),
            
            // THE FIX: Renamed UNO to DOS, and the method now automatically generates two different suits!
            buildTrapExplanation("DOS", Card.Rank.TWO, 
                "Grants you 3 Extra Draws. For your next 3 actions, your cooldown is instantly set to zero. " +
                "Use this to rapidly strip an opponent's hand before they can react!"),
                
            buildTrapExplanation("SINGKO", Card.Rank.FIVE, 
                "Instantly applies a 5-second cooldown to ALL opponents on the board. " +
                "You are free to draw cards without interference while they are locked out."),
                
            buildTrapExplanation("AMIS", Card.Rank.ACE, 
                "Pauses your game and allows you to select a specific target. " +
                "You will completely swap your entire hand with theirs!")
        );

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        Button backBtn = new Button("Back to Menu");
        backBtn.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e8c87a; -fx-background-color: #2e6644; -fx-border-color: #e8c87a44; -fx-border-width: 2; -fx-border-radius: 8; -fx-padding: 10 30; -fx-cursor: hand;");
        backBtn.setOnAction(e -> onBack.run());

        mainContainer.getChildren().addAll(title, scrollPane, backBtn);
        getChildren().add(mainContainer);
    }

    // ── UI Helpers ──────────────────────────────────────────────────────────
    private HBox buildQueenDisplay() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(
            // Shows the 3 Queens actually in the game (no glowing trap effect)
            createVisualCard(new Card(Card.Rank.QUEEN, Card.Suit.SPADES), false),
            createVisualCard(new Card(Card.Rank.QUEEN, Card.Suit.CLUBS), false),
            createVisualCard(new Card(Card.Rank.QUEEN, Card.Suit.DIAMONDS), false)
        );
        return box;
    }

    // THE FIX: Now takes a Rank, and automatically builds a Black and Red card for the pair
    private HBox buildTrapExplanation(String title, Card.Rank trapRank, String description) {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(15));
        row.setStyle("-fx-background-color: #1a3a2a; -fx-border-color: #2e6644; -fx-border-radius: 8; -fx-background-radius: 8;");

        HBox cardPairBox = new HBox(5);
        cardPairBox.getChildren().addAll(
            createVisualCard(new Card(trapRank, Card.Suit.SPADES), true), // Black suit
            createVisualCard(new Card(trapRank, Card.Suit.HEARTS), true)  // Red suit
        );

        VBox textBox = new VBox(8);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #f0a030;");
        Label descLbl = buildText(description);
        
        textBox.getChildren().addAll(titleLbl, descLbl);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        row.getChildren().addAll(cardPairBox, textBox);
        return row;
    }

    private Label buildSectionTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #a8d5b5; -fx-underline: true;");
        return lbl;
    }

    private Label buildText(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 15px; -fx-text-fill: #fdf6e3; -fx-line-spacing: 0.5em;");
        return lbl;
    }

    // Reuses your exact Dark Mode styling so the instructions match the game perfectly
    private Button createVisualCard(Card card, boolean isTrap) {
        Button btn = new Button(card.toString());
        String color = card.getSuit().isRed() ? "#ff5555" : (isTrap ? "#fdf6e3" : "#1a1a2e");
        
        String bg = isTrap ? "#121212" : "#fdf6e3";
        String border = isTrap ? "#f0a030" : "#c8b870";
        String effect = isTrap ? "-fx-effect: dropshadow(three-pass-box, #f0a030, 10, 0.5, 0, 0);" : "";
        
        btn.setStyle(
            "-fx-font-family: 'DM Mono', monospace;" +
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + color + ";" +
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + border + "; -fx-border-width: " + (isTrap ? "2" : "1") + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-min-width: 44px; -fx-min-height: 60px;" + effect
        );
        return btn;
    }
}