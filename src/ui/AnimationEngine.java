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
}