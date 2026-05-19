package model;

public class Card {
	public enum Rank {
		ACE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT,
		NINE, TEN, JACK, QUEEN, KING;
		
		public String display() {
			return switch (this) {
				case ACE -> "A"; 
				case TWO -> "2";
				case THREE -> "3";
				case FOUR -> "4";
				case FIVE -> "5";
				case SIX -> "6";
				case SEVEN -> "7";
				case EIGHT -> "8";
				case NINE -> "9";
				case TEN -> "10";
				case JACK -> "J";
				case QUEEN -> "Q";
				case KING -> "K";
			};
		}
	}
	
	public enum Suit {
		HEARTS, DIAMONDS, CLUBS, SPADES;
		
		public String symbol() {
			//symbols from the internet
			return switch (this) {
				case HEARTS -> "♥";
				case DIAMONDS -> "♦";
				case CLUBS    -> "♣";
                case SPADES   -> "♠";
			};
		}
		
		public boolean isRed() {
			return this == HEARTS || this == DIAMONDS;
		}
	}
	
	//trap cards
	public enum Trap { NONE, SINGKO, DOS, AMIS }
	
	private final Rank rank;
	private final Suit suit;
	
	//constructor
	public Card(Rank rank, Suit suit) {
		this.rank = rank;
		this.suit = suit;
	}
	
	//getters
	public Rank getRank() { return this.rank; }
	public Suit getSuit() { return this.suit; }
	
	//check if rank is of the trap card ranks
	// ACE = AMIS
	// DOS = 2
	// SINGKO = 5
	public boolean isTrap() {
		return this.rank == Rank.FIVE || this.rank == Rank.TWO || this.rank == Rank.ACE;
	}
	
	public Trap getTrap() {
		return switch(rank) {
			case FIVE -> Trap.SINGKO;
			case TWO -> Trap.DOS;
			case ACE -> Trap.AMIS;
			default -> Trap.NONE;
		};
	}
	
	public boolean isQueen() {
		return this.rank == Rank.QUEEN;
	}
	
	//check if this card is a pair with "other" card
	public boolean isPairWith(Card other) {
		return this.rank == other.rank;
	}
	
	@Override
	public String toString() {
		return rank.display() + suit.symbol();
	}
}
