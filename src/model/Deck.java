package model;

//imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
	private final List<Card> cards = new ArrayList<>();
	
	//constructor
	public Deck() {
		this.build();
	}
	
	//build deck
	//remove one queen to make sure one queen has no pair
	private void build() {
		for (Card.Suit suit : Card.Suit.values()) {
			for (Card.Rank rank : Card.Rank.values()) {
				if (rank == Card.Rank.QUEEN && suit == Card.Suit.HEARTS) {
					//skip Queen of Hearts 
					continue;
				}
				cards.add(new Card(rank, suit));
			}
		}
	}
	
	public void shuffle() {
		Collections.shuffle(cards);
	}
	
	public void dealTo(List<Player> players) {
		int i = 0;
		
		//deal cards to each player
		for (Card card : cards) {
			players.get(i % players.size()).addCard(card);
			i++;
		}
	}
	
	//getter for deck size
	public int size() {
		return cards.size();
	}
}
