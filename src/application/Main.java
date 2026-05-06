package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.GameView;
import ui.MainMenu;

public class Main extends Application{
	private Stage window;
	private Scene mainScene;
	
	@Override
	public void start(Stage primaryStage) {
		this.window = primaryStage;
		window.setTitle("UPLB Babanuki");

		// Show the Main Menu initially
		showMainMenu();

		window.show();
	}
	
	private void showMainMenu() {
		MainMenu menu = new MainMenu(
			// Callback 1: Singleplayer Clicked
			playerName -> startGame(playerName, false), 
			
			// Callback 2: Multiplayer Clicked
			playerName -> {
				System.out.println("Multiplayer coming soon! Starting Singleplayer for now.");
				startGame(playerName, true); 
			}
		);

		// Set the window size (adjust 1000x700 to whatever looks best for you)
		mainScene = new Scene(menu, 1000, 700); 
		window.setScene(mainScene);
	}

	private void startGame(String playerName, boolean isMultiplayer) {
		// Create the game, passing in the custom name!
		GameView game = new GameView(playerName, () -> showMainMenu());
		
		// Swap the root of the scene to the game layout
		mainScene.setRoot(game);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
