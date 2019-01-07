package networkcore;

import java.io.Serializable;

final class StatusMessage implements Serializable {

    public final int playerID;

    /**
     * True if the player has just connected; false if the player
     * has just disconnected.
     */
    public final boolean connecting;

    /**
     * Players' list.
     */
    public final int[] players;

    public StatusMessage(int playerID, boolean connecting, int[] players) {
        this.playerID = playerID;
        this.connecting = connecting;
        this.players = players;
    }

}
