package model;

//imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
	public enum DrawState { READY, COOLDOWN, SKIPPED }
	
	private final String name;
	private final boolean isHuman; //for singleplayer config
	private final List<Card> hand = new ArrayList<>();
	
	//keep track of which player to draw card from
	private List<Player> drawRotation = new ArrayList<>();
	private int drawIndex = 0;
	
	//keep track of this player's drawState
	// e.g. if player can draw, is blocked, etc
	private DrawState drawState = DrawState.READY;
	//cooldown until player can draw again
	private long cooldownUntil = 0;
	
	//flag if player is out of the game
	private boolean isOut = false; 
	
	//constructor
	public Player(String name, boolean isHuman) {
		this.name = name;
		this.isHuman = isHuman;
	}
	
	
	//Player's hand management
	
	//adds a card to this player's hand
	public synchronized void addCard(Card card) {
		hand.add(card);
	}
	
	//returns the hand index of the taken card
	//used for when other players draw from this player's hand
	public synchronized Card takeCard(int index) {
		return hand.remove(index);
	}
	
	public synchronized int handSize() {
		return hand.size();
	}
	
	//returns a copy of the player's hand
	public synchronized List<Card> getHand() {
		return Collections.unmodifiableList(new ArrayList<>(hand));
	}
	
	public synchronized void clearHand() {
		hand.clear();
	}
	
	public synchronized List<Card> discardPairs() {
        List<Card> discarded = new ArrayList<>();
 
        boolean found = true;
        while (found) {
            found = false;
 
            //snapshot so indices are stable during the pair search
            List<Card> snapshot = new ArrayList<>(hand);
 
            outerLoop:
            for (int i = 0; i < snapshot.size(); i++) {
                for (int j = i + 1; j < snapshot.size(); j++) {
                    if (snapshot.get(i).isPairWith(snapshot.get(j))) {
                        Card cardI = snapshot.get(i);
                        Card cardJ = snapshot.get(j);
 
                        //find actual positions in live hand by object reference
                        int realI = hand.indexOf(cardI);
                        int realJ = hand.lastIndexOf(cardJ);
 
                        //guard: valid, distinct indices
                        if (realI >= 0 && realJ >= 0 && realI != realJ) {
                            //remove higher index first so lower stays valid
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
        }
 
        if (hand.isEmpty()) {
            isOut = true;
        }
 
        return discarded;
    }

	
	
	//Draw Rotation
	public void setDrawRotation(List<Player> rotation) {
		this.drawRotation = new ArrayList<>(rotation);
		this.drawIndex = 0;
	}
	
	public Player getNextDrawTarget() {
		if (drawRotation.isEmpty()) { return null; }
		
		int attempts = 0;
		int size = drawRotation.size();
		
		//attempt to draw from each player
		while (attempts < size) {
			//get candidate player to draw from based from index
			Player candidate = drawRotation.get(drawIndex % size);
			
			//candidate player is chosen if not out of the game and has at least 1 card in hand
			if (!candidate.getIsOut() && candidate.handSize() > 0) {
				return candidate;
			}
			
			//update drawIndex and attempts
			drawIndex = (drawIndex + 1) % size;
			attempts++;
		}
		
		//no valid candidates
		return null;
	}
	
	//for advancing to next player to draw from 
	//after a successful draw
	public void advanceDrawPointer() {
		if (!drawRotation.isEmpty()) {
			//if rotation is not empty, advance draw index
			drawIndex = (drawIndex + 1) % drawRotation.size();
		}
	}
	
	//cooldown/state management
	
	public void startCooldown(long cooldownInMillis) {
		drawState = DrawState.COOLDOWN;
		//player is on cooldown until specified time in cooldownUntil
		cooldownUntil = System.currentTimeMillis() + cooldownInMillis;
	}
	
	public void applySkip() {
		drawState = DrawState.SKIPPED;
	}
	
	//check if player is now off cooldown
	//will call every tick to check
	public void refreshState() {
		if (drawState == DrawState.COOLDOWN
				&& System.currentTimeMillis() >= cooldownUntil) {
			drawState = DrawState.READY;
		}
	}
	
	public boolean canDraw() {
		return drawState == DrawState.READY && !isOut;
	}
	
	//consumes applied skip
	public boolean consumeSkip() {
		if (drawState == DrawState.SKIPPED) {
			drawState = DrawState.READY;
			return true;
		}
		return false;
	}
	
	//returns how much time left in cooldown
	public long getRemainingCooldown() {
		if (drawState != DrawState.COOLDOWN) { return 0; }
		return Math.max(0, cooldownUntil - System.currentTimeMillis());
	}

	
	//getters for player status
	public boolean getIsOut() { return this.isOut; }
	public boolean getIsHuman() { return this.isHuman; }
	public String getName() { return this.name; }
	public DrawState getDrawState() { return this.drawState; }
	
	@Override
	public String toString() {
		return name;
	}
}
