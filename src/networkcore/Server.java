package networkcore;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

public class Server {

    private TreeMap<Integer, ConnectionToClient> playerConnections;
    private LinkedBlockingQueue<Message> incomingMessages;

    /**
     * If the autoreset property is set to true, then the ObjectOutputStreams that are
     * used for transmitting messages to clients is reset before each object is sent.
     */
    private volatile boolean autoreset;

    private ServerSocket serverSocket;
    private Thread serverThread;
    volatile private boolean shutdown;  // Set to true when the Server is not listening.

    private int nextClientID = 1;   // The id number that will be assigned to
                                    // the next client that connects.

    public Server(int port) throws IOException {
        playerConnections = new TreeMap<Integer, ConnectionToClient>();
        incomingMessages = new LinkedBlockingQueue<Message>();
        serverSocket = new ServerSocket(port);
        System.out.println("Listening for client connections on port " + port);
        serverThread = new ServerThread();
        serverThread.start();
        Thread readerThread = new Thread() {    // Reader the message from incoming queue.
            public void run() {
                while (true) {
                    try {
                        Message msg = incomingMessages.take();
                        messageReceived(msg.playerConnection, msg);
                    }
                    catch (Exception e) {
                        System.out.println("Read message error.");
                        e.printStackTrace();
                    }
                }
            }
        };
        readerThread.setDaemon(true);
        readerThread.start();
    }

    protected void messageReceived(int playerID, Object message) {
        sendToAll(new ForwardedMessage(playerID,message));
    }

    protected void playerConnected(int playerID) {

    }

    protected void playerDisconnected(int playerID) {

    }

    protected void extraHandShake(int playerID, ObjectInputStream input, ObjectOutputStream output) throws IOException {

    }

    synchronized public int[] getPlayerList() {
        int[] playerList = new int[playerConnections.size()];
        int i = 0;
        for (int playerID : playerConnections.keySet()) {
            playerList[i++] = playerID;
        }

        return playerList;
    }

    public void shutdownServerSocket() {
        incomingMessages.clear();
        serverThread = null;
        serverSocket = null;
    }

    /**
     * Shutdown entire Server
     */
    public void shutdownServer() {
        shutdownServerSocket();
        sendToAll(new DisconnectMessage("*Server shutdown*"));
        for (ConnectionToClient client : playerConnections.values()) {
            client.close();
        }
    }

    synchronized public void sendToAll(Object message) {
        if (message == null)
            throw new IllegalArgumentException("The message can't be null.");
        if ( ! (message instanceof Serializable) )
            throw new IllegalArgumentException("The message should implement Serializable.");
        for (ConnectionToClient toPlayer : playerConnections.values())
            toPlayer.send(message);
    }

    synchronized public boolean sendToOne(int recipientID, Object message) {
        if (message == null)
            throw new IllegalArgumentException("The message can't be null.");
        if ( ! (message instanceof Serializable) )
            throw new IllegalArgumentException("The message should implement Serializable.");
        ConnectionToClient toPlayer = playerConnections.get(recipientID);
        if (toPlayer != null) {
            toPlayer.send(message);
            return true;
        }
        else {
            System.out.println("There are no such player.");
            return false;
        }
    }

    synchronized private void messageReceived(ConnectionToClient connection, Object message) {
        int senderID = connection.getPlayerID();
        messageReceived(senderID, connection);
    }

    /**
     * Accept connection, add the new connection to TreeMap and send StatusMessage to all clients.
     * @param connection
     */
    synchronized private void acceptConnection(ConnectionToClient connection) {
        int playerID = connection.getPlayerID();
        playerConnections.put(playerID, connection);
        StatusMessage sm = new StatusMessage(playerID, true, getPlayerList());
        sendToAll(sm);
        playerConnected(playerID);
        System.out.println("Connection accepted from client number " + playerID);
    }

    /**
     * Remove connection information from TreeMap and send StatusMessage to all clients if the client is disconnected.
     * @param playerID
     */
    synchronized private void clientDisconnected(int playerID) {
        if (playerConnections.containsKey(playerID)) {
            playerConnections.remove(playerID);
            StatusMessage sm = new StatusMessage(playerID, false, getPlayerList());
            sendToAll(sm);
            playerDisconnected(playerID);
            System.out.println("Connection with client ID " + playerID + "closed by DisconnectedMessage.");
        }
    }

    synchronized private void connectionToClientClosedWithError(ConnectionToClient playerConnection, String message) {
        int ID = playerConnection.getPlayerID();
        if (playerConnections.remove(ID) != null) {
            StatusMessage sm = new StatusMessage(ID, false, getPlayerList());
            sendToAll(sm);
        }
    }

