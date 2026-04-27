package ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

//util imports
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

//class imports
import logic.GameLoop;
import logic.TrapCardHandler;
import model.Deck;
import model.GameState;
import model.Player;


//main UI layout
public class GameView extends BorderPane{
	//game state
	private GameState gameState;
	private GameLoop gameLoop;
	private Thread gameThread;
	
	//ui components
	private final VBox aiHandsArea = new VBox(10);
	private final VBox humanArea = new VBox(8);
	private final EventLog eventLog = new EventLog();
	private final Label turnLabel = new Label("Click a card to draw");
	private final Label titleLabel = new Label("UPLB Babanuki");
	private final StackPane overlayPane = new StackPane();
	
	//hand views
	private final List<PlayerHandView> handViews = new ArrayList<>();
	
	//for singleplayer, this is the human
	private Player player;
	
	//cooldown refresh, redraws status labels
	private Timeline refreshTimeline;
	
	//constructor
	public GameView() {
		buildLayout();
		startNewGame();
	}
	
	//layout
	private void buildLayout() {
		setStyle("-fx-background-color: #122a1e;");
		
		//title bar
		titleLabel.setStyle(
			"-fx-font-family: 'Playfair Display', serif;" +
			"-fx-font-size: 22px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #e8c87a;" +
			"-fx-padding: 12 20 8 20;"
		);
		
		Button newGameBtn = makeButton("New Game", "#2e6644", "#e8c87a");
		newGameBtn.setOnAction(e -> startNewGame());
		
		HBox topBar = new HBox(newGameBtn);
		topBar.setAlignment(Pos.CENTER_RIGHT);
		topBar.setPadding(new Insets(8, 16, 0, 16));
		
		VBox header = new VBox(0, titleLabel, topBar);
		header.setStyle(
			"-fx-background-color: #0d1f16;" +
			"-fx-border-color: #2e6644;" +
			"-fx-border-width: 0 0 1 0;"
		);
		setTop(header);
		
		aiHandsArea.setPadding(new Insets(12, 16, 8, 16));
		aiHandsArea.setStyle(
			"-fx-background-color: #122a1e;"
		);
		
		//turn label styling
		turnLabel.setStyle(
			"-fx-font-family: 'DM Sans', sans-serif;" +
			"-fx-font-size: 13px;" +
			"-fx-text-fill: #a8d5b5;" +
			"-fx-padding: 6 0 2 0;"
		);
		
		humanArea.setPadding(new Insets(8, 16, 12, 16));
		humanArea.setStyle(
			"-fx-background-color: #0d1f16;" +
			"-fx-border-color: #2e6644;" +
			"-fx-border-width: 1 0 0 0;"
		);
		humanArea.getChildren().add(turnLabel);
		
		//log
		VBox logBox = new VBox(4, makeSmallLabel("Game Log"), eventLog);
		logBox.setPadding(new Insets(8, 16, 8, 16));
		logBox.setStyle("-fx-background-color: #0d1f16;");
		
		//center
		VBox center = new VBox(0, aiHandsArea, logBox);
		VBox.setVgrow(logBox, Priority.ALWAYS);
		
		setCenter(center);
		setBottom(humanArea);
		
		//overlay (game over screen and trap card dialogs -> choosing target)
		overlayPane.setVisible(false);
		overlayPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.72);");
		StackPane.setAlignment(overlayPane, Pos.CENTER);
	}
	
	//game setup
	public void startNewGame() {
		//stop any running game
		if (gameLoop != null) { gameLoop.stop(); }
		if (gameThread != null) { gameThread.interrupt(); }
		if (refreshTimeline != null) { refreshTimeline.stop(); }
		
		handViews.clear();
		aiHandsArea.getChildren().clear();
		humanArea.getChildren().clear();
		humanArea.getChildren().add(turnLabel);
		eventLog.clear();
		hideOverlay();
		
		//build players
		player = new Player("You", true);
		List<Player> players = new ArrayList<>();
		players.add(player);
		
		//bots are hardcoded for now
		players.add(new Player("CPU 1", false));
		players.add(new Player("CPU 2", false));
		players.add(new Player("CPU 3", false));
		
		//deal cards
		Deck deck = new Deck();
		deck.shuffle();
		deck.dealTo(players);
		
		//build game state
		gameState = new GameState(players);
		
		//listeners
		gameState.addLogListener(eventLog::addEntry);
		gameState.addStateChangeListener(this::refreshUI);
		
		for (Player p : players) {
			List<model.Card> discarded = p.discardPairs();
			if (!discarded.isEmpty()) {
				gameState.log(p.getName() + " discarded " + (discarded.size() / 2) + " pair(s)");
			}
		}

		for (Player p: players) {
			gameState.log(p.getName() + " starts with " + p.handSize() + " cards");
		}
		
		//build hand views
		for (Player p : players) {
			//only reveal hand view to human player
			boolean reveal = p.getIsHuman();
			PlayerHandView view = new PlayerHandView(p, reveal);
			handViews.add(view);
			
			if (p.getIsHuman()) {
				humanArea.getChildren().add(view);
			} else {
				aiHandsArea.getChildren().add(view);
			}
		}
		
		//wire human draw clicks
		wireHumanDrawClicks();
		
		//build game loop
		gameLoop = new GameLoop(gameState, buildTargetChooser());
		
		refreshTimeline = new Timeline(
			new KeyFrame(Duration.millis(500), e -> Platform.runLater(this::refreshAllHands))
		); 
		refreshTimeline.setCycleCount(Animation.INDEFINITE);
		refreshTimeline.play();
		
		//start game loop
		gameThread = new Thread(gameLoop, "GameLoop");
		gameThread.setDaemon(true);
		gameThread.start();
		
		gameState.log("NEW GAME STARTED");
	}
	
	//human input wiring
	private void wireHumanDrawClicks() {
		for (PlayerHandView view : handViews) {
			if (!view.getPlayer().getIsHuman()) {
				//if player is not human
				view.setOnCardClicked(cardIndex -> {
					Player target = player.getNextDrawTarget();
					if (target != null && target == view.getPlayer()) {
						gameLoop.submitHumanDraw(cardIndex);
					}
				});
			}
		}
	}
	
	//ui refresh
	private void refreshUI() {
		Platform.runLater(() -> {
			refreshAllHands();
			updateTurnLabel();
			
			if (gameState.isFinished()) {
				refreshTimeline.stop();
				showGameOver();
			}
		});
	}
	
	private void refreshAllHands() {
		Player target = player.getIsOut() ? null : player.getNextDrawTarget();
		
		for (PlayerHandView view : handViews) {
			boolean isTarget = !view.getPlayer().getIsHuman() && view.getPlayer() == target && !player.getIsOut();
			view.refresh(isTarget);
		}
	}
	
	private void updateTurnLabel() {
		if (player.getIsOut()) {
			turnLabel.setText("Watching the rest of the game play out");
			turnLabel.setStyle(turnLabel.getStyle() + "-fx-text-fill: #4dc880;");
			return;
		}
		
		Player target = player.getNextDrawTarget();
		if (target == null) {
			turnLabel.setText("No valid targets remaining");
		} else {
			turnLabel.setText("Draw from " + target.getName() + ", click one of their cards (" + target.handSize() + " cards)");
		}
	}
	
	//trap card target chooser
	private TrapCardHandler.TargetChooser buildTargetChooser() {
		return (prompt, options) -> {
			//only call chooser if player is human
			if (options.isEmpty()) { return null; }
			
			CountDownLatch latch = new CountDownLatch(1);
			AtomicReference<Player> chosen = new AtomicReference<>(options.get(0));
			
			Platform.runLater(() -> showTargetDialog(prompt, options, chosen, latch));
			
			try {
				latch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			
			return chosen.get();
		};
	}
	
	private void showTargetDialog(String prompt, List<Player> options, AtomicReference<Player> result, CountDownLatch latch) {
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
		
		Label promptLabel = new Label(prompt);
		promptLabel.setWrapText(true);
		promptLabel.setAlignment(Pos.CENTER);
		promptLabel.setStyle(
			"-fx-font-family: 'Playfair Display', serif;" +
			"-fx-font-size: 15px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #e8c87a;" +
			"-fx-text-alignment: center;"
		);
		
		dialog.getChildren().add(promptLabel);
		
		//player options to target for trap cards
		for (Player p : options) {
			Button btn = makeButton(
				p.getName() + " (" + p.handSize() + " cards)",
				"#2e5a44", "#e8d8a0"
			);
			btn.setPrefWidth(280);
			btn.setOnAction(e -> {
				result.set(p);
				hideOverlay();
				latch.countDown();
			});
			
			dialog.getChildren().add(btn);
		}
		
		overlayPane.getChildren().setAll(dialog);
		showOverlay();
	}
	
	private void showGameOver() {
		VBox panel = new VBox(16);
		panel.setAlignment(Pos.CENTER);
		panel.setPadding(new Insets(36));
		panel.setMaxWidth(400);
		panel.setStyle(
			"-fx-background-color: #1a3a2a;" + 
			"-fx-border-color: #e8c87a;" +
			"-fx-border-width: 2;" +
			"-fx-border-radius: 14;" +
			"-fx-background-radius: 14;"
		);
		
		Label title = new Label("GAME OVER");
		title.setStyle(
			"-fx-font-family: 'Playfair Display', serif;" +
			"-fx-font-size: 26px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #e8c87a;"
		);
		
		Player winner = gameState.getWinner();
		Player loser = gameState.getLoser();
		
		VBox results = new VBox(8);
		results.setAlignment(Pos.CENTER);
		
		if (winner != null) {
			Label winLabel = new Label(winner.getName() + " won");
			winLabel.setStyle(
				"-fx-font-family: 'DM sans', sans-serif;" +
				"-fx-font-size: 16px;" +
				"-fx-text-fill: #4dc880;" +
				"-fx-font-weight: bold;"
			);
			results.getChildren().add(winLabel);
		}
		
		if (loser != null) {
			Label loseLabel = new Label(loser.getName() + " has the Queen");
            loseLabel.setStyle(
                "-fx-font-family: 'DM Sans', sans-serif;" +
                "-fx-font-size: 15px;" +
                "-fx-text-fill: #e05555;"
            );
            results.getChildren().add(loseLabel);
		}
		
		//for standings
		//show each player's remaining cards
		Rectangle divider = new Rectangle(300, 1, Color.web("#2e6644"));
		 
        VBox standings = new VBox(4);
        standings.setAlignment(Pos.CENTER);
        for (Player p : gameState.getPlayers()) {
            String status = p.getIsOut() ? "SAFE" : "HOLDS Queen " + p.handSize() + " card(s)";
            Label row = new Label(p.getName() + "  -  " + status);
            row.setStyle(
                "-fx-font-family: 'DM Mono', monospace;" +
                "-fx-font-size: 12px;" +
                "-fx-text-fill: " + (p.getIsOut() ? "#7ab893" : "#e88888") + ";"
            );
            standings.getChildren().add(row);
        }
 
        Button playAgain = makeButton("Play Again", "#2e6644", "#e8c87a");
        playAgain.setPrefWidth(200);
        playAgain.setOnAction(e -> startNewGame());
 
        panel.getChildren().addAll(title, results, divider, standings, playAgain);
 
        overlayPane.getChildren().setAll(panel);
        showOverlay();
	}
	
	//overlay helpers
	private void showOverlay() {
		overlayPane.setVisible(true);
		overlayPane.setMouseTransparent(false);
		overlayPane.toFront();
	}
	
	private void hideOverlay() {
		overlayPane.setVisible(false);
		overlayPane.setMouseTransparent(true);
	}
	
	//style helpers
	private Button makeButton(String text, String bg, String fg) {
	    Button btn = new Button(text);
	    String baseStyle =
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
	        "-fx-cursor: hand;";
	    btn.setStyle(baseStyle);
	    btn.setOnMouseEntered(e -> btn.setOpacity(0.75));
	    btn.setOnMouseExited(e -> btn.setOpacity(1.0));
	    return btn;
	}
 
    private Label makeSmallLabel(String text) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-family: 'DM Sans', sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-text-fill: #4a7a5a;" +
            "-fx-padding: 4 0 0 0;"
        );
        return l;
    }
 
    //creates hover tint
    private String lighten(String hex) {
        return hex + "dd";
    }
 
    // overlay getter
    public StackPane getOverlayPane() {
        return overlayPane;
    }

}
