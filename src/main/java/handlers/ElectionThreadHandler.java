package handlers;

import org.json.simple.JSONArray;
import models.Server;
import org.json.simple.JSONObject;
import models.CurrentServer;
import services.LeaderServices;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

import static constants.Constant.*;
import static util.Utils.send;
import static util.Utils.sendLeader;

public class ElectionThreadHandler implements Runnable {
    public String operation;
    public String reqType;
    public static int sourceID=-1;
    public static volatile boolean receivedOk = false;
    public static volatile boolean leaderFlag = false;
    public static volatile boolean electionInProgress = false;

    public static volatile boolean leaderUpdateComplete = false;

    public ElectionThreadHandler(String operation) {
        this.operation = operation;
    }

    public ElectionThreadHandler(String operation, String reqType ) {
        this.operation = operation;
        this.reqType = reqType;
    }

    public void run() {
        switch (operation) {
            case TIMER -> {
                System.out.println("Timer waits and checking for servers.......");
                try {
                    Thread.sleep(6000);
                    if(!receivedOk) {
                        LeaderServices.getInstance().setLeaderID(CurrentServer.getInstance().getServerIntID());
                        electionInProgress = false;
                        leaderFlag = true;
                        System.out.println("Server s" + LeaderServices.getInstance().getLeaderID() + " is the leader now! ");
                        LeaderServices.getInstance().resetLeader();

                        Runnable sender = new ElectionThreadHandler( "Sender", "coordinator" );
                        new Thread( sender ).start();
                    }

                    if( receivedOk && !leaderFlag ) {
                        System.out.println( "Received OK and waiting for coordinator message" );

                        electionInProgress = false;
                        receivedOk = false;

                        Runnable sender = new ElectionThreadHandler( "Sender", "election" );
                        new Thread(sender).start();
                    }
                }
                catch( Exception e ) {
                    System.out.println("Timer Thread exception");
                }
            }

            case HEARTBEAT -> {
                while( true ) {
                    try {
                        Thread.sleep(10);
                        if(leaderFlag && CurrentServer.getInstance().getServerIntID() != LeaderServices.getInstance().getLeaderID()) {
                            Thread.sleep( 1500 );

                            JSONObject message = new JSONObject();
                            message.put("option", QUARTZ_JOBS);
                            message.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));

                            sendLeader(message);

                            System.out.println("Heartbeat checking for leader s" + LeaderServices.getInstance().getLeaderID());
                        }
                    }

                    catch( Exception e ) {
                        leaderFlag = false;
                        leaderUpdateComplete = false;
                        System.out.println("LEADER HAS FAILED");
                        Runnable sender = new ElectionThreadHandler(SENDER, ELECTION);
                        new Thread(sender).start();
                    }
                }
            }

