import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Scanner; // import the Scanner class

public class Peer {

    private static InetAddress mediatorIP;
    private static int mediatorTcpDiscussionPort;
    private static int mediatorTcpPunchPort;

    private Socket socketDiscussion, socketClientPunch;
    private ServerSocket socketServerPunch;

    private BufferedReader inDiscussion;
    private BufferedOutputStream outDiscussion;

    private BufferedReader inPunch;
    private BufferedOutputStream outPunch;

    private String message = "";
    private static String sendMessage = "";
    private String[] tokens = null;
    private volatile boolean runningHole;

    private Thread readOnHole, listenOnHole, writeOnHole;
    private static boolean relayModus;

    //Constructor
    public Peer(InetAddress ip, int tcpDiscussionPort, int tcpPunchPort) throws IOException {
        socketDiscussion = createSocket(ip, tcpDiscussionPort);
        socketClientPunch = createSocket(ip, tcpPunchPort);

        this.runningHole = true;
        createInOutputStreamDiscussion(socketDiscussion);
        createInOutputStreamPunch(socketClientPunch);
        readOnHole();
        sendMessage(outPunch, sendMessage);
    }

    private Socket createSocket(InetAddress ip, int port) {
        try {
            System.out.println("attempt to create socket: " + ip + ":" + port);
            Socket socket = new Socket(ip, port);
            System.out.println("created socket: " + socket.getInetAddress() + " localport: " + socket.getLocalPort() + " publicport: " + socket.getPort() + "\n");
            return socket;
        } catch (IOException ex) {
            System.err.println("Exception creating a socket: " + ex);
        }
        return null;
    }

    private Socket createSocket() {
        System.out.println("attempt to create raw socket");
        Socket socket = new Socket();
        System.out.println("created socket: " + socket.getInetAddress());
        return socket;
    }

    private void createServerSocket(int port) {
        System.out.println("attempt to create server socket");
        try {
            socketServerPunch = new ServerSocket(port);
            socketServerPunch.setReuseAddress(true);
            System.out.println("created server socket: " + socketServerPunch.getInetAddress() + " localport: " + socketServerPunch.getLocalPort());
            socketClientPunch = socketServerPunch.accept();
            socketClientPunch.setReuseAddress(true);
            createInOutputStreamPunch(socketClientPunch);
        } catch (Exception e) {
            System.out.println("error while creating server socket: " + e);
            inPunch = null;
            outPunch = null;
        }
    }


    //bind socket so application can use it to send and receive information.
    private void bindSocket(Socket socket, InetSocketAddress address) throws IOException {
        System.out.println("bind socket on adress : " + address);
        try {
            if (!socket.isBound()) {
                while (!socket.isBound()) {
                    socket.bind(address);
                }
            } else {
                System.out.println("socket already bound");
            }
        } catch (BindException be) {
            System.out.println(be.getMessage());
        }
    }

