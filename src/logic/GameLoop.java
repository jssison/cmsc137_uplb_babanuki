package logic;

import javafx.application.Platform;
import model.Card;
import model.GameState;
import model.Player;

import java.util.List;

public class GameLoop implements Runnable {
	//cooldowns (human has no cooldown)
	private static final long AI_COOLDOWN_MS = 4000;
	private static final long HUMAN_COOLDOWN_MS = 0;
	private static final long TICK_MS = 100;
	
	private final GameState state;
	private volatile boolean isRunning = true;
	
	//human's pending draw action (consumed)
	private volatile PendingHumanDraw pendingHumanDraw = null;
	
	private TrapCardHandler.TargetChooser targetChooser;
	
	//constructor
	public GameLoop(GameState state, TrapCardHandler.TargetChooser targetChooser) {
		this.state = state;
		this.targetChooser = targetChooser;
	}
	
	@Override
	public void run() {
		//mark game start
		state.markStarted();
		
		while (isRunning && !state.isFinished()) {
			tick();
			try {
				Thread.sleep(TICK_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}
	
	private void tick() {
		for (Player player : state.getPlayers()) {
			if (player.getIsOut()) { continue; }
			
			player.refreshState();
			
			if (!player.canDraw()) {
				//check if player is skipped
				if (player.getDrawState() == Player.DrawState.SKIPPED) {
					player.consumeSkip();
					player.startCooldown(player.getIsHuman() ? HUMAN_COOLDOWN_MS : AI_COOLDOWN_MS);
					state.log(player.getName() + " was skipped.");
					Platform.runLater(state::notifyStateChanged);
				}
				//else continue
				continue;
			}
			
			if (player.getIsHuman()) {
				processHumanTick(player);
			} else {
				processAITick(player);
			}
		}
		
		//check for win/lose
		Platform.runLater(() -> {
			state.checkEndConditions();
			state.notifyStateChanged();
		});
	}
	
	//human tick
	private void processHumanTick(Player human) {
		PendingHumanDraw pending = pendingHumanDraw;
		if (pending == null) { return; }
		
		pendingHumanDraw = null; //consume draw
		
		Player target = human.getNextDrawTarget();
		if (target == null || target.handSize() == 0) {
			state.log("No valid target to draw from");
			return;
		}
		
		//validate the card index picked by human player
		int index = pending.cardIndex();
		if (index < 0 || index >= target.handSize()) {
			index = 0;
		}
		
		performDraw(human, target, index);
		human.startCooldown(HUMAN_COOLDOWN_MS);
	}
	
	//for UI
	public void submitHumanDraw(int cardIndex) {
		pendingHumanDraw = new PendingHumanDraw(cardIndex);
	}
	
	//AI tick
	private void processAITick(Player ai) {
		Player target = ai.getNextDrawTarget();
		if (target == null || target.handSize() == 0) { return; }
		
		int index = (int)(Math.random() * target.handSize());
		performDraw(ai, target, index);
		
		long variance = (long)(Math.random() * 1000);
		ai.startCooldown(AI_COOLDOWN_MS + variance);
	}
	
	//draw logic
	private void performDraw(Player drawer, Player target, int cardIndex) {
		Card drawn = target.takeCard(cardIndex);
		drawer.addCard(drawn);
		
		state.log(drawer.getName() + " drew a card from " + target.getName());
		
		//Discard pairs
		List<Card> discarded = drawer.discardPairs();
		
		if (!discarded.isEmpty()) {
			int pairs = discarded.size() / 2;
			state.log(drawer.getName() + " discarded " + pairs + " pairs");
			
			//check if any trap cards were discarded
			boolean hasTrap = discarded.stream().anyMatch(Card::isTrap);
			if (hasTrap) {
				//handle trap cards
				TrapCardHandler.handleDiscards(discarded, drawer, state, targetChooser);
			}
		}
		
		drawer.advanceDrawPointer();
		
		//update UI
		Platform.runLater(() -> {
			state.checkEndConditions();
			state.notifyStateChanged();
		});
	}
	
	public void stop() {
		isRunning = false;
	}
	
	//to carry human cardpick from UI to game loop
	private record PendingHumanDraw(int cardIndex) {}
}
