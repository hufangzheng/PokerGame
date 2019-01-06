package networkcore;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    }









    private class Message {
        ConnectionToClient playerConnection;
        Object message;
    }

    synchronized private void connectionToClientClosedWithError(ConnectionToClient playerConnection, String message) {
        int ID = playerConnection.getPlayerID();
        if (playerConnections.remove(ID) != null) {
            StatusMessage sm = new StatusMessage(ID, false, getPlayerList());
            sendToAll(sm);
        }
    }

    private class ServerThread extends Thread {
        public void run() {
            try {
                while ( !shutdown) {
                    Socket connection = serverSocket.accept();
                    if (shutdown) {
                        System.out.println("Listener socket has shut down.");
                        break;
                    }
                    new ConnectionToClient(incomingMessage, connection);
                }
            }

        }
    }

    /* Handles communication with one client. */
    private class ConnectionToClient {

        private int playerID;
        private BlockingQueue<Message> incomingMessages;
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


            }
        }
    }
}
