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

    public MainMenu(BiConsumer<String, Integer> onSinglePlayerStart, Consumer<String> onMultiPlayerStart) {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(30);
        this.setStyle("-fx-background-color: #122a1e;");

        // ── Title ─────────────────────────────────────────────────────────────
        Label title = new Label("UPLB Babanuki");
        title.setStyle(
            "-fx-font-family: 'Playfair Display', serif;" +
            "-fx-font-size: 64px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #e8c87a;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 15, 0, 0, 8);"
        );

        Label subtitle = new Label("The Classic Card Game");
        subtitle.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 18px;" +
            "-fx-text-fill: #8ca898;" +
            "-fx-font-style: italic;"
        );
        VBox titleBox = new VBox(5, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);
        VBox.setMargin(titleBox, new Insets(0, 0, 30, 0));

        // ── Inputs ────────────────────────────────────────────────────────────
        VBox inputArea = new VBox(15);
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setMaxWidth(300);

        Label nameLabel = new Label("ENTER YOUR NAME:");
        nameLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-weight: bold; -fx-text-fill: #8ca898; -fx-font-size: 12px;");
        
        TextField nameInput = new TextField();
        nameInput.setPromptText("e.g. Oble");
        nameInput.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 16px; " +
            "-fx-background-color: #0d1f16; -fx-text-fill: #fdf6e3; " +
            "-fx-border-color: #2e6644; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;"
        );

        HBox cpuRow = new HBox(15);
        cpuRow.setAlignment(Pos.CENTER);
        Label cpuLabel = new Label("CPU BOTS:");
        cpuLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-weight: bold; -fx-text-fill: #8ca898; -fx-font-size: 12px;");
        
        Spinner<Integer> cpuSpinner = new Spinner<>(1, 3, 3);
        cpuSpinner.setPrefWidth(80);
        cpuSpinner.setStyle("-fx-background-color: #0d1f16; -fx-base: #0d1f16; -fx-control-inner-background: #0d1f16; -fx-text-fill: #fdf6e3;");

        cpuRow.getChildren().addAll(cpuLabel, cpuSpinner);
        
        VBox nameBox = new VBox(5, nameLabel, nameInput);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        
        inputArea.getChildren().addAll(nameBox, cpuRow);

        // ── Buttons ───────────────────────────────────────────────────────────
        VBox buttonArea = new VBox(15);
        buttonArea.setAlignment(Pos.CENTER);
        VBox.setMargin(buttonArea, new Insets(20, 0, 0, 0));

        Button singlePlayerBtn = makeButton("Start Singleplayer", "#2e6644", "#e8c87a");
        singlePlayerBtn.setOnAction(e -> {
            String name = nameInput.getText().trim();
            if (name.isEmpty()) name = "Player 1";
            onSinglePlayerStart.accept(name, cpuSpinner.getValue());
        });

        Button multiPlayerBtn = makeButton("Multiplayer Lobby", "#1c4d8c", "#e8c87a");
        multiPlayerBtn.setOnAction(e -> {
            String name = nameInput.getText().trim();
            if (name.isEmpty()) name = "Player 1";
            onMultiPlayerStart.accept(name);
        });

        buttonArea.getChildren().addAll(singlePlayerBtn, multiPlayerBtn);

        this.getChildren().addAll(titleBox, inputArea, buttonArea);
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setPrefWidth(260);
        btn.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + fg + "44;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 12 24 12 24;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }
}