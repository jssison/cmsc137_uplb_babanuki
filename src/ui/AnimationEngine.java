package ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

public class AnimationEngine {
	private final Pane animationLayer;

	public AnimationEngine(Pane animationLayer) {
		this.animationLayer = animationLayer;
	}
	
	//flying animation from other hands to player's hand
	public void animateSteal(Node sourceNode, Node targetNode, Runnable onAnimationFinished) {
		Bounds startScene = sourceNode.localToScene(sourceNode.getBoundsInLocal());
		Bounds endScene = targetNode.localToScene(targetNode.getBoundsInLocal());

		Bounds startLocal = animationLayer.sceneToLocal(startScene);
		Bounds endLocal = animationLayer.sceneToLocal(endScene);

		Button flyingCard = new Button("?"); 
		flyingCard.setPrefSize(44, 60);
		flyingCard.setMinSize(44, 60);
		flyingCard.setStyle(
			"-fx-font-family: 'DM Mono', monospace;" +
			"-fx-font-size: 18px;" +
			"-fx-text-fill: #e8c87a;" +
			"-fx-background-color: #1c4d8c;" +
			"-fx-border-color: #4a90d9;" + // Back to standard blue
			"-fx-border-width: 2;" +
			"-fx-border-radius: 6;" +
			"-fx-background-radius: 6;"
		);

		flyingCard.setLayoutX(startLocal.getMinX());
		flyingCard.setLayoutY(startLocal.getMinY());

		animationLayer.getChildren().add(flyingCard);
		animationLayer.toFront(); 
		flyingCard.toFront();

		double targetCenterX = endLocal.getMinX() + (endLocal.getWidth() / 2) - 22; 
		double targetCenterY = endLocal.getMinY() + (endLocal.getHeight() / 2) - 30; 

		double deltaX = targetCenterX - startLocal.getMinX();
		double deltaY = targetCenterY - startLocal.getMinY();

		TranslateTransition move = new TranslateTransition(Duration.millis(300), flyingCard);
		move.setByX(deltaX);
		move.setByY(deltaY);

		ScaleTransition pop = new ScaleTransition(Duration.millis(200), flyingCard);
		pop.setFromX(1.0); pop.setFromY(1.0);
		pop.setToX(1.3); pop.setToY(1.3); 
		pop.setAutoReverse(true);
		pop.setCycleCount(2);

		ParallelTransition flight = new ParallelTransition(move, pop);
		flight.setOnFinished(e -> {
			animationLayer.getChildren().remove(flyingCard); 
			if (onAnimationFinished != null) onAnimationFinished.run(); 
		});

		flight.play();
	}
	
	public void animateDiscard(Node sourceNode, String cardText) {
		Bounds startScene = sourceNode.localToScene(sourceNode.getBoundsInLocal());
		Bounds startLocal = animationLayer.sceneToLocal(startScene);

		// 1. Put the actual card text on the button!
		Button discardedCard = new Button(cardText); 
		discardedCard.setStyle(
			"-fx-font-family: 'DM Mono', monospace; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a3a2a;" +
			"-fx-background-color: #fdf6e3; -fx-border-color: #8ca898; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;"
		);
		discardedCard.setPrefSize(44, 60);
		discardedCard.setMinSize(44, 60); // Keep it from shrinking

		discardedCard.setLayoutX(startLocal.getMinX());
		discardedCard.setLayoutY(startLocal.getMinY());

		animationLayer.getChildren().add(discardedCard);
		discardedCard.toBack(); 

		double centerX = animationLayer.getWidth() / 2 - 22;
		double centerY = animationLayer.getHeight() / 2 - 30;

		// 2. Wider random scatter so the two cards split apart in the air!
		double randomOffsetX = (Math.random() * 120) - 60;
		double randomOffsetY = (Math.random() * 120) - 60;

		TranslateTransition move = new TranslateTransition(Duration.millis(500), discardedCard);
		move.setByX((centerX + randomOffsetX) - startLocal.getMinX());
		move.setByY((centerY + randomOffsetY) - startLocal.getMinY());

		discardedCard.setRotate(Math.random() * 360);

		FadeTransition fade = new FadeTransition(Duration.millis(300), discardedCard);
		fade.setFromValue(1.0);
		fade.setToValue(0.0);
		fade.setDelay(Duration.millis(400)); 

		ParallelTransition throwCard = new ParallelTransition(move, fade);
		throwCard.setOnFinished(e -> animationLayer.getChildren().remove(discardedCard));
		throwCard.play();
	}
}