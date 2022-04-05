package services;

import daos.ClientDao;
import daos.RoomDao;
import models.Client;
import handlers.ElectionThreadHandler;
import models.Room;
import models.CurrentServer;
import models.Server;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static constants.Constant.*;
import static util.Utils.send;

public class LeaderServices {
    private Integer leaderID;
    private ClientDao clientDao;
    private RoomDao roomDao;

    private LeaderServices() {
        clientDao = ClientDao.getInstance();
        roomDao = RoomDao.getInstance();
    }

    private static LeaderServices leaderServicesInstance;

    public static LeaderServices getInstance() {
        if (leaderServicesInstance == null) {
            synchronized (LeaderServices.class) {
                if (leaderServicesInstance == null) {
                    leaderServicesInstance = new LeaderServices();
                }
            }
        }
        return leaderServicesInstance;
    }

    public boolean isLeaderElected() {
        return ElectionThreadHandler.leaderFlag && ElectionThreadHandler.leaderUpdateComplete;
    }

    public boolean isLeaderElectedAndIamLeader() {
        return (ElectionThreadHandler.leaderFlag && CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID());
    }

    public boolean isLeaderElectedAndMessageFromLeader(int serverID) {
        return (ElectionThreadHandler.leaderFlag && serverID == LeaderServices.getInstance().getLeaderID());
    }

    public boolean isClientRegistered(String clientID) {
        return clientDao.getClients().contains(clientID);
    }

    public void resetLeader() {
        clientDao.getClients().clear();
        roomDao.getChatRooms().clear();
    }

    public void addClient(@NotNull Client client) {
        clientDao.getClients().add(client.getClientID());
        roomDao.getChatRooms().get(client.getRoomID()).addParticipants(client);
    }

    public void addClientLeaderUpdate(String clientID) {
        clientDao.getClients().add(clientID);
    }

    public void removeClient(String clientID, String formerRoomID) {
        clientDao.getClients().remove(clientID);
        roomDao.getChatRooms().get(formerRoomID).removeParticipants(clientID);
    }

    public void localJoinRoomClient(@NotNull Client client, String formerRoomID) {
        removeClient(client.getClientID(), formerRoomID);
        addClient(client);
    }

    public boolean isRoomCreated(String roomID) {
        return roomDao.getChatRooms().containsKey(roomID);
    }

    public void addApprovedRoom(String clientID, String roomID, int serverID) {
        Room room = new Room(clientID, roomID, serverID);
        roomDao.getChatRooms().put(roomID, room);

        Client client = new Client(clientID, roomID, null);
        client.setRoomOwner(true);
        room.addParticipants(client);
    }

    public void removeRoom(String roomID, String mainHallID, String ownerID) {
        ConcurrentHashMap<String, Client> formerClientStateMap = roomDao.getChatRooms().get(roomID).getParticipantsMap();
        Room mainHall = roomDao.getChatRooms().get(mainHallID);

        formerClientStateMap.forEach((clientID, client) -> {
            client.setRoomID(mainHallID);
            mainHall.getParticipantsMap().put(client.getClientID(), client);
        });

        formerClientStateMap.get(ownerID).setRoomOwner(false);
        roomDao.getChatRooms().remove(roomID);
    }

    public int getServerIdIfRoomExist(String roomID) {
        if (roomDao.getChatRooms().containsKey(roomID)) {
            Room targetRoom = roomDao.getChatRooms().get(roomID);
            return targetRoom.getServerID();
        } else {
            return -1;
        }
    }

    public Integer getLeaderID() {
        return leaderID;
    }

    public void setLeaderID(int leaderID) {
        this.leaderID = leaderID;
    }

    public ArrayList<String> getRoomIDList() {
        return new ArrayList<>(roomDao.getChatRooms().keySet());
    }

    public List<String> getClientIDList() {
        return clientDao.getClients();
    }

