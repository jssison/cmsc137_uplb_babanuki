package logic;

import javafx.application.Platform;
import model.Card;
import model.GameState;
import model.Player;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class GameLoop implements Runnable {
	private static final long AI_COOLDOWN_MS    = 4000;
	private static final long HUMAN_COOLDOWN_MS = 1500;
	private static final long TICK_MS           = 100;
	private static final long AFK_TIMEOUT_MS    = 15000;

//	private long humanReadyTimestamp = 0;
	private final GameState state;
	private volatile boolean isRunning = true;

	// pending human draw (free-for-all)
//	private volatile Player pendingTargetPlayer = null;
//	private volatile int    pendingCardIndex    = -1;

	// pending human trap play — set by ui when player hits "play"
//	private volatile Card.Trap pendingHumanTrap = null;
	
	// Multi-human safe queues!
	private final java.util.Map<Player, Long> afkTimers = new java.util.concurrent.ConcurrentHashMap<>();
		private final java.util.Map<Player, PendingDraw> pendingDraws = new java.util.concurrent.ConcurrentHashMap<>();
		private final java.util.Map<Player, Card.Trap> pendingTraps = new java.util.concurrent.ConcurrentHashMap<>();

	private static class PendingDraw {
		final Player target; final int index;
		PendingDraw(Player t, int i) { this.target = t; this.index = i; }
	}

	// latch held while a trap dialog is open — freezes the whole loop
	private volatile CountDownLatch trapPauseLatch = null;

	private TrapCardHandler.TargetChooser targetChooser;

	public interface AnimationCallback {
		void playStealAnimation(Player stealer, Player target, int cardIndex, Runnable onComplete);
		void playDiscardAnimation(Player player, List<Card> discardedCards);
	}
	private AnimationCallback animationCallback;

	public GameLoop(GameState state, TrapCardHandler.TargetChooser targetChooser, AnimationCallback animationCallback) {
		this.state             = state;
		this.targetChooser     = targetChooser;
		this.animationCallback = animationCallback;
	}

	@Override
	public void run() {
		state.markStarted();
		while (isRunning && !state.isFinished()) {
			// if a trap dialog is open, block here until it resolves
			CountDownLatch latch = trapPauseLatch;
			if (latch != null) {
				try { latch.await(); } catch (InterruptedException e) {
					Thread.currentThread().interrupt(); break;
				}
			}

			tick();
			try { Thread.sleep(TICK_MS); } catch (InterruptedException e) {
				Thread.currentThread().interrupt(); break;
			}
		}
	}

	private void tick() {
		for (Player player : state.getPlayers()) {
			if (state.isFinished() || !isRunning) break;
			if (player.getIsOut()) continue;

			player.refreshState();

			if (!player.canDraw()) {
				if (player.getDrawState() == Player.DrawState.SKIPPED) {
					player.consumeSkip();
					player.startCooldown(player.getIsHuman() ? HUMAN_COOLDOWN_MS : AI_COOLDOWN_MS);
					state.log(player.getName() + " was skipped.");
					Platform.runLater(state::notifyStateChanged);
				}
				continue;
			}

			if (player.getIsHuman()) {
				processHumanTick(player);
			} else {
				processAITick(player);
			}
		}

		Platform.runLater(() -> {
			state.checkEndConditions();
			state.notifyStateChanged();
		});
	}

	// human tick — waits for click input or afk timeout
	private void processHumanTick(Player human) {
		afkTimers.putIfAbsent(human, System.currentTimeMillis());

		PendingDraw draw = pendingDraws.remove(human);

		if (draw == null) {
			if (System.currentTimeMillis() - afkTimers.get(human) > AFK_TIMEOUT_MS) {
				state.log(human.getName() + " took too long! auto-drawing...");
				Player target = human.getNextDrawTarget();
				if (target != null && target.handSize() > 0) {
					int idx = (int)(Math.random() * target.handSize());
					animatedPerformDraw(human, target, idx);
				}
				afkTimers.remove(human);
				human.startCooldown(HUMAN_COOLDOWN_MS);
			}
			return;
		}

		Player target = draw.target;
		int index     = draw.index;

		if (target.handSize() == 0) { state.log("no valid target to draw from"); return; }
		if (index < 0 || index >= target.handSize()) index = 0;

		performDraw(human, target, index);
		afkTimers.remove(human);
		human.startCooldown(HUMAN_COOLDOWN_MS);
	}

	// called by ui when player clicks a card
	// Now requires knowing WHO is submitting the draw
	public void submitHumanDraw(Player drawer, Player target, int cardIndex) {
		pendingDraws.put(drawer, new PendingDraw(target, cardIndex));
	}

	public void submitHumanTrapPlay(Player human, Card.Trap trap) {
		pendingTraps.put(human, trap);
	}

	// ai tick — draws then immediately plays any trap pairs
	private void processAITick(Player ai) {
		Player target = ai.getNextDrawTarget();
		if (target == null || target.handSize() == 0) return;

		int index = (int)(Math.random() * target.handSize());
		animatedPerformDraw(ai, target, index);

		// auto-play any trap pairs the ai is now holding
		playPendingTrapPairs(ai);

		long variance = (long)(Math.random() * 1000);
		ai.startCooldown(AI_COOLDOWN_MS + variance);
	}

	// plays all trap pairs for a given player (used by ai)
	private void playPendingTrapPairs(Player player) {
		List<Card.Trap> pending = player.getPendingTrapPairs();
		for (Card.Trap trap : pending) {
			List<Card> discarded = player.removeTrapPair(trap);
			if (discarded.isEmpty()) continue;

			state.log(player.getName() + " plays " + trap.name() + " trap pair!");
			if (animationCallback != null)
				animationCallback.playDiscardAnimation(player, discarded);

			List<model.Player> targets = state.getActivePlayers().stream()
				.filter(p -> p != player && p.handSize() > 0)
				.toList();

			if (!targets.isEmpty()) {
				// build a fake discard list containing one card so handleDiscards fires
				TrapCardHandler.handleDiscards(discarded, player, state, targetChooser);
			}
		}
	}

	// freezes the loop, plays animation, then does the actual draw
	private void animatedPerformDraw(Player drawer, Player target, int cardIndex) {
		if (animationCallback != null) {
			CountDownLatch wait = new CountDownLatch(1);
			animationCallback.playStealAnimation(drawer, target, cardIndex, wait::countDown);
			try { wait.await(); } catch (InterruptedException e) {
				Thread.currentThread().interrupt(); return;
			}
		}
		if (state.isFinished() || !isRunning) return;
		performDraw(drawer, target, cardIndex);
	}

	// core draw logic
	private void performDraw(Player drawer, Player target, int cardIndex) {
		if (target.handSize() == 0) return;
		if (cardIndex < 0 || cardIndex >= target.handSize())
			cardIndex = Math.max(0, target.handSize() - 1);

		Card drawn = target.takeCard(cardIndex);
		drawer.addCard(drawn);
		state.log(drawer.getName() + " drew a card from " + target.getName());

		// only discard non-trap pairs — trap pairs stay until played
		List<Card> discarded = drawer.discardNonTrapPairs();

		if (!discarded.isEmpty()) {
			state.log(drawer.getName() + " discarded " + (discarded.size() / 2) + " pair(s)");
			if (animationCallback != null)
				animationCallback.playDiscardAnimation(drawer, discarded);
		}

		// notify ui so it can show play buttons if a trap pair landed
		drawer.advanceDrawPointer();
		Platform.runLater(() -> {
			state.checkEndConditions();
			state.notifyStateChanged();
		});
	}

	// called by TrapCardHandler (via targetChooser) to pause loop during human dialog
	// returns a latch the caller should countDown() when the dialog closes
	public CountDownLatch pauseForTrapDialog() {
		CountDownLatch latch = new CountDownLatch(1);
		trapPauseLatch = latch;
		return latch;
	}

	// called after latch is counted down to clear the pause
	public void resumeFromTrapDialog() {
		trapPauseLatch = null;
	}

	// handle a human-submitted trap play
	// called from GameView's refresh loop when pendingHumanTrap is set


	public void processHumanTrapPlay(Player human) {
		Card.Trap trap = pendingTraps.remove(human);
		if (trap == null) return;

		List<Card> discarded = human.removeTrapPair(trap);
		if (discarded.isEmpty()) return;

		state.log(human.getName() + " plays " + trap.name() + " trap pair!");
		if (animationCallback != null)
			animationCallback.playDiscardAnimation(human, discarded);

		List<Player> targets = state.getActivePlayers().stream()
			.filter(p -> p != human && p.handSize() > 0)
			.toList();

		if (!targets.isEmpty()) {
			CountDownLatch latch = pauseForTrapDialog();
			TrapCardHandler.TargetChooser pausingChooser = (prompt, opts, onChosen) ->
				targetChooser.choose(prompt, opts, chosen -> {
					onChosen.accept(chosen);
					resumeFromTrapDialog();
					latch.countDown();
				});

			TrapCardHandler.handleDiscards(discarded, human, state, pausingChooser);
		}

		Platform.runLater(() -> {
			state.checkEndConditions();
			state.notifyStateChanged();
		});
	}

	public void stop() { isRunning = false; }
}