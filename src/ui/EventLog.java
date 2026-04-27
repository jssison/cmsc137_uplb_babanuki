package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

public class EventLog extends ScrollPane {
	private final VBox entries = new VBox(4);
	//max number of entries to display
	private static final int MAX_ENTRIES = 80;
	
	//constructor
	public EventLog() {
		entries.setPadding(new Insets(8));
		setContent(entries);
		setFitToWidth(true);
		setVbarPolicy(ScrollBarPolicy.ALWAYS);
		setPrefHeight(160);
		setStyle("-fx-background: #0d1f16; -fx-background-color: #0d1f16;");
	}
	
	public void addEntry(String message) {
		Platform.runLater(() -> {
			Label label = new Label(message);
			label.setWrapText(true);
			label.setStyle(
				"-fx-font-family: 'DM Mono', monospace;" +
				"-fx-font-size: 12px;" +
				"-fx-text-fill: #a8d5b5;"
			);
			
			//highlight trap card events
			if (message.contains("SINGKO")) {
				label.setStyle(label.getStyle() + "-fx-text-fill: #f0c040;");
			} else if (message.contains("UNO") || message.contains("AMIS")) {
				label.setStyle(label.getStyle() + "-fx-text-fill: #f07040;");
			} else if (message.contains("swapped") || message.contains("skips")) {
				label.setStyle(label.getStyle() + "-fx-text-fill: #d88840;");
			} else if (message.contains("safe") || message.contains("out")) {
				label.setStyle(label.getStyle() + "-fx-text-fill: #60d890;");
			}
			
			entries.getChildren().add(label);
			
			//remove old logs
			while (entries.getChildren().size() > MAX_ENTRIES) {
				entries.getChildren().remove(0);
			}
			
			//auto scroll to bottom
			setVvalue(1.0);
		});
	}
	
	//clear entries
	public void clear() {
		Platform.runLater(() -> entries.getChildren().clear());
	}
}
