package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

public class MainMenu extends VBox {

	public MainMenu(Consumer<String> onSinglePlayerStart, Consumer<String> onMultiPlayerStart) {
		this.setAlignment(Pos.CENTER);
		this.setSpacing(25);
		this.setStyle("-fx-background-color: #122a1e;");

		// 1. Title
		Label title = new Label("UPLB Babanuki");
		title.setStyle(
			"-fx-font-family: 'Playfair Display', serif;" +
			"-fx-font-size: 54px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #e8c87a;" +
			"-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 10, 0, 0, 5);"
		);

		// 2. Name Input Area
		VBox inputArea = new VBox(10);
		inputArea.setAlignment(Pos.CENTER);
		inputArea.setMaxWidth(300);

		Label nameLabel = new Label("ENTER YOUR NAME:");
		nameLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #8ca898;");

		TextField nameInput = new TextField();
		nameInput.setPromptText("E.g., Oble");
		nameInput.setStyle(
			"-fx-font-family: 'DM Sans', sans-serif;" +
			"-fx-font-size: 16px;" +
			"-fx-background-color: #1a3a2a;" +
			"-fx-text-fill: #fdf6e3;" +
			"-fx-border-color: #2e6644;" +
			"-fx-border-width: 2;" +
			"-fx-border-radius: 6;" +
			"-fx-background-radius: 6;" +
			"-fx-padding: 10;"
		);
		
		inputArea.getChildren().addAll(nameLabel, nameInput);

		// 3. Buttons
		VBox buttonArea = new VBox(15);
		buttonArea.setAlignment(Pos.CENTER);

		Button singlePlayerBtn = makeButton("Start Singleplayer", "#2e6644", "#e8c87a");
		singlePlayerBtn.setPrefWidth(250);
		singlePlayerBtn.setOnAction(e -> {
			String name = nameInput.getText().trim();
			if (name.isEmpty()) name = "Player 1"; // Default name fallback
			onSinglePlayerStart.accept(name);
		});

		// Same func as single player button for now
		Button multiPlayerBtn = makeButton("Start Multiplayer", "#2e6644", "#e8c87a");
		multiPlayerBtn.setPrefWidth(250);
		multiPlayerBtn.setOnAction(e -> {
			String name = nameInput.getText().trim();
			if (name.isEmpty()) name = "Player 1";
			onMultiPlayerStart.accept(name);
		});

		buttonArea.getChildren().addAll(singlePlayerBtn, multiPlayerBtn);

		// Add everything to the screen
		this.getChildren().addAll(title, inputArea, buttonArea);
	}

	// Helper for clean buttons
	private Button makeButton(String text, String bg, String fg) {
		Button btn = new Button(text);
		btn.setStyle(
			"-fx-font-family: 'DM Sans', sans-serif;" +
			"-fx-font-size: 16px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: " + fg + ";" +
			"-fx-background-color: " + bg + ";" +
			"-fx-border-color: " + fg + "44;" +
			"-fx-border-width: 1;" +
			"-fx-border-radius: 6;" +
			"-fx-background-radius: 6;" +
			"-fx-padding: 12 24 12 24;" +
			"-fx-cursor: hand;"
		);
		btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
		btn.setOnMouseExited(e -> btn.setOpacity(1.0));
		return btn;
	}
}