package logic;

//class imports
import model.Card;
import model.GameState;
import model.Player;

//util imports
import java.util.ArrayList;
import java.util.List;

public class TrapCardHandler {
	public static void handleDiscards(
		List<Card> discarded,
		Player activator,
		GameState state,
		TargetChooser targetChooser
	) {
		
		for (Card c : discarded) {
			if (!c.isTrap()) { continue; }
			
			List<Player> targets = state.getActivePlayers()
					.stream()
					.filter(p -> p != activator)
					.toList();
			
			if (targets.isEmpty()) { continue; }
			
			switch (c.getTrap()) {
				case SINGKO -> handleSingko(c, activator, targets, state, targetChooser);
				case UNO -> handleUno(c, activator, targets, state, targetChooser);
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
		Player target;
		if (activator.getIsHuman()) {
			target = chooser.choose("SINGKO! Choose a player to skip: ", targets);
		} else {
			//remove this for multiplayer
			//for bot/AI
			//choose player with the fewest cards
			target = targets.stream()
					.min((a, b) -> Integer.compare(a.handSize(), b.handSize()))
					.orElse(targets.get(0));
		}
		
		target.applySkip();
		state.log("SINGKO! " + activator.getName()
			+ " skips " + target.getName() + "'s next draw."
		);
		state.notifyStateChanged();
	}
	
	//UNO: Steal one card from a chosen player
	private static void handleUno(
		Card card,
		Player activator,
		List<Player> targets,
		GameState state,
		TargetChooser chooser
	) {
		Player target;
		if (activator.getIsHuman()) {
			target = chooser.choose("UNO! Choose a player to steal from: ", targets);
		} else {
			//remove this for multiplayer
			//for bot/AI
			//choose player with the most cards
			target = targets.stream()
					.max((a, b) -> Integer.compare(a.handSize(), b.handSize()))
					.orElse(targets.get(0));
		}
		
		if (target.handSize() == 0) {
			state.log("UNO! " + target.getName() + " has no cards to steal.");
			return;
		}
		
		//pick a card to steal
		int index = (int)(Math.random() * target.handSize());
		Card stolen = target.takeCard(index);
		activator.addCard(stolen);
		
		state.log("UNO! " + activator.getName()
			+ " stole a card from " + target.getName() + "."
		);
		
		List<Card> newDiscards = activator.discardPairs();
		if (!newDiscards.isEmpty()) {
			state.log(activator.getName() + " discards "
					+ (newDiscards.size() / 2) + " pair(s) after UNO steal."
			);
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
		Player target;
		if (activator.getIsHuman()) {
			target = chooser.choose("AMIS! Choose a player to swap hands with: ", targets);
		} else {
			//remove this for multiplayer
			//for bot/AI
			//choose player with the fewest cards compared to the activator
			target = targets.stream()
					.filter(p -> p.handSize() < activator.handSize())
					.min((a,b) -> Integer.compare(a.handSize(), b.handSize()))
					.orElse(targets.get(0));
		}
		
		swapHands(activator, target);
		state.log("AMIS! " + activator.getName()
			+ " swapped hands with " + target.getName() + "."
		);
		state.notifyStateChanged();
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
	//to prevent UNO chains
	private static void handleDiscardsNoChain(
		List<Card> discarded,
		Player activator,
		GameState state, 
		TargetChooser chooser
	) {
		//left intentionally empty to supress trap effects chaining
	}
	
	//target chooser interface
	//humans can pick via dialog
	@FunctionalInterface
	public interface TargetChooser {
		Player choose(String prompt, List<Player> options);
	}
}