    //Function to directly connect, used to do hole punching. If it fails, switches to relay modus.
    private void connectSocket(Socket socket, InetSocketAddress address) {
        try {
            System.out.println("connect to : " + address);
            socket.connect(address);
            createInOutputStreamPunch(socketClientPunch);
        } catch (IOException e) {
            System.out.println("Connection refused");
            relayModus = true;
            System.out.println("Starting Relay Modus!");
            try {
                readOnHoleRelay();
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }

    private void closeSocket(Socket socket) {
        System.out.println("close socket");
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createInOutputStreamDiscussion(Socket discussionSocket) throws IOException {
        inDiscussion = new BufferedReader(new InputStreamReader(discussionSocket.getInputStream()));
        outDiscussion = new BufferedOutputStream(discussionSocket.getOutputStream());
    }

    public void createInOutputStreamPunch(Socket punchSocket) throws IOException {
        inPunch = new BufferedReader(new InputStreamReader(punchSocket.getInputStream()));
        outPunch = new BufferedOutputStream(punchSocket.getOutputStream());
    }


    //Function to send a message to a peer or server.
    private void sendMessage(BufferedOutputStream stream, String message) throws IOException {
        byte[] sendData = message.getBytes();
        stream.write(sendData);
        stream.write('\n');
        stream.flush();
    }


    //Function to receive information from server.
    private void readOnHole() throws IOException {
        System.out.println("Read on hole");
        this.readOnHole = new Thread(() -> {
            boolean serverResponse = false;
                while (!serverResponse) {
                    try {
                        message = inDiscussion.readLine();
                        System.out.println(message);
                        readTokens(message);
                        serverResponse = true;
                        proceedHolePunching(tokens);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        });
        this.readOnHole.start();
    }


    //Function to receive messages from another peer through server using relay.
    private void readOnHoleRelay() throws IOException {
        System.out.println("Read on hole relay");
        this.readOnHole = new Thread(() -> {
                while (true) {
                    try {
                        message = inDiscussion.readLine();
                        if(message != null) {
                            System.out.println("Received: " + message);
                        }
                        if(message == null) {
                            closeSocket(socketDiscussion);
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        });
        this.readOnHole.start();
    }

    //Function to send messages to another peer through server using relay.
    private void writeDataOnHoleRelay() {
        System.out.println("write data on hole relay");
        this.writeOnHole = new Thread(() -> {
            while (true) {
                try {
                    Scanner myObj = new Scanner(System.in);
                    String msg = myObj.nextLine();
                    sendMessage(outDiscussion, msg);
                } catch (IOException e) {
                    System.err.println("IOException");
                    System.out.println(e);
                } catch (Exception e) {
                    System.err.println("SleepException");
                    System.err.println(e);
                }
            }
        });
        this.writeOnHole.start();
    }






    //Function to read and print the tokens/information from server.
    private void readTokens(String message) {
        System.out.println("read tokens");
        tokens = message.split("~~");  //split response into tokens for IP and Port

        System.out.println("****************************************");
        System.out.println("My PUBLIC IP seen by server: " + tokens[0]);
        System.out.println("My PUBLIC TCP PORT seen by server: " + tokens[1]);
        System.out.println("My LOCAL  TCP PORT seen by server: " + tokens[2]);
        System.out.println("****************************************\n");

        System.out.println("****************************************");
        System.out.println("CLIENT B PUBLIC IP seen by server: " + tokens[3]);
        System.out.println("CLIENT B PUBLIC TCP PORT seen by server: " + tokens[4]);
        System.out.println("CLIENT B LOCAL  TCP PORT seen by server: " + tokens[5]);
        System.out.println("****************************************");
    }

    private void listenConnectionHole(int localPort) {
        System.out.println("Start listen on port : " + localPort);
        new Thread(() -> createServerSocket(localPort)).start();
    }

    //Function to make thread for hole punching so client can receive messages from other client.
    private void listenDataOnHole(InetAddress ip, int port) {
        System.out.println("listen date on hole: " + ip + ":" + port);
        this.listenOnHole = new Thread(() -> {
            while (runningHole) {
                try {
                    message = inPunch.readLine();
                    System.out.println("Received: " + message.trim() + ", From: IP " + ip + " Port " + port);
                } catch (IOException ex) {
                    System.err.println("Error " + ex);
                }
            }
        });
        this.listenOnHole.start();
    }


    //Function to send messages to another peer using hole punching.
    private void writeDataOnHole() {
        System.out.println("write data on hole");
        this.writeOnHole = new Thread(() -> {
            String msg1;
            while (runningHole) {
                try {
                    Scanner myObj = new Scanner(System.in);
                    msg1 = myObj.nextLine();
                    sendMessage(outPunch, msg1);
                } catch (IOException e) {
                    System.err.println("IOException");
                } catch (Exception e) {
                    System.err.println("SleepException");
                }
            }
        });
        this.writeOnHole.start();
    }


    //Get information from server about both clients and try to hole punch.
    private void proceedHolePunching(String[] tokens) throws IOException {
        System.out.println("proceed holepunching");

        InetAddress clientAIp = InetAddress.getByName(tokens[0]);
        int clientAPortPublic = Integer.parseInt(tokens[1]);
        int clientAPortLocal = Integer.parseInt(tokens[2]);
        String clientBIp = InetAddress.getByName(tokens[3]).getHostAddress().trim();
        int clientBPortPublic = Integer.parseInt(tokens[4]);
        int clientBPortLocal = Integer.parseInt(tokens[5]);

        listenConnectionHole(clientAPortLocal);

        if (this.socketClientPunch != null) {
            outPunch = null;
            inPunch = null;

            closeSocket(socketClientPunch);
            connectToPeer(clientAIp, clientAPortPublic, clientAPortLocal, clientBIp, clientBPortPublic, clientBPortLocal);
        }
    }


    //Connect directly to a peer
    private void connectToPeer(InetAddress clientAIp, int clientAPortPublic, int clientAPortLocal, String clientBIp, int clientBPortPublic, int clientBPortLocal) throws IOException {
        System.out.println("connect to peer");
        socketClientPunch = createSocket();
        socketClientPunch.setReuseAddress(true);
        bindSocket(socketClientPunch, new InetSocketAddress(clientAPortLocal));
        bindSocket(socketClientPunch, new InetSocketAddress(clientAPortPublic));

        InetSocketAddress addresLocal = new InetSocketAddress(clientBIp, clientBPortLocal);
        InetSocketAddress addresPublic = new InetSocketAddress(clientBIp, clientAPortPublic);

//        try { Thread.sleep(50000);
//        } catch (InterruptedException e) {
//        e.printStackTrace(); }

        connectSocket(socketClientPunch, addresLocal);

        if (outPunch != null && inPunch != null) {
            System.out.println("Punch: Connected to : " + clientBIp + ":" + clientBPortLocal);
            listenDataOnHole(clientAIp, clientAPortLocal);
            writeDataOnHole();
        } else {
            System.err.println("Error when attempting to connect to peer, switching to relay!");
            writeDataOnHoleRelay();
        }
    }


    //main method to check for args.
    public static void main(String[] args) throws IOException {
        if (args.length == 4) {
            sendMessage = args[3];
            clientSetup(InetAddress.getByName(args[0].trim()), Integer.parseInt(args[1].trim()), Integer.parseInt(args[2].trim()));
        } else if (args.length == 3) {
            clientSetup(InetAddress.getByName(args[0].trim()), Integer.parseInt(args[1].trim()), Integer.parseInt(args[2].trim()));
        } else if (args.length == 1) {
            sendMessage = args[0];
            clientSetup(InetAddress.getByName("127.0.0.1"), 9000, 9001);
        } else {
            clientSetup(InetAddress.getByName("127.0.0.1"), 9000, 9001);
        }
    }

    //Function to setup a client/peer
    private static void clientSetup(InetAddress clientIp, int discussionPort, int punchPort) {
        try {
            System.out.println("Client A running with IP: " + clientIp + " DiscussionPort: " + discussionPort + " PunchPort: " + punchPort);
            mediatorIP = clientIp;
            mediatorTcpDiscussionPort = discussionPort;
            mediatorTcpPunchPort = punchPort;
            new Peer(mediatorIP, mediatorTcpDiscussionPort, mediatorTcpPunchPort);
        } catch (Exception ex) {
            System.err.println("error with connecting to server, exiting");
            System.exit(0);
        }
    }
}
