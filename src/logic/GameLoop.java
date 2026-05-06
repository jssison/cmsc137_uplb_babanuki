package logic;

import javafx.application.Platform;
import model.Card;
import model.GameState;
import model.Player;

import java.util.List;
import java.util.concurrent.CountDownLatch; // NEW IMPORT!

public class GameLoop implements Runnable {
	//cooldowns
	private static final long AI_COOLDOWN_MS = 4000;
	private static final long HUMAN_COOLDOWN_MS = 1500; //too op if no cooldown
	private static final long TICK_MS = 100;
	
	private static final long AFK_TIMEOUT_MS = 15000; //for players taking too long to draw
	private long humanReadyTimestamp = 0;
	private final GameState state;
	private volatile boolean isRunning = true;
	
	// for new free-for-all 
	private volatile Player pendingTargetPlayer = null;
	private volatile int pendingCardIndex = -1;
	
	private TrapCardHandler.TargetChooser targetChooser;
	
	// NEW: The callback interface so the backend can trigger UI animations
	public interface AnimationCallback {
		void playStealAnimation(Player stealer, Player target, int cardIndex, Runnable onComplete);
		void playDiscardAnimation(Player player, List<Card> discardedCards);
	}
	private AnimationCallback animationCallback;
	
	//constructor (UPDATED to accept the callback)
	public GameLoop(GameState state, TrapCardHandler.TargetChooser targetChooser, AnimationCallback animationCallback) {
		this.state = state;
		this.targetChooser = targetChooser;
		this.animationCallback = animationCallback;
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
			if (state.isFinished() || !isRunning) break;
			
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
		if (humanReadyTimestamp == 0) {
			humanReadyTimestamp = System.currentTimeMillis();
		}
		
		//new free for all logic
		if (pendingTargetPlayer == null || pendingCardIndex == -1) {
			if (System.currentTimeMillis() - humanReadyTimestamp > AFK_TIMEOUT_MS) {
				state.log(human.getName() + " took too long! Auto-drawing...");
				Player target = human.getNextDrawTarget(); 
				if (target != null && target.handSize() > 0) {
					int randomCardIndex = (int)(Math.random() * target.handSize());
					// USE ANIMATED DRAW FOR AFK!
					animatedPerformDraw(human, target, randomCardIndex); 
				}
				humanReadyTimestamp = 0;
				human.startCooldown(HUMAN_COOLDOWN_MS);
			}
			return; 
		}
		
		Player target = pendingTargetPlayer;
		int index = pendingCardIndex;
		
		pendingTargetPlayer = null;
		pendingCardIndex = -1;
		
		if (target.handSize() == 0) {
			state.log("No valid target to draw from");
			return;
		}
		
		if (index < 0 || index >= target.handSize()) { index = 0; }
		
		// Standard performDraw, because the UI already animated the human click!
		performDraw(human, target, index);
		
		humanReadyTimestamp = 0;
		human.startCooldown(HUMAN_COOLDOWN_MS);
	}
	
	//for UI
	public void submitHumanDraw(Player target, int cardIndex) {
		// new free for all logic
		this.pendingTargetPlayer = target;
		this.pendingCardIndex = cardIndex;
	}
	
	//AI tick
	private void processAITick(Player ai) {
		Player target = ai.getNextDrawTarget();
		if (target == null || target.handSize() == 0) { return; }
		
		int index = (int)(Math.random() * target.handSize());
		
		// THE NEW LOGIC: Use the animated draw instead of instantly moving data!
		animatedPerformDraw(ai, target, index);
		
		long variance = (long)(Math.random() * 1000);
		ai.startCooldown(AI_COOLDOWN_MS + variance);
	}

	// NEW METHOD: Halts the GameLoop Thread while the UI plays the animation
	private void animatedPerformDraw(Player drawer, Player target, int cardIndex) {
		if (animationCallback != null) {
			CountDownLatch waitForAnimation = new CountDownLatch(1);
			
			// Trigger UI animation
			animationCallback.playStealAnimation(drawer, target, cardIndex, () -> {
				waitForAnimation.countDown(); // UI tells us it finished!
			});
			
			// Freeze GameLoop until countDown() happens
			try {
				waitForAnimation.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		if (state.isFinished() || !isRunning) return;
		
		// Now actually draw the card
		performDraw(drawer, target, cardIndex);
	}
	
	//draw logic
	private void performDraw(Player drawer, Player target, int cardIndex) {
		if (target.handSize() == 0) return;
		if (cardIndex < 0 || cardIndex >= target.handSize()) {
			cardIndex = Math.max(0, target.handSize() - 1); 
		}
		
		Card drawn = target.takeCard(cardIndex);
		drawer.addCard(drawn);
		
		state.log(drawer.getName() + " drew a card from " + target.getName());
		
		//Discard pairs
		List<Card> discarded = drawer.discardPairs();
		
		if (!discarded.isEmpty()) {
			int pairs = discarded.size() / 2;
			state.log(drawer.getName() + " discarded " + pairs + " pairs");
			
			if (animationCallback != null) {
				animationCallback.playDiscardAnimation(drawer, discarded);
			}
			
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
}