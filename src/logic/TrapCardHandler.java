package logic;

//class imports
import model.Card;
import model.GameState;
import model.Player;

//util imports
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TrapCardHandler {
	public static void handleDiscards(
	    List<Card> discarded,
	    Player activator,
	    GameState state,
	    TargetChooser targetChooser
	) {
	    
	    // track triggered traps to prevent duplicate effects from a single pair
	    List<Card.Trap> triggered = new ArrayList<>();
	    
	    for (Card c : discarded) {
	        if (!c.isTrap()) continue;
	        if (triggered.contains(c.getTrap())) continue; // skip if already triggered
	        
	        triggered.add(c.getTrap()); // mark as triggered
	        
	        List<Player> targets = state.getActivePlayers()
	        		.stream()
	                .filter(p -> p != activator && p.handSize() > 0)
	                .toList();
	        
	        if (targets.isEmpty()) continue;
	        
	        switch (c.getTrap()) {
	            case SINGKO -> handleSingko(c, activator, targets, state, targetChooser);
	            case DOS -> handleDos(c, activator, targets, state, targetChooser);
	            case AMIS -> handleAmis(c, activator, targets, state, targetChooser);
	            default -> {}
	        }
	    }
	}
	
	//Trap card handlers
	
	//SINGKO: Player chooses another player to skip their next draw
	private static void handleSingko(
	    Card card,
	    Player activator,
	    List<Player> targets,
	    GameState state,
	    TargetChooser chooser
    ) {
	    
	    if (activator.getIsHuman()) {
	        // callback
	        chooser.choose("SINGKO! Choose a player to skip: ", targets, target -> {
	            target.applySkip();
	            state.log("SINGKO! " + activator.getName() + " skips " + target.getName() + "'s next draw.");
	            state.notifyStateChanged();
	        });
	    } else {
	    	//remove this for multiplayer
	        //for bot/AI
	    	//choose player with the fewest cards
	        Player target = targets.stream()
	                .min((a, b) -> Integer.compare(a.handSize(), b.handSize()))
	                .orElse(targets.get(0));
	        target.applySkip();
	        state.log("SINGKO! " + activator.getName()
	        	+ " skips " + target.getName() + "'s next draw."
        	);
	        state.notifyStateChanged();
	    }
	}
	
	//DOS: Steal one card from a chosen player
	private static void handleDos(
	    Card card,
	    Player activator,
	    List<Player> targets,
	    GameState state,
	    TargetChooser chooser
	    ) {
	    
	    if (activator.getIsHuman()) {
	        chooser.choose("DOS! Choose a player to steal from: ", targets, target -> {
	            executeDosSteal(activator, target, state, chooser);
	        });
	    } else {
	    	//remove this for multiplayer
			//for bot/AI
			//choose player with the most cards
	        Player target = targets.stream()
	                .max((a, b) -> Integer.compare(a.handSize(), b.handSize()))
	                .orElse(targets.get(0));
	                
	        executeDosSteal(activator, target, state, chooser);
	    }
	}

	// helper method holding effect logic
	private static void executeDosSteal(Player activator, Player target, GameState state, TargetChooser chooser) {
	    if (target.handSize() == 0) {
	        state.log("DOS! " + target.getName() + " has no cards to steal.");
	        return;
	    }
	    
	    // Pick a card to steal
	    int index = (int)(Math.random() * target.handSize());
	    Card stolen = target.takeCard(index);
	    activator.addCard(stolen);
	    
	    state.log("DOS! " + activator.getName() + " stole a card from " + target.getName() + ".");
	    
	    // Check if the stolen card created a new pair
	    List<Card> newDiscards = activator.discardNonTrapPairs();
	    if (!newDiscards.isEmpty()) {
	        state.log(activator.getName() + " discards " + (newDiscards.size() / 2) + " pair(s) after DOS steal.");
	        handleDiscardsNoChain(newDiscards, activator, state, chooser);
	    }
	    
	    state.notifyStateChanged();
	}
	
	//AMIS: swap hands with a chose player
	private static void handleAmis(
	    Card card,
	    Player activator,
	    List<Player> targets,
	    GameState state,
	    TargetChooser chooser
    ) {
	    
	    if (activator.getIsHuman()) {
	        chooser.choose("AMIS! Choose a player to swap hands with: ", targets, target -> {
	            swapHands(activator, target);
	            state.log("AMIS! " + activator.getName() + " swapped hands with " + target.getName() + ".");
	            state.notifyStateChanged();
	        });
	    } else {
	    	//remove this for multiplayer
			//for bot/AI
			//choose player with the fewest cards compared to the activator
	        Player target = targets.stream()
	                .filter(p -> p.handSize() < activator.handSize())
	                .min((a,b) -> Integer.compare(a.handSize(), b.handSize()))
	                .orElse(targets.get(0));
	                
	        swapHands(activator, target);
	        state.log("AMIS! " + activator.getName() + " swapped hands with " + target.getName() + ".");
	        state.notifyStateChanged();
	    }
	}
	
	//helpers
	private static void swapHands(Player a, Player b) {
		List<Card> tempA = new ArrayList<>(a.getHand());
		List<Card> tempB = new ArrayList<>(b.getHand());
		a.clearHand();
		b.clearHand();
		tempB.forEach(a::addCard);
		tempA.forEach(b::addCard);
	}
	
	//no recurse ver of handleDiscards
	//to prevent DOS chains
	private static void handleDiscardsNoChain(
		List<Card> discarded,
		Player activator,
		GameState state, 
		TargetChooser chooser
	) {
		state.log(activator.getName() + " discarded pairs after DOS steal (trap effects suppressed).");
	}
	
	//target chooser interface
	//humans can pick via dialog
	@FunctionalInterface
	public interface TargetChooser {
	    void choose(String prompt, List<Player> options, Consumer<Player> onChosen);
	}
}