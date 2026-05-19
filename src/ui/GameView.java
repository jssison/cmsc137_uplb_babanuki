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

import javafx.scene.shape.Circle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javafx.util.Duration;

//util imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

//class imports
import logic.GameLoop;
import logic.TrapCardHandler;
import model.Deck;
import model.GameState;
import model.Player;

//main UI layout - REFACTORED TO STACKPANE FOR HUD OVERLAY
public class GameView extends StackPane {
	//game state
	private GameState gameState;
	private GameLoop gameLoop;
	private Thread gameThread;
	
	//layer components
	private final BorderPane tableLayer = new BorderPane();
	private final AnchorPane hudLayer = new AnchorPane();
	private final Pane animationLayer = new Pane();
	private AnimationEngine animEngine;
	
	//ui components
	private final VBox topSeat = new VBox();
	private final VBox leftSeat = new VBox();
	private final VBox rightSeat = new VBox();
	private final VBox humanArea = new VBox(8);
	private final EventLog eventLog = new EventLog();
	private final Label turnLabel = new Label("Click a card to draw");
	private final Label titleLabel = new Label("UPLB Babanuki");
	private final StackPane overlayPane = new StackPane();
	
	//hand views
	private final List<PlayerHandView> handViews = new ArrayList<>();
	private final List<PlayerPlate> playerPlates = new ArrayList<>();
	
	//for singleplayer, this is the human
	private Player player;
	
	//cooldown refresh, redraws status labels
	private Timeline refreshTimeline;
	
	//avatar_icons
	private final String[] ANIMAL_ICONS = {
		"monkey.png", "dragon.png", "rat.png", "rabbit.png", 
		"cow.png", "pig.png", "bear.png", "cat.png", "dog.png"
	};
	
	private String humanPlayerName = "You";
	private int cpuCount = 3; // configurable
	private Runnable onReturnToMenu;
	
	//constructor
	public GameView(String playerName, int cpuCount, Runnable onReturnToMenu) {
		this.humanPlayerName = playerName;
		this.cpuCount = cpuCount;
		this.onReturnToMenu = onReturnToMenu;
		buildLayout();
		startNewGame();
	}
	
	//layout
	private void buildLayout() {
		setStyle("-fx-background-color: #122a1e;");
		
		//title bar
		titleLabel.setStyle(
			"-fx-font-family: 'Playfair Display', serif;" +
			"-fx-font-size: 20px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #e8c87a;"
		);
		
		Button restartBtn = makeButton("New Game", "#2e6644", "#e8c87a");
		restartBtn.setOnAction(e -> startNewGame());

		// NEW: Main Menu Button
		Button menuBtn = makeButton("Main Menu", "#2e6644", "#e8c87a");
		menuBtn.setOnAction(e -> {
			cleanup(); // Stop the game!
			onReturnToMenu.run(); // Swap the screen!
		});
		
		Region headerSpacer = new Region();
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		
		// Add BOTH buttons to the header
		HBox header = new HBox(16, titleLabel, headerSpacer, restartBtn, menuBtn);
		header.setAlignment(Pos.CENTER);
		header.setPadding(new Insets(10, 20, 10, 20));
		header.setStyle(
			"-fx-background-color: #0d1f16;" +
			"-fx-border-color: #2e6644;" +
			"-fx-border-width: 0 0 1 0;"
		);

	    // Setup Seats
	    topSeat.setAlignment(Pos.CENTER);
	    topSeat.setPadding(new Insets(4));
	    
	    leftSeat.setAlignment(Pos.CENTER);
	    leftSeat.setPadding(new Insets(16));
	    
	    rightSeat.setAlignment(Pos.CENTER);
	    rightSeat.setPadding(new Insets(16));
	    
	    //bottom seat (Player)
	    humanArea.setPadding(new Insets(4, 16, 8, 16));
	    humanArea.setStyle("-fx-background-color: #0d1f16; -fx-border-color: #2e6644; -fx-border-width: 1 0 0 0;");
	    humanArea.setAlignment(Pos.CENTER);
	    humanArea.getChildren().add(turnLabel);
	    
	    // smaller logbox
	    VBox logBox = new VBox(2, eventLog);
	    logBox.setPadding(new Insets(4, 12, 4, 12));
	    logBox.setMaxWidth(450); 
	    logBox.setMaxHeight(60); // 60px is exactly enough for 1-2 lines of text
	    logBox.setStyle(
	        "-fx-background-color: #1a3a2add;" + //dd for slight transparency
	        "-fx-border-color: #2e6644;" + 
	        "-fx-border-radius: 8;" + 
	        "-fx-background-radius: 8;"
	    );

	    // setup table layer
	    tableLayer.setPadding(new Insets(55, 0, 0, 0)); 
	    tableLayer.setTop(topSeat); 
	    tableLayer.setBottom(humanArea);
	    tableLayer.setLeft(leftSeat);
	    tableLayer.setRight(rightSeat);
	    
	    //place log in bottom center
	    BorderPane.setAlignment(logBox, Pos.BOTTOM_CENTER);
	    BorderPane.setMargin(logBox, new Insets(0, 0, 10, 0));
	    tableLayer.setCenter(logBox);
	    
	    //hud layer setup
	    hudLayer.setPickOnBounds(false); 
	    
	    //pin header to top
	    AnchorPane.setTopAnchor(header, 0.0);
	    AnchorPane.setLeftAnchor(header, 0.0);
	    AnchorPane.setRightAnchor(header, 0.0);
	    
	    hudLayer.getChildren().add(header);
		
		//overlay setup
		overlayPane.setVisible(false);
		overlayPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.72);");
		StackPane.setAlignment(overlayPane, Pos.CENTER);
		
