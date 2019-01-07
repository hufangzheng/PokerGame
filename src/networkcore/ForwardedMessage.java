package networkcore;

import java.io.Serializable;

public class ForwardedMessage implements Serializable {

    public final int senderID;
    public final Object message;

    public ForwardedMessage(int senderID, Object message) {
        this.senderID = senderID;
        this.message = message;
    }
}
