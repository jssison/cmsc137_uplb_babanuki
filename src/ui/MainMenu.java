package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MainMenu extends VBox {

    private final VBox mainButtons = new VBox(15);
    private final VBox spConfigBox = new VBox(15);

    public MainMenu(BiConsumer<String, Integer> onSinglePlayerStart, Consumer<String> onMultiPlayerStart) {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(30);
        this.setStyle("-fx-background-color: #122a1e;");

        // ── Title ─────────────────────────────────────────────────────────────
        Label title = new Label("UPLB Babanuki");
        title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 64px; -fx-font-weight: bold; -fx-text-fill: #e8c87a; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 15, 0, 0, 8);");
        Label subtitle = new Label("The Classic Card Game");
        subtitle.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 18px; -fx-text-fill: #8ca898; -fx-font-style: italic;");
        VBox titleBox = new VBox(5, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);
        VBox.setMargin(titleBox, new Insets(0, 0, 30, 0));

        // ── Main Buttons ──────────────────────────────────────────────────────
        mainButtons.setAlignment(Pos.CENTER);
        Button singlePlayerBtn = makeButton("Singleplayer", "#2e6644", "#e8c87a");
        singlePlayerBtn.setOnAction(e -> {
            mainButtons.setVisible(false); mainButtons.setManaged(false);
            spConfigBox.setVisible(true);  spConfigBox.setManaged(true);
        });

        Button multiPlayerBtn = makeButton("Multiplayer Lobby", "#1c4d8c", "#e8c87a");
        multiPlayerBtn.setOnAction(e -> onMultiPlayerStart.accept("")); // Pass empty, Lobby handles name now!
        mainButtons.getChildren().addAll(singlePlayerBtn, multiPlayerBtn);

        // ── Singleplayer Config (Hidden by default) ───────────────────────────
        spConfigBox.setAlignment(Pos.CENTER);
        spConfigBox.setVisible(false); spConfigBox.setManaged(false);

        TextField spNameInput = new TextField();
        spNameInput.setPromptText("Enter your name");
        spNameInput.setMaxWidth(260);
        spNameInput.setStyle("-fx-background-color: #0d1f16; -fx-text-fill: #fdf6e3; -fx-border-color: #2e6644; -fx-padding: 10;");

        Spinner<Integer> cpuSpinner = new Spinner<>(1, 3, 3);
        cpuSpinner.setStyle("-fx-base: #0d1f16; -fx-control-inner-background: #0d1f16; -fx-text-fill: #fdf6e3;");
        HBox cpuRow = new HBox(10, new Label("Bots:"), cpuSpinner);
        cpuRow.setAlignment(Pos.CENTER);

        Button startSpBtn = makeButton("Start Game", "#2e6644", "#e8c87a");
        startSpBtn.setOnAction(e -> {
            String n = spNameInput.getText().trim();
            onSinglePlayerStart.accept(n.isEmpty() ? "Player 1" : n, cpuSpinner.getValue());
        });
        
        Button backBtn = makeButton("Back", "#4a2e2e", "#e05555");
        backBtn.setOnAction(e -> {
            spConfigBox.setVisible(false); spConfigBox.setManaged(false);
            mainButtons.setVisible(true);  mainButtons.setManaged(true);
        });
        spConfigBox.getChildren().addAll(spNameInput, cpuRow, startSpBtn, backBtn);

        this.getChildren().addAll(titleBox, mainButtons, spConfigBox);
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setPrefWidth(260);
        btn.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + fg + "; -fx-background-color: " + bg + "; -fx-border-color: " + fg + "44; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12 24 12 24; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }
}