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

		if (revealCards) {
            Button shuffleBtn = new Button("⟳ Shuffle Hand");
            shuffleBtn.setStyle(
                "-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 11px; -fx-font-weight: bold;" +
                "-fx-text-fill: #8ca898; -fx-background-color: transparent;" +
                "-fx-border-color: #2e6644; -fx-border-radius: 4; -fx-cursor: hand;"
            );
            shuffleBtn.setOnMouseEntered(e -> shuffleBtn.setOpacity(0.6));
            shuffleBtn.setOnMouseExited(e -> shuffleBtn.setOpacity(1.0));
            
            shuffleBtn.setOnAction(e -> {
                player.shuffleHand();
                lastHandSnapshot.clear(); // Force the UI to redraw immediately
                refresh(false);
            });
            
            javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(shuffleBtn);
            header.setAlignment(Pos.CENTER_RIGHT);
            getChildren().add(header); 
        }

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

		List<Card>      currentHand  = player.getHand();
		List<Card.Trap> pendingTraps = revealCards ? player.getPendingTrapPairs() : new ArrayList<>();
		boolean handChanged    = !lastHandSnapshot.equals(currentHand);
		boolean targetChanged  = lastTargetStatus != isTarget;
		boolean trapsChanged   = !lastPendingTraps.equals(pendingTraps);

		if (handChanged || targetChanged || trapsChanged) {
			cardRow.getChildren().clear();
            
            // Track which traps have been wired to prevent double-firing if they mash the button
            java.util.Set<Card.Trap> wiredTraps = new java.util.HashSet<>();

			for (int i = 0; i < currentHand.size(); i++) {
				Card card = currentHand.get(i);
                boolean isPlayable = revealCards && card.isTrap() && pendingTraps.contains(card.getTrap());

                // We now route ALL cards through a single, much smarter method!
				cardRow.getChildren().add(createCardButton(card, i, isTarget, isPlayable, wiredTraps));
			}

			lastHandSnapshot = new ArrayList<>(currentHand);
			lastTargetStatus = isTarget;
			lastPendingTraps = new ArrayList<>(pendingTraps);
		}
	}
	
	private Button createCardButton(Card card, int index, boolean isTarget, boolean isPlayable, java.util.Set<Card.Trap> wiredTraps) {
		Button btn = new Button();

		if (revealCards) {
			// human hand — face up, not clickable by self unless it's a playable trap
			btn.setText(card.toString());
            
            if (card.isTrap()) {
                // THE FIX: Dark Mode Trap Card Styling!
                String color = card.getSuit().isRed() ? "#ff5555" : "#fdf6e3";
                String baseStyle = 
                    "-fx-font-family: 'DM Mono', monospace;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-text-fill: " + color + ";" +
                    "-fx-background-color: #121212;" + // Obsidian Black
                    "-fx-border-color: #f0a030;" +
                    "-fx-border-width: 2;" +
                    "-fx-border-radius: 6;" +
                    "-fx-background-radius: 6;" +
                    "-fx-min-width: 44px;" +
                    "-fx-min-height: 60px;";
                    
                if (isPlayable) {
                    // Playable Pair! Make it glow and clickable.
                    btn.setStyle(baseStyle + "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, #f0a030, 10, 0.5, 0, 0);");
                    Card.Trap trap = card.getTrap();
                    
                    btn.setOnMousePressed(e -> {
                        if (onTrapPlayed != null && !wiredTraps.contains(trap)) {
                            wiredTraps.add(trap); // Lock out the other card in the pair
                            onTrapPlayed.accept(trap);
                        }
                    });
                    btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
                    btn.setOnMouseExited(e -> btn.setOpacity(1.0));
                } else {
                    // Single Trap Card. Just Dark Mode, no glow.
                    btn.setStyle(baseStyle + "-fx-cursor: default;");
                }
            } else {
                // Standard Normal Card
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
            }
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