    public void requestNewIdentityApproval(String clientID, int sender, String threadID){

        try {
            JSONObject reply = new JSONObject();
            reply.put("type", REPLY_NEW_IDENTITY_APPROVAL);
            reply.put("threadid", threadID);

            if (LeaderServices.getInstance().isClientRegistered(clientID)) {
                reply.put("approved", "false");

                System.out.println(clientID + " from server s" + sender + " is not " + "approved");
            }
            else {
                Client client = new Client(clientID, CurrentServer.getMainHallIDbyServerInt(sender), null);
                LeaderServices.getInstance().addClient(client);
                reply.put("approved", "true");

                System.out.println(clientID + " from server s" + sender + " is " + "approved");
            }

            Server destinationServer = CurrentServer.getInstance().getServers().get(sender);

            // send approval reply to sender
            Socket destinationSocket = new Socket(destinationServer.getServerAddress(), destinationServer.getCoordinationPort());

            send(reply, destinationSocket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void requestCreateRoomApproval (String clientID, String roomID, int sender, String threadID) {
        try {
            JSONObject reply = new JSONObject();
            reply.put("type", REPLY_CREATE_ROOM_APPROVAL);
            reply.put("threadid", threadID);

            if (LeaderServices.getInstance().isRoomCreated(roomID)) {
                reply.put("approved", "false");
                System.out.println(roomID + " room creation request is not approved");
            } else {
                LeaderServices.getInstance().addApprovedRoom(clientID, roomID, sender);
                reply.put("approved", "true");
                System.out.println(roomID + " room creation request is approved");
            }

            Server destinationServer = CurrentServer.getInstance().getServers().get(sender);

            Socket destinationSocket = new Socket(destinationServer.getServerAddress(), destinationServer.getCoordinationPort());

            send(reply, destinationSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void requestJoinRoomApproval(
            String clientID,
            String roomID,
            String formerRoomID,
            int sender,
            String threadID,
            boolean isLocalRoomChange
    ) {

        if (isLocalRoomChange) {
            Client client = new Client(clientID, roomID, null);
            LeaderServices.getInstance().localJoinRoomClient(client, formerRoomID);
        }
        else {
            try {
                int roomServerID = LeaderServices.getInstance().getServerIdIfRoomExist(roomID);

                Server destinationServer = CurrentServer.getInstance().getServers().get(sender);
                Server roomServer = CurrentServer.getInstance().getServers().get(roomServerID);

                JSONObject reply = new JSONObject();
                reply.put("type", REPLY_JOIN_ROOM_APPROVAL);
                reply.put("threadid", threadID);

                if (roomServerID != -1) {
                    LeaderServices.getInstance().removeClient(clientID, formerRoomID);
                    reply.put("approved", "true");
                    reply.put("host", roomServer.getServerAddress());
                    reply.put("port", String.valueOf(roomServer.getClientsPort()));

                    System.out.println(roomID + " room join is approved");
                }
                else {
                    reply.put("approved", "false");
                    reply.put("host", "");
                    reply.put("port", "");

                    System.out.println(roomID + " room join is not approved");
                }

                Socket destinationSocket = new Socket(destinationServer.getServerAddress(), destinationServer.getCoordinationPort());
                send(reply, destinationSocket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void ackMoveJoin(String clientID, String roomID, int sender) {
        Client client = new Client(clientID, roomID, null);
        LeaderServices.getInstance().addClient(client);

        System.out.println(clientID + " client move to to " + roomID +  " room of server s" + sender);
    }

    public void requestList(String threadID, int sender) {
        try {
            JSONObject reply = new JSONObject();
            reply.put("type", REPLY_LIST);
            reply.put("threadid", threadID);
            JSONArray roomIDs = new JSONArray();
            roomIDs.addAll(LeaderServices.getInstance().getRoomIDList());
            reply.put("rooms", roomIDs);

            Server destinationServer = CurrentServer.getInstance().getServers().get(sender);

            Socket destinationSocket = new Socket(destinationServer.getServerAddress(), destinationServer.getCoordinationPort());
            send(reply, destinationSocket);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void requestDeleteRoom(String roomID, String mainHallID, String ownerID){
        LeaderServices.getInstance().removeRoom(roomID, mainHallID, ownerID);
    }

    public void requestQuit(String clientID, String former) {
        LeaderServices.getInstance().removeClient(clientID, former);
        System.out.println("leader delete " + clientID + " client ");
    }

}
