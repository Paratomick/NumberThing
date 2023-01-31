package main;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public abstract class NetworkConnection {

    private List<ConnectionThread> connThreads = new LinkedList<>();
    private Consumer<Serializable> onReceiveCallback;

    private ServerSocket serverSocket;

    public NetworkConnection(Consumer<Serializable> onReceiveCallback) {
        this.onReceiveCallback = onReceiveCallback;
    }

    public void startConnection() throws Exception {
        if(isServer()) {
            Runnable serverThread = () -> {
                try {
                    this.serverSocket = new ServerSocket(getPort());

                    while(true) {

                        Socket socket = serverSocket.accept();

                        ConnectionThread cT = new ConnectionThread(socket);
                        cT.setDaemon(true);
                        System.out.println("Connection from: " + cT.socket.getInetAddress());
                        cT.start();
                        connThreads.add(cT);
                        if(isServer()) onReceiveCallback.accept("MEMBER-"+connThreads.size());
                    }
                } catch (Exception e) {
                    System.out.println("Action: Server gestoppt!");
                }
            };
            new Thread(serverThread).start();
        } else {
            ConnectionThread cT = new ConnectionThread();
            cT.setDaemon(true);
            cT.start();
            connThreads.add(cT);
        }
    }

    public boolean available(int id) {
        if(connThreads != null && connThreads.get(id).out != null) return true;
        return false;
    }

    public void send(Serializable data) throws Exception {
        for(ConnectionThread cT: connThreads)
            cT.out.writeObject(data);
    }

    public void closeConnection() throws Exception {
        if(serverSocket != null)
            serverSocket.close();
        for(ConnectionThread cT: connThreads)
            if(cT != null && cT.socket != null)
                cT.socket.close();
    }

    protected abstract boolean isServer();
    protected abstract String getIP();
    protected abstract int getPort();

    private class ConnectionThread extends Thread {

        private Socket socket;
        private ObjectOutputStream out;

        public ConnectionThread() {
        }

        public ConnectionThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try(Socket socket = isServer() ? this.socket : new Socket(getIP(), getPort());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                this.out = out;
                socket.setTcpNoDelay(true);

                while(true) {

                    Serializable data = (Serializable) in.readObject();
                    onReceiveCallback.accept(data);
                }

            } catch (Exception e) {
                System.out.println("Action: Verbindung zu einem Client geschlossen!");
                connThreads.remove(this);
                if(isServer()) onReceiveCallback.accept("MEMBER-"+connThreads.size());
                else onReceiveCallback.accept("CONNECTIONLOST");
            }
        }
    }
}
