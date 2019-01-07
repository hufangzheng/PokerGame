public class PokerDeck {

    private PokerCard[] deck;
    private int cardsUsed;

    public PokerDeck() {
        deck = new PokerCard[54];
        int cardCt = 0;
        for (int suit = 0; suit <= 3; suit++) {
            for (int value = 2; value <= 14; value++) {
                deck[cardCt] = new PokerCard(value, suit);
                cardCt++;
            }
        }
        deck[52] = new PokerCard(1, PokerCard.JOKER);
        deck[53] = new PokerCard(2, PokerCard.JOKER);

        cardsUsed = 0;
    }

    public void shuffle() {
        for (int i = deck.length - 1; i > 0; i--) {
            int rand = (int)(Math.random() * (i + 1));
            PokerCard temp = deck[i];
            deck[i] = deck[rand];
            deck[rand] = temp;
        }
        cardsUsed = 0;
    }

    public PokerCard dealCard() {
        if (cardsUsed == deck.length)
            throw new IllegalStateException("No cards are left in the deck.");
        cardsUsed++;
        return deck[cardsUsed - 1];
    }
}