		animationLayer.setMouseTransparent(true);
		
		//combine layers
		this.getChildren().addAll(tableLayer, hudLayer, animationLayer, overlayPane);
	}
	
	//game setup
	public void startNewGame() {
		//stop any running game
		if (gameLoop != null) { gameLoop.stop(); }
		if (gameThread != null) { gameThread.interrupt(); }
		if (refreshTimeline != null) { refreshTimeline.stop(); }
		
		handViews.clear();
		
		topSeat.getChildren().clear();
		leftSeat.getChildren().clear();
		rightSeat.getChildren().clear();
		
		humanArea.getChildren().clear();
		humanArea.getChildren().add(turnLabel);
		eventLog.clear();
		hideOverlay();
		animEngine = new AnimationEngine(animationLayer);
		
		//build players
		player = new Player(humanPlayerName, true);
		List<Player> players = new ArrayList<>();
		players.add(player);
		
		// CPU count is set from MainMenu
		for (int c = 1; c <= cpuCount; c++) {
			players.add(new Player("CPU " + c, false));
		}
		
		//deal cards
		Deck deck = new Deck();
		deck.shuffle();
		deck.dealTo(players);
		
		//build game state
		gameState = new GameState(players);
		
		//listeners
		gameState.addLogListener(eventLog::addEntry);
		gameState.addStateChangeListener(this::refreshUI);
		
		java.util.Map<Player, List<model.Card>> initialDiscards = new java.util.HashMap<>();
		
		for (Player p : players) {
			List<model.Card> discarded = p.discardNonTrapPairs();
			if (!discarded.isEmpty()) {
				gameState.log(p.getName() + " discarded " + (discarded.size() / 2) + " pair(s)");
				initialDiscards.put(p, discarded);
			}
		}

		for (Player p: players) {
			gameState.log(p.getName() + " starts with " + p.handSize() + " cards");
		}
		
		// clear old plates before starting
		playerPlates.clear();
		
		//randomize avatar icons
		List<String> availableIcons = new ArrayList<>(Arrays.asList(ANIMAL_ICONS));
		Collections.shuffle(availableIcons);
		
		// build hand views, plates, and place them in the Round Table seats
		for (int i = 0; i < players.size(); i++) {
			Player p = players.get(i);
			boolean reveal = p.getIsHuman();
			PlayerHandView view = new PlayerHandView(p, reveal);
			handViews.add(view);
			
			//pop the first icon off the shuffled list
			String iconFilename = availableIcons.remove(0);
			String fullImagePath = "/assets/avatars/" + iconFilename;
			
			//create the ui plate
			PlayerPlate plate = new PlayerPlate(p, fullImagePath);
			playerPlates.add(plate);
			
			// seat assignment based on index
			if (p.getIsHuman()) {
				// Human area: Cards point at center, Plate below, Turn label at bottom
				humanArea.getChildren().clear(); 
				humanArea.getChildren().addAll(view, plate, turnLabel); 
			} else if (i == 1) {
				// CPU 1 is Left
				view.setRotate(90);
				view.setMaxWidth(300);
				leftSeat.setSpacing(12);
				leftSeat.getChildren().addAll(plate, new javafx.scene.Group(view));
			} else if (i == 2) {
				// CPU 2 is Top 
				view.setRotate(0);
				topSeat.setSpacing(12);
				topSeat.getChildren().addAll(plate, new javafx.scene.Group(view)); 
			} else if (i == 3) {
				// CPU 3 is Right 
				view.setRotate(-90);
				view.setMaxWidth(300);
				rightSeat.setSpacing(12);
				rightSeat.getChildren().addAll(plate, new javafx.scene.Group(view));
			}
		}
		
		//wire human draw clicks
		wireHumanDrawClicks();
		
		//build game loop
		gameLoop = new GameLoop(gameState, buildTargetChooser(), buildAnimationCallback());
		
		refreshTimeline = new Timeline(
			new KeyFrame(Duration.millis(500), e -> Platform.runLater(this::refreshAllHands))
		); 
		refreshTimeline.setCycleCount(Animation.INDEFINITE);
		refreshTimeline.play();
		
		//start game loop
		gameThread = new Thread(gameLoop, "GameLoop");
		gameThread.setDaemon(true);
		gameThread.start();
		
		javafx.animation.PauseTransition startupPause = new javafx.animation.PauseTransition(Duration.millis(800));
		startupPause.setOnFinished(e -> {
			for (java.util.Map.Entry<Player, List<model.Card>> entry : initialDiscards.entrySet()) {
				Player p = entry.getKey();
				PlayerPlate plate = playerPlates.stream().filter(pl -> pl.player == p).findFirst().orElse(null);
				if (plate != null) {
					for (model.Card c : entry.getValue()) {
						animEngine.animateDiscard(plate, c.toString());
					}
				}
			}
		});
		startupPause.play();
		
		gameState.log("NEW GAME STARTED");
	}
	
	//human input wiring
	private void wireHumanDrawClicks() {
		PlayerPlate humanPlate = playerPlates.stream()
				.filter(p -> p.player.getIsHuman())
				.findFirst()
				.orElse(null);
		
		for (PlayerHandView view : handViews) {
			if (!view.getPlayer().getIsHuman()) {
				view.setOnCardClicked(cardIndex -> {
					if (player.canDraw()) {
						animEngine.animateSteal(view.getCardNode(cardIndex), humanPlate, () -> {
                            gameLoop.submitHumanDraw(player, view.getPlayer(), cardIndex); // Added 'player'
                        });
					}
				});
			} else {
				// wire trap play buttons on the human's own hand
				view.setOnTrapPlayed(trap -> {
                    gameLoop.submitHumanTrapPlay(player, trap); // Added 'player'
                    gameLoop.processHumanTrapPlay(player);
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
		for (PlayerPlate plate : playerPlates) {
			plate.refresh();
		}
		
		//Player target = player.getIsOut() ? null : player.getNextDrawTarget();
		
		boolean humanCanDraw = player.canDraw();
		
		for (PlayerHandView view : handViews) {
			boolean isTarget = !view.getPlayer().getIsHuman() 
			        && !view.getPlayer().getIsOut() 
			        && humanCanDraw;
			/* PREVIOUS LOGIC
			boolean isTarget = !view.getPlayer().getIsHuman() 
                    && view.getPlayer() == target 
                    && !player.getIsOut()
                    && humanCanDraw; 
			 */
                    
			view.refresh(isTarget);
		}
		
		wireHumanDrawClicks();
	}
	
	private void updateTurnLabel() {
		if (player.getIsOut()) {
			turnLabel.setText("Watching the rest of the game play out");
			turnLabel.setStyle(turnLabel.getStyle() + "-fx-text-fill: #4dc880;");
			return;
		}
		
		// Free-for-all logic
		turnLabel.setText("Free-For-All! Click any opponent's card to draw.");
		
		/* PREVIOUS LOGIC
		Player target = player.getNextDrawTarget();
		if (target == null) {
			turnLabel.setText("No valid targets remaining");
		} else {
			turnLabel.setText("Draw from " + target.getName() + ", click one of their cards (" + target.handSize() + " cards)");
		}		 
		 */
	}
	
	//trap card target chooser
	private TrapCardHandler.TargetChooser buildTargetChooser() {
	    return (prompt, options, onChosen) -> {
	    	//only call chooser if player is human
	    	if (options.isEmpty()) return;
	        
	        Platform.runLater(() -> showTargetDialog(prompt, options, onChosen));
	    };
	}
	
	// animation callback for the backend GameLoop
	private GameLoop.AnimationCallback buildAnimationCallback() {
		return new GameLoop.AnimationCallback() {
			@Override
			public void playStealAnimation(Player stealer, Player target, int cardIndex, Runnable onComplete) {
				Platform.runLater(() -> {
					PlayerHandView targetView = handViews.stream().filter(v -> v.getPlayer() == target).findFirst().orElse(null);
					PlayerPlate stealerPlate = playerPlates.stream().filter(p -> p.player == stealer).findFirst().orElse(null);

					if (targetView != null && stealerPlate != null) {
						animEngine.animateSteal(targetView.getCardNode(cardIndex), stealerPlate, onComplete);
					} else {
						if (onComplete != null) onComplete.run(); 
					}
				});
			}

			@Override
			public void playDiscardAnimation(Player player, List<model.Card> discardedCards) {
				Platform.runLater(() -> {
					PlayerPlate sourcePlate = playerPlates.stream().filter(p -> p.player == player).findFirst().orElse(null);
					if (sourcePlate != null) {
						// THE FIX: Loop through EVERY single card and throw it
						for (model.Card c : discardedCards) {
							// NOTE: We use c.toString() here. If your Card class doesn't have 
							// a good toString method, you might need to use c.getRank() + c.getSuit()
							animEngine.animateDiscard(sourcePlate, c.toString());
						}
					}
				});
			}
		};
	}

	private void showTargetDialog(String prompt, List<Player> options, Consumer<Player> onChosen) {
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
	        
	        // When the button is clicked, hide the UI and fire the callback
	        btn.setOnAction(e -> {
	            hideOverlay();
	            onChosen.accept(p); //executes the logic back in TrapCardHandler!
	        });
	        
	        dialog.getChildren().add(btn);
	    }
	    
	    overlayPane.getChildren().setAll(dialog);
	    showOverlay();
	}
	
	private void showGameOver() {
		VBox leaderboardBox = new VBox(15);
		leaderboardBox.setAlignment(Pos.CENTER);
		leaderboardBox.setMaxWidth(400);
		leaderboardBox.setMaxHeight(Region.USE_PREF_SIZE);
		leaderboardBox.setPadding(new Insets(30, 40, 30, 40));
		leaderboardBox.setStyle(
			"-fx-background-color: #0d1f16;" +
			"-fx-border-color: #e8c87a;" + // Gold border for the finale
			"-fx-border-width: 2;" +
			"-fx-border-radius: 12;" +
			"-fx-background-radius: 12;" +
			"-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 20, 0, 0, 10);"
		);
		
		// 2. The Title
		Label title = new Label("GAME OVER");
		title.setStyle("-fx-font-family: 'Playfair Display', serif; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #e8c87a;");
		leaderboardBox.getChildren().add(title);
		
		// 3. Generate the Ranks dynamically from the backend!
		List<Player> ranks = gameState.getLeaderboard();
		
		for (int i = 0; i < ranks.size(); i++) {
			Player p = ranks.get(i);
			
			// Setup the rank row
			HBox row = new HBox(15);
			row.setAlignment(Pos.CENTER_LEFT);
			row.setPadding(new Insets(10, 20, 10, 20));
			row.setStyle("-fx-background-color: #1a3a2a; -fx-background-radius: 8;");
			
			Label rankLabel = new Label();
			rankLabel.setPrefWidth(50);
			rankLabel.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-font-size: 18px; -fx-font-weight: bold;");
			
			Label nameLabel = new Label(p.getName());
			nameLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-font-size: 16px; -fx-text-fill: #fdf6e3;");
			
			// Styling based on placement
			String rowStyle = "-fx-background-color: #1a3a2a; -fx-background-radius: 8;";
			
			// NEW: Highlight the Human Player's row with a blue border!
			if (p.getIsHuman()) {
				rowStyle += " -fx-border-color: #4a90d9; -fx-border-radius: 8; -fx-border-width: 2;";
			}

			if (i == 0) {
				rankLabel.setText("1ST");
				rankLabel.setStyle(rankLabel.getStyle() + "-fx-text-fill: #e8c87a;"); // Gold
				// Overwrite background for 1st place, keep human border if human won
				rowStyle = "-fx-background-color: #2e6644; -fx-background-radius: 8;" + 
				           (p.getIsHuman() ? " -fx-border-color: #4a90d9; -fx-border-width: 3;" : " -fx-border-color: #e8c87a; -fx-border-width: 2;") +
				           " -fx-border-radius: 8;";
			} else if (i == ranks.size() - 1) {
				rankLabel.setText("LSR"); // Loser / Babanuki
				rankLabel.setStyle(rankLabel.getStyle() + "-fx-text-fill: #e05555;"); // Red
				nameLabel.setText(p.getName() + " (Babanuki!)");
				nameLabel.setStyle(nameLabel.getStyle() + "-fx-text-fill: #e05555;");
			} else {
				if (i == 1) {
					rankLabel.setText("2ND");
				} else if (i == 2) {
					rankLabel.setText("3RD");
				} else {
					rankLabel.setText((i + 1) + "TH");
				}
				rankLabel.setStyle(rankLabel.getStyle() + "-fx-text-fill: #8ca898;"); // Silver/Gray
			}
			
			row.setStyle(rowStyle);
			
			// Push name to the right
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			
			row.getChildren().addAll(rankLabel, spacer, nameLabel);
			leaderboardBox.getChildren().add(row);
		}
		
		// 4Action Buttons
		Button playAgainBtn = makeButton("Play Again", "#2e6644", "#e8c87a");
		playAgainBtn.setOnAction(e -> startNewGame());
		
		Button menuBtn = makeButton("Main Menu", "#2e6644", "#e8c87a");
		menuBtn.setOnAction(e -> {
			cleanup();
			onReturnToMenu.run();
		});

		// Put them side-by-side
		HBox buttonBox = new HBox(15, playAgainBtn, menuBtn);
		buttonBox.setAlignment(Pos.CENTER);
		VBox.setMargin(buttonBox, new Insets(15, 0, 0, 0));
		
		leaderboardBox.getChildren().add(buttonBox);
		
		overlayPane.getChildren().setAll(leaderboardBox);
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
 
    // overlay getter
    public StackPane getOverlayPane() {
        return overlayPane;
    }
    
    // avatar component
    private class PlayerPlate extends HBox {
		private final Player player;
		private final Label countLabel;
		private final ImageView avatarView;
		private final Label statusLabel;

		public PlayerPlate(Player player, String imagePath) {
			this.player = player;
			
			//timer
			this.statusLabel = new Label();
			this.statusLabel.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-font-weight: bold; -fx-font-size: 13px;");
			this.statusLabel.setPrefWidth(50); 
			this.statusLabel.setAlignment(Pos.CENTER_RIGHT);

			Image avatarImage = new Image(getClass().getResourceAsStream(imagePath));
			this.avatarView = new ImageView(avatarImage);
			avatarView.setFitWidth(32);
			avatarView.setFitHeight(32);
			avatarView.setPreserveRatio(true);
			avatarView.setSmooth(true);
			
			Circle clip = new Circle(16, 16, 16);
			avatarView.setClip(clip);

			Label nameLabel = new Label(player.getName());
			nameLabel.setStyle("-fx-font-family: 'DM Sans', sans-serif; -fx-text-fill: #e8c87a; -fx-font-weight: bold; -fx-font-size: 13px;");
			
			this.countLabel = new Label();
			this.countLabel.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-text-fill: #8ca898; -fx-font-size: 12px;");

			this.setSpacing(12);
			this.setAlignment(Pos.CENTER);
			this.setPadding(new Insets(6, 16, 6, 16));
			this.setMaxWidth(Region.USE_PREF_SIZE); 
			
			this.setStyle(
				"-fx-background-color: #0d1f16;" +
				"-fx-background-radius: 20;" +
				"-fx-border-color: #2e6644;" +
				"-fx-border-radius: 20;"
			);

			this.getChildren().addAll(avatarView, nameLabel, countLabel, statusLabel);
			refresh(); 
		}

		public void refresh() {
			countLabel.setText(player.handSize() + " cards");

			if (player.getIsOut()) {
				statusLabel.setText("SAFE");
				statusLabel.setStyle("-fx-text-fill: #555555;"); // Gray
				return;
			}
			
			//status label states
			switch (player.getDrawState()) {
				case SKIPPED -> {
					statusLabel.setText("SKIPPED");
					statusLabel.setStyle("-fx-text-fill: #e05555;"); // Red
				}
				case COOLDOWN -> {
					long secondsLeft = player.getRemainingCooldown() / 1000 + 1;
					statusLabel.setText(secondsLeft + "s");
					statusLabel.setStyle("-fx-text-fill: #e05555;"); // Red
				}
				default -> {
					statusLabel.setText("READY");
					statusLabel.setStyle("-fx-text-fill: #4dc880;"); // Green
				}
			}
		}
	}
    
    public void cleanup() {
		if (gameLoop != null) { gameLoop.stop(); }
		if (gameThread != null) { gameThread.interrupt(); }
		if (refreshTimeline != null) { refreshTimeline.stop(); }
	}

}