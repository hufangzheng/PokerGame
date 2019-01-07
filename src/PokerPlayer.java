import networkcore.Client;

import java.io.IOException;
import java.util.Scanner;

public class PokerPlayer extends Client {

    private PokerCard[] hand;
    private boolean isMyTurn;
    private int playCardID;
    Scanner input = new Scanner(System.in);

    public PokerPlayer(String hostIPAddress, int hostPort) throws IOException {
        super(hostIPAddress, hostPort);
    }

    protected void playerConnected(int newPlayerID) {

    }

    protected void playerDisconnected(int departingPlayerID) {

    }

    protected void connectionClosedByError(String message) { }

    protected void serverShutdown(String message) { }

    protected void messageReceived(Object message) {
        if ("your turn".equals(message)) {
            System.out.println(message);
            printHand();
            playCardID = input.nextInt();
            PokerCard p = hand[playCardID];
            send(p);
            hand[playCardID] = null;
            send(hand);
        }
        else if (message instanceof PokerCard[]) {
            hand = (PokerCard[])message;
            printHand();
        }
        else if (message instanceof String) {
            System.out.println((String)message);
        }
    }

    private void printHand() {
        int num = 0;
        System.out.println("-------------------------------------------------------------------------");
        for (PokerCard c : hand) {
            if (c != null) {
                System.out.println(num + " | " + c.toString());
                num++;
            }
        }
        System.out.println("-------------------------------------------------------------------------");
    }

    public static void main(String[] args) {
        try {
            PokerPlayer pokerPlayer = new PokerPlayer("192.168.43.124", 32058);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