    private class Message {
        ConnectionToClient playerConnection;
        Object message;

    }

    /**
     * Listen client's connection requests.
     */
    private class ServerThread extends Thread {
        public void run() {
            try {
                while ( !shutdown) {
                    Socket connection = serverSocket.accept();
                    if (shutdown) {
                        System.out.println("Listener socket has shut down.");
                        break;
                    }
                    new ConnectionToClient(incomingMessages, connection);
                }
            }
            catch (Exception e) {
                if (shutdown) {
                    System.out.println("The Server was shutdown.");
                }
                e.printStackTrace();
            }

        }
    }

    /* Handles communication with one client. */
    private class ConnectionToClient {

        private int playerID;
        private BlockingQueue<Message> incomingMessages;        //incomingMessage from Server
        private LinkedBlockingQueue<Object> outgoingMessages;   // Send all subclass of Object as message.
        private Socket connection;
        /* I/O Stream */
        ObjectInputStream input;
        ObjectOutputStream output;
        private volatile boolean closed;   // Set to true when connection is closing normally.
        private Thread sendThread;
        private volatile Thread receiveThread;

        ConnectionToClient(BlockingQueue<Message> receivedMessageQueue, Socket connection) {
            this.connection = connection;
            incomingMessages = receivedMessageQueue;
            outgoingMessages = new LinkedBlockingQueue<Object>();
            sendThread = new SendThread();
            sendThread.start();
        }

        int getPlayerID() {
            return playerID;
        }

        /**
         * Close ConnectionToClient's socket and threads.
         */
        void close() {
            closed = true;
            sendThread.interrupt();
            if (receiveThread != null)
                receiveThread.interrupt();
            try {
                connection.close();
            }
            catch (IOException e) {
            }
        }

        /*  Drop message into message output queue. */
        void send(Object obj) {
            if (obj instanceof DisconnectMessage) {   // Clean the outgoing queue if the obj is instance of DisconnectMessage.
                outgoingMessages.clear();
            }
            outgoingMessages.add(obj);
        }

        private void closeWithError(String message) {
            connectionToClientClosedWithError(this, message);
            close();
        }

        private class SendThread extends Thread {
            public void run() {
                try {
                    output = new ObjectOutputStream(connection.getOutputStream());
                    input = new ObjectInputStream(connection.getInputStream());
                    String handle = (String) input.readObject();
                    if (! "Hello Server".equals(handle))
                        throw new Exception("Incorrect hello string received from client.");
                    synchronized (Server.this) {
                        playerID = nextClientID++;
                    }
                    output.writeObject(playerID);
                    output.flush();
                    receiveThread = new ReceiveThread();
                    receiveThread.start();
                }
                catch (Exception e) {
                    try {
                        closed = true;
                        connection.close();
                    }
                    catch (Exception e1){
                    }
                    System.out.println("\nError while setting up connection: " + e);
                    e.printStackTrace();
                    return ;
                }

                try {
                    while( !closed ) {
                        try {
                            Object message = outgoingMessages.take();
                            if (message instanceof ResetSignal)
                                output.reset();
                            else {
                                if (autoreset)
                                    output.reset();
                                output.writeObject(message);
                                output.flush();
                                if (message instanceof DisconnectMessage)
                                    close();
                            }
                        }
                        catch (InterruptedException e) {

                        }

                    }
                }
                catch (IOException e) {
                    closeWithError("Error while sending data to client.");
                    e.printStackTrace();
                }
                catch (Exception e) {
                    closeWithError("Internal Error: An unexpected exception has occurred.");
                    e.printStackTrace();
                }
            }
        }

        private class ReceiveThread extends Thread {
            public void run() {
                try {
                    while ( ! closed ) {
                        Object message = input.readObject();
                        Message msg = new Message();
                        msg.playerConnection = ConnectionToClient.this;
                        msg.message = message;
                        if (!(message instanceof DisconnectMessage))
                            incomingMessages.add(msg);
                        else {
                            closed = true;
                            outgoingMessages.clear();
                            output.writeObject("* Good Bye *");
                            output.flush();
                            clientDisconnected(playerID);
                            close();
                        }
                    }
                }
                catch (IOException e) {
                    closeWithError("Error while reading data from client.");
                    e.printStackTrace();
                    close();
                }
                catch (Exception e) {
                    closeWithError("Internal Error: Unexpected exception in input thread: " + e);
                    e.printStackTrace();
                    close();
                }
            }
        }
    }
}
