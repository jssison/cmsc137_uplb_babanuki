package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
	public enum DrawState { READY, COOLDOWN, SKIPPED }

	private String name;
	private boolean isHuman;
	private final List<Card> hand = new ArrayList<>();

	private List<Player> drawRotation = new ArrayList<>();
	private int drawIndex = 0;

	private DrawState drawState = DrawState.READY;
	private long cooldownUntil = 0;
	private boolean isOut = false;
	
	private int extraDraws = 0;

	public Player(String name, boolean isHuman) {
		this.name = name;
		this.isHuman = isHuman;
	}

	// hand management

	public synchronized void addCard(Card card) {
		hand.add(card);
	}

	public synchronized Card takeCard(int index) {
		Card taken = hand.remove(index);
		if (hand.isEmpty()) isOut = true;
		return taken;
	}

	public synchronized int handSize() { return hand.size(); }

	public synchronized List<Card> getHand() {
		return Collections.unmodifiableList(new ArrayList<>(hand));
	}

	public synchronized void clearHand() { hand.clear(); }

	// discards all pairs regardless of type — used at game end
	public synchronized List<Card> discardAllPairs() {
		return discardPairsInternal(false);
	}

	// discards only non-trap pairs — used during normal draw flow
	// trap pairs stay in hand until manually played
	public synchronized List<Card> discardNonTrapPairs() {
		return discardPairsInternal(true);
	}

	// skipTraps=true means trap pairs are left alone
	private List<Card> discardPairsInternal(boolean skipTraps) {
		List<Card> discarded = new ArrayList<>();

		boolean found = true;
		while (found) {
			found = false;
			List<Card> snapshot = new ArrayList<>(hand);

			outerLoop:
			for (int i = 0; i < snapshot.size(); i++) {
				for (int j = i + 1; j < snapshot.size(); j++) {
					Card ci = snapshot.get(i);
					Card cj = snapshot.get(j);

					if (!ci.isPairWith(cj)) continue;
					if (skipTraps && ci.isTrap()) continue; // leave trap pairs alone

					int realI = hand.indexOf(ci);
					int realJ = hand.lastIndexOf(cj);

					if (realI >= 0 && realJ >= 0 && realI != realJ) {
						int hi = Math.max(realI, realJ);
						int lo = Math.min(realI, realJ);
						discarded.add(hand.remove(hi));
						discarded.add(hand.remove(lo));
						found = true;
					}
					break outerLoop;
				}
			}
		}

		if (hand.isEmpty()) isOut = true;
		return discarded;
	}

	// returns trap pairs currently sitting in hand (pairs of same trap rank)
	// used by ai to auto-play and by ui to show play buttons
	public synchronized List<Card.Trap> getPendingTrapPairs() {
		List<Card.Trap> pending = new ArrayList<>();
		List<Card> snapshot = new ArrayList<>(hand);

		for (int i = 0; i < snapshot.size(); i++) {
			Card ci = snapshot.get(i);
			if (!ci.isTrap()) continue;
			if (pending.contains(ci.getTrap())) continue; // already noted

			for (int j = i + 1; j < snapshot.size(); j++) {
				if (snapshot.get(j).isPairWith(ci)) {
					pending.add(ci.getTrap());
					break;
				}
			}
		}
		return pending;
	}

	// removes and returns both cards of a trap pair from hand
	// returns empty list if pair not found
	public synchronized List<Card> removeTrapPair(Card.Trap trap) {
		List<Card> removed = new ArrayList<>();
		List<Card> snapshot = new ArrayList<>(hand);

		for (int i = 0; i < snapshot.size(); i++) {
			Card ci = snapshot.get(i);
			if (!ci.isTrap() || ci.getTrap() != trap) continue;

			for (int j = i + 1; j < snapshot.size(); j++) {
				Card cj = snapshot.get(j);
				if (cj.isPairWith(ci)) {
					// remove higher index first
					int realI = hand.indexOf(ci);
					int realJ = hand.lastIndexOf(cj);
					if (realI >= 0 && realJ >= 0 && realI != realJ) {
						int hi = Math.max(realI, realJ);
						int lo = Math.min(realI, realJ);
						removed.add(hand.remove(hi));
						removed.add(hand.remove(lo));
					}
					break;
				}
			}
			break;
		}

		if (hand.isEmpty()) isOut = true;
		return removed;
	}

	// draw rotation

	public void setDrawRotation(List<Player> rotation) {
		this.drawRotation = new ArrayList<>(rotation);
		this.drawIndex = 0;
	}

	public Player getNextDrawTarget() {
		if (drawRotation.isEmpty()) return null;

		int attempts = 0;
		int size = drawRotation.size();
		while (attempts < size) {
			Player candidate = drawRotation.get(drawIndex % size);
			if (!candidate.getIsOut() && candidate.handSize() > 0) return candidate;
			drawIndex = (drawIndex + 1) % size;
			attempts++;
		}
		return null;
	}

	public void advanceDrawPointer() {
		if (!drawRotation.isEmpty())
			drawIndex = (drawIndex + 1) % drawRotation.size();
	}

	// cooldown / state

	public void startCooldown(long ms) {
		drawState = DrawState.COOLDOWN;
		cooldownUntil = System.currentTimeMillis() + ms;
	}

	public void applySkip() { drawState = DrawState.SKIPPED; }

	public void refreshState() {
		if (drawState == DrawState.COOLDOWN && System.currentTimeMillis() >= cooldownUntil)
			drawState = DrawState.READY;
	}

	public boolean canDraw() { return drawState == DrawState.READY && !isOut; }

	public boolean consumeSkip() {
		if (drawState == DrawState.SKIPPED) { drawState = DrawState.READY; return true; }
		return false;
	}

	public long getRemainingCooldown() {
		if (drawState != DrawState.COOLDOWN) return 0;
		return Math.max(0, cooldownUntil - System.currentTimeMillis());
	}
	
	// ── DOS (EXTRA DRAWS) TRAP MECHANICS ──────────────────────────

	public synchronized void addExtraDraws(int amount) {
		this.extraDraws += amount;
	}

	public synchronized boolean consumeExtraDraw() {
		if (this.extraDraws > 0) {
			this.extraDraws--;
			return true;
		}
		return false;
	}

	public synchronized int getExtraDraws() {
		return this.extraDraws;
	}
	
	public synchronized void shuffleHand() {
		java.util.Collections.shuffle(hand);
	}

	// getters
	public boolean getIsOut()        { return isOut; }
	public boolean getIsHuman()      { return isHuman; }
	public String getName()          { return name; }
	public DrawState getDrawState()  { return drawState; }
	
	// setter
	public void setIsHuman(boolean h){ this.isHuman = h; }
	public void setName(String name) { this.name = name; }

	@Override public String toString() { return name; }
}