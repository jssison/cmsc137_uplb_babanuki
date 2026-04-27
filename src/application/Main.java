package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

//class import/s
import ui.GameView;

public class Main extends Application{
	@Override
	public void start(Stage primaryStage) {
		GameView gameView = new GameView();
		
		StackPane root = new StackPane(gameView, gameView.getOverlayPane());
		
		//change screen size here
		Scene scene = new Scene(root, 860, 700);
		
		scene.getStylesheets().add(
			"https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,700;1,400" +
			"&family=DM+Mono:wght@400;500&family=DM+Sans:wght@300;400;500&display=swap"
		);
		
		primaryStage.setTitle("UPLB Babanuki");
		primaryStage.setScene(scene);
		primaryStage.setMinWidth(700);
		primaryStage.setMinHeight(540);
		primaryStage.show();
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
