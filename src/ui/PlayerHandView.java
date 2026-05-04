package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

//util imports
import java.util.List;
import java.util.function.Consumer;

//model imports
import model.Card;
import model.Player;

public class PlayerHandView extends VBox {
	private final Player player;
	//true = show card faces, false = show backs
	private final boolean revealCards;
	
	private final FlowPane cardRow = new FlowPane(6,6);
	
	/*infos moved to playerPlate
	private final Label nameLabel = new Label();
	private final Label statusLabel = new Label();
	private final Label countLabel = new Label();
	*/
	
	private Consumer<Integer> onCardClicked;
	
	private java.util.List<Card> lastHandSnapshot = new java.util.ArrayList<>();
	private boolean lastTargetStatus = false;
	
	//constructor
	public PlayerHandView(Player player, boolean revealCards) {
		this.player = player;
		this.revealCards = revealCards;
		
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
		
		/* 
		//name label styling
		nameLabel.setStyle( ... );
		//status label styling
		statusLabel.setStyle( ... );
		//count label styling
		countLabel.setStyle( ... );
		
		HBox header = new HBox(8, nameLabel, countLabel, statusLabel);
		header.setAlignment(Pos.CENTER_LEFT);
		*/
		
		cardRow.setPrefWrapLength(500);
		cardRow.setAlignment(Pos.CENTER_LEFT);
		
		getChildren().addAll(cardRow);
		refresh(false);
	}
	
	//refresh card display
	//isTarget = someone is about to draw from this player
	public void refresh(boolean isTarget) {
		/* TEXT REFRESH COMMENTED OUT
		nameLabel.setText(player.getName());
		countLabel.setText("(" + player.handSize() + " cards)");
		*/
		
		if (player.getIsOut()) {
			/*
			statusLabel.setText("SAFE");
			statusLabel.setStyle(statusLabel.getStyle().replace("#7ab893", "#4dc880"));
			*/
			setStyle(getStyle() + "-fx-opacity: 0.5;");
			cardRow.getChildren().clear(); // Safely clear cards
			return;
		}
		
		/* timer logic moved to player plate
		switch (player.getDrawState()) {
			case SKIPPED -> statusLabel.setText("SKIPPED");
			case COOLDOWN -> statusLabel.setText(
						"COOLDOWN " + (player.getRemainingCooldown() / 1000 + 1) + "s"
					);
			default -> statusLabel.setText(isTarget ? "Draw here": "");
		}
		*/
		
		// rebuild cards if they changed
		List<Card> currentHand = player.getHand();
		boolean handChanged = !lastHandSnapshot.equals(currentHand);
		boolean targetChanged = (lastTargetStatus != isTarget);
		
		if (handChanged || targetChanged) {
			cardRow.getChildren().clear();
			
			for (int i = 0; i < currentHand.size(); i++) {
				Button btn = createCardButton(currentHand.get(i), i, isTarget);
				cardRow.getChildren().add(btn);
			}
			
			//save the current state so it doesn't rebuild next time
			lastHandSnapshot = new java.util.ArrayList<>(currentHand);
			lastTargetStatus = isTarget;
		}
	}
	
	private Button createCardButton(Card card, int index, boolean isTarget) {
		Button btn = new Button();
		
		if (revealCards) {
			//player hand
			btn.setText(card.toString());
			String color = card.getSuit().isRed() ? "#cc3333" : "#1a1a2e"; 
			btn.setStyle(
				"-fx-font-family: 'DM Mono', monospace;" +
				"-fx-font-size: 13px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + color + ";" +
				"-fx-background-color: #fdf6e3;" +
				"-fx-border-color: #c8b870;" +
				"-fx-border-width: 1;" +
				"-fx-border-radius: 6;" +
				"-fx-background-radius: 6;" +
				"-fx-min-width: 44px;" +
				"-fx-min-height: 60px;" +
				"-fx-cursor: default;"
			);
		} else if (isTarget) {
			btn.setText("?");
			final int capturedIndex = index;
			btn.setStyle(
				"-fx-font-family: 'DM Mono', monospace;" +
				"-fx-font-size: 18px;" +
				"-fx-text-fill: #e8c87a;" +
				"-fx-background-color: #1c4d8c;" +
				"-fx-border-color: #4a90d9;" +
				"-fx-border-width: 2;" +
				"-fx-border-radius: 6;" +
				"-fx-background-radius: 6;" +
				"-fx-min-width: 44px;" +
				"-fx-min-height: 60px;" +
				"-fx-cursor: hand;"
			);
			//fires immediately on press
			btn.setOnMousePressed(e -> {
				if (onCardClicked != null) onCardClicked.accept(capturedIndex);
			});
			btn.setOnMouseEntered(e -> btn.setOpacity(0.7));
			btn.setOnMouseExited(e -> btn.setOpacity(1.0));
		} else {
			//unclickable
			btn.setText("▪");
			btn.setStyle(
				"-fx-font-size: 18px;" +
				"-fx-text-fill: #3a6e8a;" +
				"-fx-background-color: #1a3a55;" +
				"-fx-border-color: #2e5a75;" +
				"-fx-border-width: 1;" +
				"-fx-border-radius: 6;" +
				"-fx-background-radius: 6;" +
				"-fx-min-width: 44px;" +
				"-fx-min-height: 60px;" +
				"-fx-cursor: default;"
			);
		}
		
		return btn;
	}
	
	public void setOnCardClicked(Consumer<Integer> handler) {
		this.onCardClicked = handler;
	}
	
	public Player getPlayer() {
		return player;
	}
}