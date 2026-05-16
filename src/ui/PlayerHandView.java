package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import model.Card;
import model.Player;

public class PlayerHandView extends VBox {
	private final Player player;
	private final boolean revealCards;

	private final FlowPane cardRow = new FlowPane(6, 6);

	private Consumer<Integer>  onCardClicked;
	private Consumer<Card.Trap> onTrapPlayed; // fired when human hits "play" on a trap pair

	private List<Card>      lastHandSnapshot  = new ArrayList<>();
	private boolean         lastTargetStatus  = false;
	private List<Card.Trap> lastPendingTraps  = new ArrayList<>();

	public PlayerHandView(Player player, boolean revealCards) {
		this.player      = player;
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

		cardRow.setPrefWrapLength(500);
		cardRow.setAlignment(Pos.CENTER_LEFT);
		getChildren().add(cardRow);
		refresh(false);
	}

	public void refresh(boolean isTarget) {
		if (player.getIsOut()) {
			setStyle(getStyle() + "-fx-opacity: 0.5;");
			cardRow.getChildren().clear();
			return;
		}

		List<Card>      currentHand   = player.getHand();
		List<Card.Trap> pendingTraps  = revealCards ? player.getPendingTrapPairs() : new ArrayList<>();
		boolean handChanged    = !lastHandSnapshot.equals(currentHand);
		boolean targetChanged  = lastTargetStatus != isTarget;
		boolean trapsChanged   = !lastPendingTraps.equals(pendingTraps);

		if (handChanged || targetChanged || trapsChanged) {
			cardRow.getChildren().clear();

			for (int i = 0; i < currentHand.size(); i++) {
				Card card = currentHand.get(i);
				// if this card is part of a playable trap pair, show play button instead
				if (revealCards && card.isTrap() && pendingTraps.contains(card.getTrap())) {
					// only add the play button once per trap type (first card of pair)
					boolean alreadyAdded = cardRow.getChildren().stream()
						.anyMatch(n -> n.getUserData() == card.getTrap());
					if (!alreadyAdded) {
						cardRow.getChildren().add(createTrapPlayButton(card, card.getTrap()));
					} else {
						// second card of pair — show faded so player sees both cards exist
						cardRow.getChildren().add(createFadedTrapCard(card));
					}
				} else {
					cardRow.getChildren().add(createCardButton(card, i, isTarget));
				}
			}

			lastHandSnapshot = new ArrayList<>(currentHand);
			lastTargetStatus = isTarget;
			lastPendingTraps = new ArrayList<>(pendingTraps);
		}
	}

	// glowing play button shown on trap pairs in the human hand
	private Button createTrapPlayButton(Card card, Card.Trap trap) {
		Button btn = new Button(card.toString() + "\n▶ PLAY");
		btn.setUserData(trap); // used above to detect duplicates
		btn.setStyle(
			"-fx-font-family: 'DM Mono', monospace;" +
			"-fx-font-size: 11px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #122a1e;" +
			"-fx-background-color: #e8c87a;" +
			"-fx-border-color: #f0a030;" +
			"-fx-border-width: 2;" +
			"-fx-border-radius: 6;" +
			"-fx-background-radius: 6;" +
			"-fx-min-width: 44px;" +
			"-fx-min-height: 60px;" +
			"-fx-cursor: hand;" +
			"-fx-effect: dropshadow(three-pass-box, #f0a03099, 8, 0, 0, 0);"
		);
		btn.setOnMousePressed(e -> {
			if (onTrapPlayed != null) onTrapPlayed.accept(trap);
		});
		btn.setOnMouseEntered(e -> btn.setOpacity(0.75));
		btn.setOnMouseExited(e -> btn.setOpacity(1.0));
		return btn;
	}

	// second card of a trap pair — shown faded, not clickable
	private Button createFadedTrapCard(Card card) {
		String color = card.getSuit().isRed() ? "#cc3333" : "#1a1a2e";
		Button btn = new Button(card.toString());
		btn.setStyle(
			"-fx-font-family: 'DM Mono', monospace;" +
			"-fx-font-size: 13px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: " + color + ";" +
			"-fx-background-color: #fdf6e3;" +
			"-fx-border-color: #f0a030;" +
			"-fx-border-width: 2;" +
			"-fx-border-radius: 6;" +
			"-fx-background-radius: 6;" +
			"-fx-min-width: 44px;" +
			"-fx-min-height: 60px;" +
			"-fx-opacity: 0.5;" +
			"-fx-cursor: default;"
		);
		return btn;
	}

	private Button createCardButton(Card card, int index, boolean isTarget) {
		Button btn = new Button();

		if (revealCards) {
			// human hand — face up, not clickable by self
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
			// opponent hand, human can click to draw
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
			btn.setOnMousePressed(e -> {
				if (onCardClicked != null) onCardClicked.accept(capturedIndex);
			});
			btn.setOnMouseEntered(e -> btn.setOpacity(0.7));
			btn.setOnMouseExited(e -> btn.setOpacity(1.0));
		} else {
			// opponent hand, not a draw target
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

	public void setOnCardClicked(Consumer<Integer> handler)  { this.onCardClicked = handler; }
	public void setOnTrapPlayed(Consumer<Card.Trap> handler) { this.onTrapPlayed  = handler; }

	public Player getPlayer() { return player; }

	public Node getCardNode(int index) {
		if (index >= 0 && index < cardRow.getChildren().size())
			return cardRow.getChildren().get(index);
		return this;
	}
}