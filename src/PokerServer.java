import networkcore.Server;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PokerServer extends Server {

    private int currentPlayer;

    private PokerCard[][] hand = new PokerCard[4][25];   // Everyone have 25 cards, remain 8 cards.
    private PokerCard[] remainCards = new PokerCard[8];
    private PokerDeck deck1;
    private PokerDeck deck2;

    public PokerServer(int port) throws IOException {
        super(port);
        currentPlayer = 1;
    }

    protected void playerConnected(int playerID) {
        sendToAll("player " + playerID + " join the room.");
        if (playerID == 4) {
            shutdownServerSocket();
            sendToAll("*Game Begin*");
            beginGame();                    //   Begin Game
            sendToOne(1, hand[0]);
            sendToOne(2, hand[1]);
            sendToOne(3, hand[2]);
            sendToOne(4, hand[3]);
            sendToOne(currentPlayer, "your turn");
        }
    }

    protected void playerDisconnected(int playerID) {
        System.out.println("player " + playerID + " quits.");
    }

    protected void messageReceived(int playerID, Object message) {
        if (message instanceof PokerCard) {
            PokerCard playCard = (PokerCard) message;
            System.out.println("get card");
            sendToAll("player " + playerID + " play: " + playCard.toString());
            currentPlayer++;
            if (currentPlayer > 4)
                currentPlayer = 1;
//            System.out.println("player " + playerID + " plays: " + message.toString());
        }
        else if (message instanceof PokerCard[]) {
            hand[playerID] = (PokerCard[]) message;
            int remainCards = statisticCards(hand[playerID]);
            if (remainCards == 0)
                sendToAll("*Game over* player " + playerID + " win!");
            sendToOne(currentPlayer, "your turn");
        }
        else if (message instanceof String) {
            System.out.println((String)message);
        }
    }

//    private class CardComparator implements Comparator<PokerCard>, Serializable {
//
//        public int compare(PokerCard card1, PokerCard card2) {
//
//        }
//    }

    private int statisticCards(PokerCard[] handCards) {
        int num = 0;
        for (PokerCard c : handCards) {
            if (c != null)
                num++;
        }

        return num;
    }

    private void beginGame() {
        deck1 = new PokerDeck();
        deck2 = new PokerDeck();
        deck1.shuffle();
        deck2.shuffle();
        PokerCard[] deck = new PokerCard[108];
        for (int i = 0; i < 54; i++)
            deck[i] = deck1.dealCard();
        for (int i = 54; i < 108; i++)
            deck[i] = deck2.dealCard();
        for (int i = 0, k = 0; i < 4; i++) {
            for (int j = 0; j < 25; j++) {
                hand[i][j] = deck[k++];
            }
        }
        for (int i = 100, j = 0; i < 108; i++)
            remainCards[j++] = deck[i];
    }

    public static void main(String[] args) {
        try {
            PokerServer pokerServer = new PokerServer(32058);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
