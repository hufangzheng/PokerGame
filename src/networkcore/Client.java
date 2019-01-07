package networkcore;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.ExportException;
import java.util.concurrent.LinkedBlockingQueue;

abstract public class Client {

    protected int[] connectedPlayerIDs = new int[0];
    private volatile boolean autoreset;
    private final ConnectToServer connection;

    public Client(String hostIPAddress, int hostPort) throws IOException{
        connection = new ConnectToServer(hostIPAddress, hostPort);
    }

    abstract protected void messageReceived(Object message);

    protected void playerConnected(int newPlayerID) { }

    protected void playerDisconnected(int departingPlayerID) { }

    protected void connectionClosedByError(String message) { }

    protected void serverShutdown(String message) { }

    protected void extraHandshake(ObjectInputStream input, ObjectOutputStream output)
    throws IOException { }

    public void disconnect() {
        if (!connection.closed)
            connection.send(new DisconnectMessage("Good Bye"));
    }

    public void send(Object message) {
        if (message == null)
            throw new IllegalArgumentException("Null cannot be sent as a message.");
        if (! (message instanceof Serializable) )
            throw new IllegalArgumentException("Messages must implement the Serializable interface.");
        if (connection.closed)
            throw new IllegalStateException("Message cannot be sent because the connection is closed.");
        connection.send(message);
    }

    public int getID() {
        return connection.playerID;
    }

    /**
     * This private class handles the actual communication with the server.
     */
    private class ConnectToServer {

        private final int playerID;
        private final Socket socket;
        private final ObjectInputStream input;
        private final ObjectOutputStream output;
        private final SendThread sendThread;
        private final ReceiveThread receiveThread;

        private final LinkedBlockingQueue<Object> outgoingMessages;

        private volatile boolean closed;

        ConnectToServer(String host, int port) throws IOException {
            outgoingMessages = new LinkedBlockingQueue<Object>();
            socket = new Socket(host, port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.writeObject("Hello Server");
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            try {
                Object response = input.readObject();
                playerID = ((Integer)response).intValue();
            }
            catch (Exception e) {
                throw new IOException("Illegal response from server.");
            }
            sendThread = new SendThread();
            receiveThread = new ReceiveThread();
            sendThread.start();
            receiveThread.start();
        }

        void close() {
            closed = true;
            sendThread.interrupt();
            receiveThread.interrupt();
            try {
                socket.close();
            }
            catch (IOException e) {
                System.out.println("An error occurred while close ConnectToServer.");
            }
        }

        void send(Object message) {
            outgoingMessages.add(message);
        }

        synchronized void closedByError(String message) {
            if ( ! closed ) {
                connectionClosedByError(message);
                close();
            }
        }

        private class SendThread extends Thread {
            public void run() {
                try {
                    while ( ! closed ) {
                        Object message = outgoingMessages.take();
                        if (message instanceof ResetSignal) {
                            output.reset();
                        }
                        else {
                            if (autoreset)
                                output.reset();
                            output.writeObject(message);
                            output.flush();
                            if (message instanceof  DisconnectMessage) {
                                close();
                            }
                        }
                    }
                }
                catch (IOException e) {
                    if ( ! closed ) {
                        closedByError("IO error occurred while trying to send message.");
                        System.out.println("Client send thread terminated by IOException: " + e);
                    }
                }
                catch (Exception e) {
                    if ( ! closed ) {
                        closedByError("Unexpected internal error in send thread: " + e);
                        System.out.println("\nUnexpected error shuts down client send thread:");
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * This class defines a thread that reads messages from the Hub.
         */
        private class ReceiveThread extends Thread {
            public void run() {
                try {
                    while ( ! closed ) {
                        Object obj = input.readObject();
                        if (obj instanceof DisconnectMessage) {
                            close();
                            serverShutdown(((DisconnectMessage)obj).message);
                        }
                        else if (obj instanceof StatusMessage) {
                            StatusMessage msg = (StatusMessage)obj;
                            connectedPlayerIDs = msg.players;
                            if (msg.connecting)
                                playerConnected(msg.playerID);
                            else
                                playerDisconnected(msg.playerID);
                        }
                    }
                }
                catch (IOException e) {
                    if ( ! closed ) {
                        closedByError("IO error occurred while waiting to receive  message.");
                        System.out.println("Client receive thread terminated by IOException: " + e);
                    }
                }
                catch (Exception e) {
                    if ( ! closed ) {
                        closedByError("Unexpected internal error in receive thread: " + e);
                        System.out.println("\nUnexpected error shuts down client receive thread:");
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
