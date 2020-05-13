/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author user
 */
public class ServerMediator {

    //Ports for exchanging information/relay and punching.
    private int tcpDiscussionPort = 9000;
    private int tcpPunchPort = 9001;

    private BufferedReader inConnectA, inPunchA;
    private BufferedOutputStream outConnectA, outPunchA;

    private BufferedReader inConnectB, inPunchB;
    private BufferedOutputStream outConnectB, outPunchB;

    private ServerSocket socketConnect, socketPunch;

    private Socket clientAConnect, clientAPunch, clientBConnect, clientBPunch;


    private boolean readClientA = false;
    private String clientAIp = "";
    private String clientAPort = "";
    private String clientAPortLocal = "";

    private boolean readClientB = false;
    private String clientBIp = "";
    private String clientBPort = "";
    private String clientBPortLocal = "";
    private String lastMessageA = "";
    private String lastMessageB = "";
    private Thread A; //Thread voor peerA
    private Thread B; //Thread voor peerB

    //Constructor using default tcp discussion/punch ports
    public ServerMediator() {
        try {
            runServer();
        } catch (IOException ex) {
            Logger.getLogger(ServerMediator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Constructor specify tcp discussion/punch ports
    public ServerMediator(int userTcpPort, int userUdpPort) {
        this.tcpDiscussionPort = userTcpPort;
        this.tcpPunchPort = userUdpPort;
        try {
            runServer();
        } catch (IOException ex) {
            Logger.getLogger(ServerMediator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {//Give args
            new ServerMediator(Integer.parseInt(args[0].trim()), Integer.parseInt(args[1].trim()));
        } else {//Give no args
            new ServerMediator();
        }
    }

    //Run server listening clients
    void runServer() throws IOException {
        //Create Server Socket for accepting Client TCP connections

        System.out.println("Server started with ports, TCP connection: " + tcpDiscussionPort + " TCP: " + tcpPunchPort);
        //Thread voor client gegevens
        runDiscussionServer();

        runPunchServer();
    }

    //Thread to connect the clients and get information from them
    private void runDiscussionServer(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    socketConnect = new ServerSocket(tcpDiscussionPort);

                    System.out.println("Waiting for Client A");

                    //Accept first client connection
                    clientAConnect = socketConnect.accept();
                    System.out.println("Client 1 connected " + clientAConnect.getInetAddress() + " " + clientAConnect.getPort());

                    //Create input and output streams to read/write messages for CLIENT A
                    inConnectA = new BufferedReader(new InputStreamReader(clientAConnect.getInputStream()));
                    outConnectA = new BufferedOutputStream(clientAConnect.getOutputStream());

                    System.out.println("Waiting for Client B");

                    //Accept second client connection
                    clientBConnect = socketConnect.accept();
                    System.out.println("Client 2 connected " + clientBConnect.getInetAddress() + " " + clientBConnect.getPort());

                    //Create input and output streams to read/write messages for CLIENT B
                    inConnectB = new BufferedReader(new InputStreamReader(clientBConnect.getInputStream()));
                    outConnectB = new BufferedOutputStream(clientBConnect.getOutputStream());
                }catch (IOException ioe){
                    ioe.printStackTrace();
                }
            }
        }).start();
    }


    //Thread for punching to happen, clients make another connection to the server.
    private void runPunchServer(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    socketPunch = new ServerSocket(tcpPunchPort);

                    System.out.println("Waiting for Client A punch");

                    //Accept first client connection and save information
                    clientAPunch = socketPunch.accept();
                    clientAIp = ((InetSocketAddress)clientAPunch.getRemoteSocketAddress()).getAddress().getHostAddress().trim();
                    clientAPortLocal = String.valueOf(clientAPunch.getPort());
                    clientAPort = String.valueOf(clientAPunch.getLocalPort());

                    System.out.println("Client A punch " + clientAPunch.getInetAddress() + " " + clientAPunch.getPort());

                    //Create input and output streams to read/write messages for CLIENT A
                    inPunchA = new BufferedReader(new InputStreamReader(clientAPunch.getInputStream()));
                    outPunchA = new BufferedOutputStream(clientAPunch.getOutputStream());


                    System.out.println("Waiting for Client B punch");
                    //Accept second client connection and save information
                    clientBPunch = socketPunch.accept();
                    clientBIp = ((InetSocketAddress)clientBPunch.getRemoteSocketAddress()).getAddress().getHostAddress().trim();
                    clientBPortLocal = String.valueOf(clientBPunch.getPort());
                    clientBPort = String.valueOf(clientBPunch.getLocalPort());

                    System.out.println("Client 2 punch " + clientBPunch.getInetAddress() + " " + clientBPunch.getPort());

                    //Create input and output streams to read/write messages for CLIENT B
                    inPunchB = new BufferedReader(new InputStreamReader(clientBPunch.getInputStream()));
                    outPunchB = new BufferedOutputStream(clientBPunch.getOutputStream());


                    //Once the two clients have punched
                    proceedInfosExchange();
                }catch (IOException ioe){
                    ioe.printStackTrace();
                }
            }
        }).start();
    }

    //Wissel gegevens
    private void proceedInfosExchange() throws IOException{
        /**
         *
         * *** FIRST CLIENT'S PUBLIC IP AND PORTS ****
         *///read initial punch message to see if its client A.
        while (!readClientA) {
            String message = inPunchA.readLine();
            System.out.println(message);
            if (message.trim().equals("one")) {
                readClientA = true;
                System.out.println("Initial punch message from CLIENT A: " + message);
            }
        }

        System.out.println("******CLIENT A IP AND PORT DETECTED " + clientAIp + ":" +  clientAPortLocal + "->" + clientAPort + " *****");

        /**
         *
         * *** SECOND CLIENT'S PUBLIC IP AND PORTS ****
         *///read initial message to see if its client B.
        while (!readClientB) {
            String message = inPunchB.readLine();   //Get Data from tcp packet into a string
            if (message.trim().equals("two")) {
                readClientB = true;
                System.out.println("Initial punch message from CLIENT B: " + message);
            }
        }

        System.out.println("******CLIENT B IP AND PORT DETECTED " + clientBIp + ":" +  clientBPortLocal + "->" + clientBPort + " *****");
        System.out.println("***** Exchanging public IP and port between the clients OR DOING RELAY *****");
        boolean sent = false; //Boolean to check if public IP and port has been exchanged.
        while (true) {
                //Send client info once and start up threads for relay.
                if(!sent) {
                    //SENDING CLIENT B's public IP & PORT TO CLIENT A
                    String string = clientAIp + "~~" + clientAPort + "~~" + clientAPortLocal + "~~" + clientBIp + "~~" + clientBPort + "~~" + clientBPortLocal;
                    outConnectA.write(string.getBytes());
                    outConnectA.write('\n');
                    outConnectA.flush();
                    //SENDING CLIENT A's public IP & PORT TO CLIENT B
                    String string1 = clientBIp + "~~" + clientBPort + "~~" + clientBPortLocal + "~~" + clientAIp + "~~" + clientAPort + "~~" + clientAPortLocal;
                    outConnectB.write(string1.getBytes());
                    outConnectB.write('\n');
                    outConnectB.flush();

                    //Make threads for peers.
                    makePeerA();
                    makePeerB();
                    sent = true;
                }
        }

    }

    //Make thread voor PeerA, function to send message from A to B using relay!
    private void makePeerA() throws IOException {
        this.A = new Thread(() -> {
            while (true) {
                try {
                    String message = inConnectA.readLine(); // Message from clientA
                    if(message != null) {
                        System.out.println(message);
                        outConnectB.write(message.getBytes());     //Send message to peerA
                        outConnectB.write('\n');
                        outConnectB.flush(); //empty buffer
                    }
                } catch (IOException e ) {
                    System.out.println(e);
                }
            }
        });
        this.A.start();
    }


    //Make thread voor PeerB, function to send message from B to A using relay!
    private void makePeerB() throws IOException {
        this.B = new Thread(() -> {
            while (true) {
                try {
                    String message2 = inConnectB.readLine(); // Message from clientB
                    if(message2 != null){
                        System.out.println(message2);
                        outConnectA.write(message2.getBytes());      //Send message to peerB
                        outConnectA.write('\n');
                        outConnectA.flush(); //empty buffer
                    }
                } catch (IOException e ) {
                    System.out.println(e);
                }
            }
        });
        this.B.start();
    }


}
