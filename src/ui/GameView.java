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
import java.util.List;

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
			"-fx-font-size: 20px;" +
			"-fx-font-weight: bold;" +
			"-fx-text-fill: #e8c87a;"
		);
		
		Button newGameBtn = makeButton("New Game", "#2e6644", "#e8c87a");
		newGameBtn.setOnAction(e -> startNewGame());
		
		// add spacer between title text and button
		Region headerSpacer = new Region();
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		
		HBox header = new HBox(16, titleLabel, headerSpacer, newGameBtn);
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
		
		//combine layers
		this.getChildren().addAll(tableLayer, hudLayer, overlayPane);
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
		
		// clear old plates before starting
		playerPlates.clear();
		
		//randomize avatar icons
		java.util.List<String> availableIcons = new java.util.ArrayList<>(java.util.Arrays.asList(ANIMAL_ICONS));
		java.util.Collections.shuffle(availableIcons);
		
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
					if (player.canDraw()) {
						gameLoop.submitHumanDraw(view.getPlayer(), cardIndex);
					}
					/*
					Player target = player.getNextDrawTarget();
					if (target != null && target == view.getPlayer()) {
						gameLoop.submitHumanDraw(cardIndex);
					}
					 */
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

	private void showTargetDialog(String prompt, List<Player> options, java.util.function.Consumer<Player> onChosen) {
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
        	boolean isLoser = (p == loser);
        	boolean isSafe = p.getIsOut();
        	
        	String statusText;
        	String color;

        	if (isLoser) {
        		statusText = "holds the Queen";
        		color = "#e88888";
        	} else if (isSafe) {
        		statusText = "safe";
        		color = "#7ab893";
        	} else {
        		statusText = "not the loser"; //idk ano dapat tawag sa kanila (not the loser but still has cards) (edge case)
        		color = "#7ab893";
        	}
        	
            Label row = new Label(p.getName() + "  -  " + statusText);
            row.setStyle(
                "-fx-font-family: 'DM Mono', monospace;" +
                "-fx-font-size: 12px;" +
                "-fx-text-fill: " + color + ";"
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
    
    // avatar component
    private class PlayerPlate extends HBox {
		private final Player player;
		private final Label countLabel;
		private final ImageView avatarView;
		private final Label statusLabel; // NEW: Replaced the dot with a text label!

		public PlayerPlate(Player player, String imagePath) {
			this.player = player;
			
			//timer
			this.statusLabel = new Label();
			this.statusLabel.setStyle("-fx-font-family: 'DM Mono', monospace; -fx-font-weight: bold; -fx-font-size: 13px;");
			this.statusLabel.setPrefWidth(50); // Fixed width prevents the whole plate from jittering
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

}