package model;

import java.util.ArrayList;
import model.Card;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameState {
	public enum GameStatus { PLAYING, FINISHED }
	
	private final List<Player> players;
	private GameStatus status = GameStatus.PLAYING;
	
	// Won't allow condition checks until game loop starts the game
	private boolean started = false;
	
	// UI stuff (listeners)
	private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
	private final List<Runnable> stateChangeListeners = new CopyOnWriteArrayList<>();

	// Ordered finish list: index 0 = 1st place, last index = Babanuki loser
	private final List<Player> leaderboard = new ArrayList<>();

	// Constructor
	public GameState(List<Player> players) {
		this.players = new ArrayList<>(players);
		setupDrawRotation();
	}
	
	// Starts the game once setup is done
	public void markStarted() {
		this.started = true;
	}
	
	// Clockwise rotation per player
	private void setupDrawRotation() {
		int n = players.size();
		for (int i = 0; i < n; i++) {
			List<Player> rotation = new ArrayList<>();
			for (int j = 1; j < n; j++) {
				rotation.add(players.get((i + j) % n));
			}
			players.get(i).setDrawRotation(rotation);
		}
	}
	
	// Win / lose detection
	public synchronized void checkEndConditions() {
		if (!started) return;
		if (status == GameStatus.FINISHED) return;

		// flush unplayed trap pairs at end — they count as normal discards
		for (Player p : players) {
			if (!p.getIsOut()) {
				List<Card> flushed = p.discardAllPairs();
				if (!flushed.isEmpty())
					log(p.getName() + " auto-discarded " + (flushed.size()/2) + " unplayed trap pair(s)");
			}
		}
		
		// add newly-safe players to leaderboard in iteration order
		for (Player p : players) {
			if (p.getIsOut() && !leaderboard.contains(p)) {
				leaderboard.add(p);
				log(p.getName() + " finished in " + getRankString(leaderboard.size()) + " place!");
			}
		}
		
		List<Player> stillInGame = getActivePlayers();
		
		// Case 1: only one player left — they hold the Queen
		if (stillInGame.size() == 1) {
			Player loser = stillInGame.get(0);
			if (!leaderboard.contains(loser)) {
				leaderboard.add(loser);
				log(loser.getName() + " is holding the Queen (Babanuki!)");
			}
			status = GameStatus.FINISHED;
			return;
		}
		
		// Case 2: everyone is out (safety net)
		if (stillInGame.isEmpty()) {
			status = GameStatus.FINISHED;
			return;
		}
		
		// Case 3: a player has no valid draw targets left
		for (Player p : stillInGame) {
			if (p.getNextDrawTarget() == null) {
				if (!leaderboard.contains(p)) {
					leaderboard.add(p);
					log(p.getName() + " is holding the Queen");
				}
				status = GameStatus.FINISHED;
				return;
			}
		}
	}

	private String getRankString(int rank) {
		return switch (rank) {
			case 1 -> "1st";
			case 2 -> "2nd";
			case 3 -> "3rd";
			default -> rank + "th";
		};
	}
	
	public List<Player> getActivePlayers() {
		List<Player> active = new ArrayList<>();
		for (Player p : players) {
			if (!p.getIsOut() && p.handSize() > 0) active.add(p);
		}
		return active;
	}
	
	public List<Player> getLeaderboard() { return leaderboard; }

	// Listeners
	public void addLogListener(Consumer<String> listener) { logListeners.add(listener); }
	public void addStateChangeListener(Runnable listener) { stateChangeListeners.add(listener); }
	
	public void log(String message) {
		for (Consumer<String> l : logListeners) l.accept(message);
	}
	
	public void notifyStateChanged() {
		for (Runnable r : stateChangeListeners) r.run();
	}
	
	// Getters
	public boolean isFinished()        { return this.status == GameStatus.FINISHED; }
	public List<Player> getPlayers()   { return this.players; }
	public GameStatus getStatus()      { return this.status; }
}