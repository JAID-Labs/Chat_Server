package services;

import handlers.ElectionThreadHandler;
import handlers.LeaderUpdateThreadHandler;
import handlers.ClientThreadHandler;
import models.Server;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import models.CurrentServer;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import static constants.Constant.REPLY_HEARTBEAT;
import static util.Utils.send;

public class CoordinationServices {

    private LeaderUpdateThreadHandler leaderUpdateThreadHandler;
    private CurrentServer currentServer;

    private CoordinationServices() {
        this.leaderUpdateThreadHandler = new LeaderUpdateThreadHandler();
        this.currentServer = CurrentServer.getInstance();
    }

    private static CoordinationServices instance;

    public static CoordinationServices getInstance() {
        if (instance == null) {
            synchronized (LeaderServices.class) {
                if (instance == null) {
                    instance = new CoordinationServices();
                }
            }
        }
        return instance;
    }

    public void replyNewIdentityApproval(Long threadID, boolean approved) {
        ClientThreadHandler clientThreadHandler = CurrentServer.getInstance().getClientHandlerThread(threadID);

        if (approved) {
            clientThreadHandler.getClientServices().setApprovedClientID(1);
        }
        else {
            clientThreadHandler.getClientServices().setApprovedClientID(0);
        }

        Object lock = clientThreadHandler.getClientServices().getLock();
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public void replyCreateRoomApproval(Long threadID, boolean approved) {
        ClientThreadHandler clientThreadHandler = CurrentServer.getInstance().getClientHandlerThread(threadID);

        if (approved) {
            clientThreadHandler.getClientServices().setApprovedRoomCreation(1);
        }
        else {
            clientThreadHandler.getClientServices().setApprovedRoomCreation(0);
        }

        Object lock = clientThreadHandler.getClientServices().getLock();
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public void replyJoinRoomApproval(boolean approved, Long threadID, String host, String port) {
        int intApproved;
        if (approved) {
            intApproved = 1;
        } else {
            intApproved = 0;
        }

        ClientThreadHandler clientThreadHandler = CurrentServer.getInstance().getClientHandlerThread(threadID);

        Object lock = clientThreadHandler.getClientServices().getLock();

        synchronized (lock) {
            clientThreadHandler.getClientServices().setApprovedJoinRoom(intApproved);
            clientThreadHandler.getClientServices().setApprovedJoinRoomServerHostAddress(host);
            clientThreadHandler.getClientServices().setApprovedJoinRoomServerPort(port);
            lock.notifyAll();
        }
    }

    public void replyList(Long threadID, JSONArray roomsJSONArray) {
        ArrayList<String> roomIDs = new ArrayList();
        roomIDs.addAll(roomsJSONArray);

        ClientThreadHandler clientThreadHandler = CurrentServer.getInstance().getClientHandlerThread(threadID);

        Object lock = clientThreadHandler.getClientServices().getLock();

        synchronized (lock) {
            clientThreadHandler.getClientServices().setRoomsList(roomIDs);
            lock.notifyAll();
        }
    }

    public void leaderUpdate(JSONObject data) {
        if(LeaderServices.getInstance().isLeaderElectedAndIamLeader()) {
            if(!leaderUpdateThreadHandler.isAlive()) {
                leaderUpdateThreadHandler = new LeaderUpdateThreadHandler();
                leaderUpdateThreadHandler.start();
            }
            leaderUpdateThreadHandler.receiveUpdate(data);
        }
    }

    public void leaderUpdateComplete(int serverID) {
        if(LeaderServices.getInstance().isLeaderElectedAndMessageFromLeader(serverID)) {
            System.out.println("Received leader update complete message from s"+serverID);
            ElectionThreadHandler.leaderUpdateComplete = true;
        }
    }

    public void replyHeartbeat (int serverid){
        JSONObject replyHeartbeat = new JSONObject();
        replyHeartbeat.put("type", REPLY_HEARTBEAT);
        replyHeartbeat.put("serverid", CurrentServer.getInstance().getServerIntID());

        try {
            Server sender = currentServer.getServers().get(serverid);
            Socket destinationSocket = new Socket(sender.getServerAddress(), sender.getCoordinationPort());

            send(replyHeartbeat, destinationSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void markHeartbeat(int serverid){
        CurrentServer.getInstance().getHeartbeatCountMap().put(serverid, 0);
        CurrentServer.getInstance().getServers().get(serverid).setActive(true);
        System.out.println("Server s" + serverid + " is marked");
        System.out.println("Server s" + serverid + " is up");
    }
}
