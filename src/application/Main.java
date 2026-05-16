package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.GameClient;
import network.GameServer;
import ui.GameView;
import ui.MainMenu;
import ui.MultiplayerGameView;
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
            (playerName, cpuCount) -> startSingleplayer(playerName, cpuCount),
            playerName -> showNetworkLobby(playerName)
        );
        mainScene = new Scene(menu, 1000, 700);
        window.setScene(mainScene);
    }

    private void startSingleplayer(String playerName, int cpuCount) {
        GameView game = new GameView(playerName, cpuCount, this::showMainMenu);
        mainScene.setRoot(game);
    }

    private void showNetworkLobby(String playerName) {
        NetworkLobby lobby = new NetworkLobby(playerName,
            (server, client) -> startMultiplayerView(client, server),
            (client)         -> startMultiplayerView(client, null),
            this::showMainMenu
        );
        mainScene.setRoot(lobby);
    }

    private void startMultiplayerView(GameClient client, GameServer server) {
        MultiplayerGameView mpView = new MultiplayerGameView(client, server, this::showMainMenu);
        mainScene.setRoot(mpView);

        // rewire client callbacks from the lobby stubs to the real game view
        client.setCallbacks(new GameClient.Callbacks() {
            @Override public void onWelcome(int slot)              { /* already set before connect */ }
            @Override public void onPlayerList(String[] names)     { mpView.onPlayerList(names); }
            @Override public void onState(String[] entries)        { mpView.onState(entries); }
            @Override public void onLog(String message)            { mpView.onLog(message); }
            @Override public void onHand(String[] entries)           { mpView.onHand(entries); }
            @Override public void onTrapPrompt(String t, String[] opts) { mpView.onTrapPrompt(t, opts); }
            @Override public void onGameOver(String[] names)       { mpView.onGameOver(names); }
            @Override public void onChat(String s, String txt)     { /* future */ }
            @Override public void onDisconnect(String reason)      {
                javafx.application.Platform.runLater(this::showMainMenu);
            }
            private void showMainMenu() { Main.this.showMainMenu(); }
        });
    }

    public static void main(String[] args) { launch(args); }
}