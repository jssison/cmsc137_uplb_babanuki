package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.GameClient;
import network.GameServer;
import ui.GameView;
import ui.MainMenu;
import ui.NetworkLobby;

public class Main extends Application {
	private Stage window;
	private Scene mainScene;

	@Override
	public void start(Stage primaryStage) {
		this.window = primaryStage;
		window.setTitle("UPLB Babanuki");
		showMainMenu();
		window.show();
	}

	private void showMainMenu() {
		MainMenu menu = new MainMenu(
			// Singleplayer
			playerName -> startSingleplayer(playerName),

			// Multiplayer → go to lobby
			playerName -> showNetworkLobby()
		);

		mainScene = new Scene(menu, 1000, 700);
		window.setScene(mainScene);
	}

	private void startSingleplayer(String playerName) {
		GameView game = new GameView(playerName, this::showMainMenu);
		mainScene.setRoot(game);
	}

	// ── Multiplayer lobby ────────────────────────────────────────────────────

	private void showNetworkLobby() {
		NetworkLobby lobby = new NetworkLobby(
			// onHostReady: server running + host connected as client
			(server, client) -> startMultiplayerView(client, server),

			// onJoinReady: connected to a remote server
			(client) -> startMultiplayerView(client, null),

			// onCancel: back to menu
			this::showMainMenu
		);
		mainScene.setRoot(lobby);
	}

	/**
	 * Launch the multiplayer game view.
	 * @param client  connected GameClient (always non-null)
	 * @param server  non-null only if this machine is the host
	 */
	private void startMultiplayerView(GameClient client, GameServer server) {
		// TODO: replace placeholder with MultiplayerGameView once it's built
		javafx.scene.control.Label placeholder = new javafx.scene.control.Label(
			"Connected as \"" + client.getPlayerName() + "\" (slot " + client.mySlot + ").\n"
			+ "MultiplayerGameView — coming next!"
		);
		placeholder.setStyle(
			"-fx-font-family: 'DM Sans', sans-serif;" +
			"-fx-font-size: 18px;" +
			"-fx-text-fill: #e8c87a;" +
			"-fx-background-color: #122a1e;" +
			"-fx-padding: 40;"
		);
		placeholder.setWrapText(true);
		mainScene.setRoot(new javafx.scene.layout.StackPane(placeholder));
	}

	public static void main(String[] args) {
		launch(args);
	}
}