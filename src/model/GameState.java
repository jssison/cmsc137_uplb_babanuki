package model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameState {
	public enum GameStatus { PLAYING, FINISHED }
	
	private final List<Player> players;
	private GameStatus status = GameStatus.PLAYING;
	
	//first player to have an empty hand
	private Player winner = null;
	//player holding the Queen
	private Player loser = null;
	
	//wont allow condition checks until game loop starts the game
	private boolean started = false;
	
	//UI stuff (listeners)
	private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
	private final List<Runnable> stateChangeListeners = new CopyOnWriteArrayList<>();
	
	//constructor
	public GameState(List<Player> players) {
		this.players = new ArrayList<>(players);
		setupDrawRotation();
	}
	
	//starts the game once setup is done
	public void markStarted() {
		this.started = true;
	}
	
	//Clockwise rotation per player
	private void setupDrawRotation() {
		int n = players.size();
		
		for (int i=0; i < n; i++) {
			List<Player> rotation = new ArrayList<>();
			
			for (int j=1; j < n; j++) {
				rotation.add(players.get((i+j) % n));
			}
			players.get(i).setDrawRotation(rotation);
		}
	}
	
	//win or lose detection
	public synchronized void checkEndConditions() {
		//does nothing unless game has started
		if (!started) { return; }
		
		//stop reevaluating when already finished
		if (status == GameStatus.FINISHED) { return; }
		
		for (Player p : players) {
			if (p.getIsOut() && winner == null) {
				//first player out is winner
				winner = p;
				log(winner.getName() + " is safe");
			}
		}
		
		List<Player> stillInGame = getActivePlayers();
		
		//case 1: only one player still has cards
		if (stillInGame.size() == 1) {
			//set loser to last player in the game
			loser = stillInGame.get(0);
			log(loser.getName() + " is holding the Queen");
			status = GameStatus.FINISHED;
			return;
		}
		
		//case 2: all players are out (safety net)
		if (stillInGame.isEmpty()) {
			status = GameStatus.FINISHED;
			return;
		}
		
		//case 3: only one player has cards and no other active player
		for (Player p : stillInGame) {
			if (p.getNextDrawTarget() == null) {
				loser = p;
				log(loser.getName() + " is holding the Queen");
				status = GameStatus.FINISHED;
				return;
			}
		}
	}
	
	public List<Player> getActivePlayers() {
		List<Player> active = new ArrayList<>();
		
		for (Player p : players) {
			//add player to active players if not out and has cards
			if (!p.getIsOut() && p.handSize() > 0) { active.add(p); }
		}
		
		return active;
	}
	
	//listeners
	public void addLogListener(Consumer<String> listener) {
		logListeners.add(listener);
	}
	
	public void addStateChangeListener(Runnable listener) {
		stateChangeListeners.add(listener);
	}
	
	public void log(String message) {
		for (Consumer<String> l : logListeners) { l.accept(message); }
	}
	
	public void notifyStateChanged() {
		for (Runnable r : stateChangeListeners) { r.run(); }
	}
	
	
	//getters
	public boolean isFinished() { return this.status == GameStatus.FINISHED; }
	public List<Player> getPlayers() { return this.players; }
	public GameStatus getStatus() { return this.status; }
	public Player getWinner() { return this.winner; }
	public Player getLoser() { return this.loser; }
}
