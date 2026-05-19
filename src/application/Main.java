package application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.GameClient;
import network.GameServer;
import network.Message;
import ui.GameView;
import ui.Instructions;
import ui.MainMenu;
import ui.MultiplayerGameView;
import ui.NetworkLobby;

public class Main extends Application {
    private Stage window;
    private Scene mainScene;
    private GameClient client;
    private GameServer server;
    private String currentPlayerName;
    private NetworkLobby currentLobby;

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
            playerName -> showNetworkLobby(playerName),
            () -> {
                Instructions instructions = new Instructions(() -> {
                    showMainMenu(); 
                });
                mainScene.setRoot(instructions);
            }
        );
        mainScene = new Scene(menu, 1000, 700);
        window.setScene(mainScene);
    }

    private void startSingleplayer(String playerName, int cpuCount) {
        GameView game = new GameView(playerName, cpuCount, this::showMainMenu);
        mainScene.setRoot(game);
    }

    
    private void showNetworkLobby(String playerName) {
    	currentPlayerName = playerName;
    	
        currentLobby = new NetworkLobby(playerName,
            (server, client) -> startMultiplayerView(client, server),
            (client)         -> startMultiplayerView(client, null),
            this::showMainMenu
        );
        mainScene.setRoot(currentLobby);
    }

    private void returnToLobby(GameClient client, GameServer server) {
        if (server != null) server.resetToLobby();
        else client.send(Message.returnToLobby());
    }
    
    private void startMultiplayerView(GameClient client, GameServer server) {
        MultiplayerGameView mpView = new MultiplayerGameView(client, server, this::showMainMenu, () -> {
        	returnToLobby(client, server);
        });
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
            @Override public void onAnimSteal(int stealerSlot, int targetSlot, int cardIndex) {
                mpView.onAnimSteal(stealerSlot, targetSlot, cardIndex);
            }
            @Override public void onAnimDiscard(int slot, String[] cards) {
                mpView.onAnimDiscard(slot, cards);
            }
            @Override public void onReturnToLobby() {
            	Platform.runLater(() -> {
            		mainScene.setRoot(currentLobby);
            		currentLobby.reEnterWaitingRoom();
            	});
            }
            private void showMainMenu() { Main.this.showMainMenu(); }
        });
    }

    public static void main(String[] args) { launch(args); }
}