            case SENDER -> {
                switch(reqType) {
                    case ELECTION -> {
                        try {
                            sendElectionRequest();
                        } catch (Exception e) {
                            System.out.println("Election request abandoned");
                        }
                    }

                    case OK -> {
                        try {
                            sendOK();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    case COORDINATOR -> {
                        try {
                            sendCoordinatorMsg();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static void sendCoordinatorMsg() {
        int numberOfRequestsNotSent = 0;
        for (int key : CurrentServer.getInstance().getServers().keySet()) {
            if (key != CurrentServer.getInstance().getServerIntID()){
                Server destinationServer = CurrentServer.getInstance().getServers().get(key);

                JSONObject message = new JSONObject();
                message.put("option", COORDINATOR);
                message.put("leader", String.valueOf(CurrentServer.getInstance().getServerIntID()));

                try {
                    Socket destinationSocket = new Socket(destinationServer.getServerAddress(), destinationServer.getCoordinationPort());
                    send(message, destinationSocket);
                    System.out.println("Sending..... leader ID to s"+destinationServer.getServerID());
                }
                catch(Exception e) {
                    numberOfRequestsNotSent += 1;
                    System.out.println("Server s"+destinationServer.getServerID()+ " not responding to leader update....");
                }
            }
        }
        if( numberOfRequestsNotSent == CurrentServer.getInstance().getServers().size()-1 ) {
            List<String> selfClients = CurrentServer.getInstance().getClientIdList();
            List<List<String>> selfRooms = CurrentServer.getInstance().getChatRoomList();

            for( String clientID : selfClients ) {
                LeaderServices.getInstance().addClientLeaderUpdate( clientID );
            }

            for( List<String> chatRoom : selfRooms ) {
                LeaderServices.getInstance().addApprovedRoom( chatRoom.get( 0 ),
                        chatRoom.get( 1 ), Integer.parseInt(chatRoom.get( 2 )) );
            }

            leaderUpdateComplete = true;
        }
    }

    public static void sendOK() {
        try {
            Server destinationServer = CurrentServer.getInstance().getServers().get(sourceID);

            JSONObject message = new JSONObject();
            message.put("option", OK);
            message.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));

            Socket destinationSocket = new Socket(destinationServer.getServerAddress(), destinationServer.getCoordinationPort());

            send(message, destinationSocket);

            System.out.println("Sending......  OK to s"+destinationServer.getServerID());
        }
        catch(Exception e) {
            System.out.println("OK message failed, Server s" + sourceID +  "not responding...." );
        }
    }

    public static void sendElectionRequest() {
        System.out.println("***************Election started***************");
        int numberOfFailedRequests = 0;
        for ( int key : CurrentServer.getInstance().getServers().keySet() ) {
            if( key > CurrentServer.getInstance().getServerIntID() ){
                Server destServer = CurrentServer.getInstance().getServers().get(key);

                JSONObject message = new JSONObject();
                message.put("option", ELECTION);
                message.put("source", String.valueOf(CurrentServer.getInstance().getServerIntID()));

                try {
                    Socket destinationSocket = new Socket(destServer.getServerAddress(), destServer.getCoordinationPort());

                    send(message, destinationSocket);
                    System.out.println("Election request to server s"+destServer.getServerID());
                }
                catch(Exception e){
                    numberOfFailedRequests++;
                    System.out.println("Election request can't send,Server s"+destServer.getServerID() + "failed");
                }
            }

        }
        if (numberOfFailedRequests == CurrentServer.getInstance().getNumberOfServersWithHigherIds()) {
            if(!electionInProgress){
                electionInProgress = true;
                receivedOk = false;
                Runnable timer = new ElectionThreadHandler("Timer");
                new Thread(timer).start();
            }
        }
    }

    public static void receiveMessages(JSONObject data) {
        String option = data.get("option").toString();
        switch(option) {
            case ELECTION -> {
                sourceID = Integer.parseInt(data.get("source").toString());
                System.out.println("Election request received from s" + sourceID);

                if (CurrentServer.getInstance().getServerIntID() > sourceID) {
                    Runnable sender = new ElectionThreadHandler(SENDER, OK);
                    new Thread(sender).start();
                }
                if (!electionInProgress) {
                    Runnable sender = new ElectionThreadHandler(SENDER, ELECTION);
                    new Thread(sender).start();
                    electionInProgress = true;

                    Runnable timer = new ElectionThreadHandler(TIMER);
                    new Thread(timer).start();
                    System.out.println("Election started by intermediate server....");
                }
            }

            case OK -> {
                receivedOk = true;
                int senderID = Integer.parseInt(data.get("sender").toString());
                System.out.println( "Received OK from s" + senderID + "...leader already in the system" );
            }

            case COORDINATOR -> {
                LeaderServices.getInstance().setLeaderID(
                        Integer.parseInt(data.get("leader").toString()));
                leaderFlag = true;
                leaderUpdateComplete = false;
                electionInProgress = false;
                receivedOk = false;
                System.out.println("Server s" + LeaderServices.getInstance().getLeaderID() + " is selected as leader! ");

                JSONArray clients = new JSONArray();
                clients.addAll(CurrentServer.getInstance().getClientIdList());

                JSONArray chatRooms = new JSONArray();
                for (List<String> chatRoomObj : CurrentServer.getInstance().getChatRoomList()) {
                    JSONObject chatRoom = new JSONObject();
                    chatRoom.put("clientid", chatRoomObj.get(0));
                    chatRoom.put("roomid", chatRoomObj.get(1));
                    chatRoom.put("serverid", chatRoomObj.get(2));
                    chatRooms.add(chatRoom);
                }

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type", LEADER_UPDATE);
                jsonObject.put("clients", clients);
                jsonObject.put("chatrooms", chatRooms);

                try {
                    sendLeader(jsonObject);
                } catch (IOException e) {
                    System.out.println("Updating new leader fails....");
                }
                break;
            }

            case QUARTZ_JOBS -> {
                int senderID = Integer.parseInt(data.get( "sender" ).toString());
            }
        }
    }

    public static void initialize() {
        System.out.println("New server "+ CurrentServer.getInstance().getServerIntID()+"joins and starts election....");
        Runnable sender = new ElectionThreadHandler("Sender",ELECTION);
        new Thread(sender).start();
    }
}
