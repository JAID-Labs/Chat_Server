package services;

import models.Client;
import models.CurrentServer;
import models.Room;
import models.Server;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import util.Utils;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static constants.Constant.*;
import static util.Utils.send;
import static util.Utils.sendLeader;

public class ClientServices {
    private int approvedClientID = -1;
    private int approvedRoomCreation = -1;
    private int approvedJoinRoom = -1;

    private String approvedJoinRoomServerHostAddress;
    private String approvedJoinRoomServerPort;
    private Client client;

    private List<String> roomsList;

    final Object lock;

    private boolean boolQuit = false;

    public ClientServices() {
        this.lock = new Object();
    }

    public void setApprovedClientID(int approvedClientID) {
        this.approvedClientID = approvedClientID;
    }

    public void setApprovedRoomCreation( int approvedRoomCreation ) {
        this.approvedRoomCreation = approvedRoomCreation;
    }

    public void setApprovedJoinRoom(int approvedJoinRoom) {
        this.approvedJoinRoom = approvedJoinRoom;
    }

    public void setApprovedJoinRoomServerHostAddress(String approvedJoinRoomServerHostAddress) {
        this.approvedJoinRoomServerHostAddress = approvedJoinRoomServerHostAddress;
    }

    public void setApprovedJoinRoomServerPort(String approvedJoinRoomServerPort) {
        this.approvedJoinRoomServerPort = approvedJoinRoomServerPort;
    }

    public void setRoomsList(List<String> roomsList) {
        this.roomsList = roomsList;
    }

    public Object getLock() {
        return lock;
    }

    public boolean isBoolQuit() {
        return boolQuit;
    }

    public void newIdentity(String clientID, Socket clientSocket) {
        try {
            if (Utils.isValidIdentity(clientID)) {
                while (!LeaderServices.getInstance().isLeaderElected()) {
                    Thread.sleep(1000);
                }

                if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                    if (LeaderServices.getInstance().isClientRegistered(clientID)) {
                        approvedClientID = 0;
                        System.out.println("Client is not approved");
                    } else {
                        approvedClientID = 1;
                        System.out.println("Client is approved");
                    }
                }
                else {
                    try {
                        JSONObject requestMessage = new JSONObject();
                        requestMessage.put("type", REQUEST_NEW_IDENTITY_APPROVAL);
                        requestMessage.put("clientid", clientID);
                        requestMessage.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));
                        requestMessage.put("threadid", String.valueOf(Thread.currentThread().getId()));

                        sendLeader(requestMessage);

                        System.out.println("Client ID '" + clientID + "' sent to leader for approval");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    synchronized (lock) {
                        while (approvedClientID == -1) {
                            lock.wait(7000);
                        }
                    }
                }

                if (approvedClientID == 1) {
                    this.client = new Client(clientID, CurrentServer.getInstance().getMainHall().getRoomID(), clientSocket);
                    CurrentServer.getInstance().getMainHall().getParticipantsMap().put(clientID, client);

                    if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                        LeaderServices.getInstance().addClient(new Client(clientID, client.getRoomID(), null));
                    }

                    String mainHallRoomID = CurrentServer.getInstance().getMainHall().getRoomID();

                    JSONObject newIdentityMessage = new JSONObject();
                    newIdentityMessage.put("type", NEW_IDENTITY);
                    newIdentityMessage.put("approved", "true");

                    JSONObject joinRoomMessage = new JSONObject();
                    joinRoomMessage.put("type", ROOM_CHANGE);
                    joinRoomMessage.put("identity", clientID);
                    joinRoomMessage.put("former", "");
                    joinRoomMessage.put("roomid", mainHallRoomID);

                    synchronized (clientSocket) {
                        send(newIdentityMessage, clientSocket);
                        CurrentServer.getInstance().getRoomMap().get(mainHallRoomID).getParticipantsMap().forEach((k, v) -> {
                            try {
                                send(joinRoomMessage, v.getSocket());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            ;
                        });
                    }
                }
                else if (approvedClientID == 0) {
                    JSONObject newIdentityMessage = new JSONObject();
                    newIdentityMessage.put("type", NEW_IDENTITY);
                    newIdentityMessage.put("approved", "false");

                    send(newIdentityMessage, clientSocket);
                    System.out.println("Already used ClientID");
                }
                approvedClientID = -1;
            }
            else {
                JSONObject newIdentityMessage = new JSONObject();
                newIdentityMessage.put("type", NEW_IDENTITY);
                newIdentityMessage.put("approved", "false");

                send(newIdentityMessage, clientSocket);
                System.out.println("Wrong ClientID");
            }
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void list(Socket clientSocket) {
        try {
            roomsList = null;

            while (!LeaderServices.getInstance().isLeaderElected()) {
                Thread.sleep(1000);
            }

            if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                roomsList = LeaderServices.getInstance().getRoomIDList();
            }
            else {
                JSONObject request = new JSONObject();
                request.put("type", REQUEST_LIST);
                request.put("sender", CurrentServer.getInstance().getServerIntID());
                request.put("threadid", Thread.currentThread().getId());

                sendLeader(request);

                synchronized (lock) {
                    while (roomsList == null) {
                        lock.wait(7000);
                    }
                }
            }

            if (roomsList != null) {
                JSONObject message = new JSONObject();
                message.put("type", ROOMLIST);
                message.put("rooms", roomsList);

                send(message, clientSocket);
            }
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void who(Socket clientSocket) {
        try {
            String roomID = client.getRoomID();
            Room room = CurrentServer.getInstance().getRoomMap().get(roomID);
            JSONArray participants = new JSONArray();
            participants.addAll(room.getParticipantsMap().keySet());
            String ownerID = room.getOwnerIdentity();

            System.out.println("show participants in room " + roomID);

            JSONObject message = new JSONObject();
            message.put("type", ROOM_CONTENTS);
            message.put("roomid", roomID);
            message.put("identities", participants);
            message.put("owner", ownerID);

            send(message, clientSocket);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createRoom(String newRoomID, Socket clientSocket) {
        try {
            if (!Utils.isValidIdentity(newRoomID)) {
                JSONObject roomCreateMessage = new JSONObject();
                roomCreateMessage.put("type", CREATE_ROOM);
                roomCreateMessage.put("roomid", newRoomID);
                roomCreateMessage.put("approved", "false");

                send(roomCreateMessage, clientSocket);
                System.out.println("Wrong RoomID");
            }
            else if (client.isRoomOwner()) {
                JSONObject roomCreateMessage = new JSONObject();
                roomCreateMessage.put("type", CREATE_ROOM);
                roomCreateMessage.put("roomid", newRoomID);
                roomCreateMessage.put("approved", "false");

                send(roomCreateMessage, clientSocket);
                System.out.println("Client already owns a room");
            }
            else {
                while (!LeaderServices.getInstance().isLeaderElected()) {
                    Thread.sleep(1000);
                }
                if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                    if (LeaderServices.getInstance().isRoomCreated(newRoomID)) {
                        approvedRoomCreation = 0;
                        System.out.println("Room creation is not approved");
                    }
                    else {
                        approvedRoomCreation = 1;
                        System.out.println("Room creation is approved");
                    }
                } else {
                    try {
                        JSONObject requestMessage = new JSONObject();
                        requestMessage.put("type", REQUEST_CREATE_ROOM_APPROVAL);
                        requestMessage.put("clientid", client.getClientID());
                        requestMessage.put("roomid", newRoomID);
                        requestMessage.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));
                        requestMessage.put("threadid", String.valueOf(Thread.currentThread().getId()));


                        sendLeader(requestMessage);

                        System.out.println("Room ID '" + newRoomID + "' sent to leader for room creation approval");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    synchronized (lock) {
                        while (approvedRoomCreation == -1) {
                            lock.wait(7000);
                        }
                    }
                }

                if (approvedRoomCreation == 1) {

                    String formerRoomID = client.getRoomID();

                    ArrayList<Socket> formerSocket = new ArrayList<>();
                    CurrentServer.getInstance().getRoomMap().get(formerRoomID).getParticipantsMap().forEach((k, v) -> {
                        formerSocket.add(v.getSocket());
                    });

                    CurrentServer.getInstance().getRoomMap().get(formerRoomID).removeParticipants(client.getClientID());

                    Room newRoom = new Room(client.getClientID(), newRoomID, CurrentServer.getInstance().getServerIntID());
                    CurrentServer.getInstance().getRoomMap().put(newRoomID, newRoom);

                    client.setRoomID(newRoomID);
                    client.setRoomOwner(true);
                    newRoom.addParticipants(client);

                    if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                        LeaderServices.getInstance().addApprovedRoom(client.getClientID(), newRoomID, CurrentServer.getInstance().getServerIntID());
                    }

                    JSONObject roomCreationMessage = new JSONObject();
                    roomCreationMessage.put("type", CREATE_ROOM);
                    roomCreationMessage.put("roomid", newRoomID);
                    roomCreationMessage.put("approved", "true");

                    JSONObject broadcastMessage = new JSONObject();
                    broadcastMessage.put("type", ROOM_CHANGE);
                    broadcastMessage.put("identity", client.getClientID());
                    broadcastMessage.put("former", formerRoomID);
                    broadcastMessage.put("roomid", newRoomID);

                    synchronized (clientSocket) {
                        send(roomCreationMessage, clientSocket);
                        formerSocket.forEach((v) -> {
                            try {
                                send(broadcastMessage, v);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            ;
                        });
                    }
                }
                else if (approvedRoomCreation == 0) {
                    JSONObject roomCreationMessage = new JSONObject();
                    roomCreationMessage.put("type", CREATE_ROOM);
                    roomCreationMessage.put("roomid", newRoomID);
                    roomCreationMessage.put("approved", "false");

                    send(roomCreationMessage, clientSocket);

                    System.out.println("Already used roomID");
                }
                approvedRoomCreation = -1;
            }
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void joinRoom(String roomID, Socket clientSocket) {
        try {
            String formerRoomID = client.getRoomID();

            if (client.isRoomOwner()) {
                JSONObject message = new JSONObject();
                message.put("type", ROOM_CHANGE);
                message.put("identity", client.getClientID());
                message.put("former", formerRoomID);
                message.put("roomid", formerRoomID);

                send(message, clientSocket);

                System.out.println(client.getClientID() + " Owns a room");
            }
            else if (CurrentServer.getInstance().getRoomMap().containsKey(roomID)) {
                client.setRoomID(roomID);
                CurrentServer.getInstance().getRoomMap().get(formerRoomID).removeParticipants(client.getClientID());
                CurrentServer.getInstance().getRoomMap().get(roomID).addParticipants(client);

                System.out.println(client.getClientID() + " join to room " + roomID);

                Collection<Client> newRoomClients = CurrentServer.getInstance().getRoomMap().get(roomID).getParticipantsMap().values();
                Collection<Client> formerRoomClients = CurrentServer.getInstance().getRoomMap().get(formerRoomID).getParticipantsMap().values();

                JSONObject broadcastMessage = new JSONObject();
                broadcastMessage.put("type", ROOM_CHANGE);
                broadcastMessage.put("identity", client.getClientID());
                broadcastMessage.put("former", formerRoomID);
                broadcastMessage.put("roomid", roomID);

                newRoomClients.forEach((i) -> {
                    try {
                        send(broadcastMessage, i.getSocket());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                formerRoomClients.forEach((i) -> {
                    try {
                        send(broadcastMessage, i.getSocket());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                while (!LeaderServices.getInstance().isLeaderElected()) {
                    Thread.sleep(1000);
                }

                if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                    LeaderServices.getInstance().localJoinRoomClient(client, formerRoomID);
                }
                else {
                    JSONObject request = new JSONObject();
                    request.put("type", REQUEST_JOIN_ROOM_APPROVAL);
                    request.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));
                    request.put("roomid", roomID);
                    request.put("former", formerRoomID);
                    request.put("clientid", client.getClientID());
                    request.put("threadid", String.valueOf(Thread.currentThread().getId()));
                    request.put("isLocalRoomChange", "true");

                    sendLeader(request);
                }

            }
            else {
                while (!LeaderServices.getInstance().isLeaderElected()) {
                    Thread.sleep(1000);
                }

                approvedJoinRoom = -1;

                if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                    int roomServerID = LeaderServices.getInstance().getServerIdIfRoomExist(roomID);

                    if (roomServerID != -1) {
                        approvedJoinRoom = 1;
                        Server roomServer = CurrentServer.getInstance().getServers().get(roomServerID);
                        approvedJoinRoomServerHostAddress = roomServer.getServerAddress();
                        approvedJoinRoomServerPort = String.valueOf(roomServer.getClientsPort());
                    }
                    else {
                        approvedJoinRoom = 0;
                    }

                } else {
                    JSONObject request = new JSONObject();
                    request.put("type", REQUEST_JOIN_ROOM_APPROVAL);
                    request.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));
                    request.put("roomid", roomID);
                    request.put("former", formerRoomID);
                    request.put("clientid", client.getClientID());
                    request.put("threadid", String.valueOf(Thread.currentThread().getId()));
                    request.put("isLocalRoomChange", "false");

                    sendLeader(request);

                    synchronized (lock) {
                        while (approvedJoinRoom == -1) {
                            lock.wait(7000);
                        }
                    }

                    System.out.println("Received response for join room route request");
                }

                if (approvedJoinRoom == 1) {
                    CurrentServer.getInstance().removeClient(client.getClientID(), formerRoomID, Thread.currentThread().getId());
                    System.out.println(client.getClientID() + " left " + formerRoomID + " room");

                    Collection<Client> formerRoomClients = CurrentServer.getInstance().getRoomMap().get(formerRoomID).getParticipantsMap().values();

                    JSONObject broadcastMessage = new JSONObject();
                    broadcastMessage.put("type", ROOM_CHANGE);
                    broadcastMessage.put("identity", client.getClientID());
                    broadcastMessage.put("former", formerRoomID);
                    broadcastMessage.put("roomid", roomID);

                    formerRoomClients.forEach((i) -> {
                        try {
                            send(broadcastMessage, i.getSocket());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    JSONObject routeMessage = new JSONObject();
                    routeMessage.put("type", ROUTE);
                    routeMessage.put("roomid", roomID);
                    routeMessage.put("host", approvedJoinRoomServerHostAddress);
                    routeMessage.put("port", approvedJoinRoomServerPort);

                    send(routeMessage, clientSocket);
                    System.out.println("Route Message Sent to Client");
                    boolQuit = true;
                } else if (approvedJoinRoom == 0) {
                    JSONObject message = new JSONObject();
                    message.put("type", ROOM_CHANGE);
                    message.put("identity", client.getClientID());
                    message.put("former", formerRoomID);
                    message.put("roomid", formerRoomID);

                    send(message, clientSocket);

                    System.out.println(roomID + "room does not exist");
                }

                approvedJoinRoom = -1;
            }
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void moveJoin(JSONObject inputData, Socket clientSocket) {
        try {
            String roomID = inputData.get("roomid").toString();
            String formerRoomID = inputData.get("former").toString();
            String clientID = inputData.get("identity").toString();

            if (CurrentServer.getInstance().getRoomMap().containsKey(roomID)) {
                roomID = inputData.get("roomid").toString();
            } else {
                roomID = CurrentServer.getInstance().getMainHallID();
            }

            this.client = new Client(clientID, roomID, clientSocket);
            CurrentServer.getInstance().getRoomMap().get(roomID).addParticipants(client);

            Collection<Client> roomParticipants = CurrentServer.getInstance().getRoomMap().get(roomID).getParticipantsMap().values();

            JSONObject serverChangeMessage = new JSONObject();
            serverChangeMessage.put("type", SERVER_CHANGE);
            serverChangeMessage.put("approved", "true");
            serverChangeMessage.put("serverid", CurrentServer.getInstance().getServerID());

            send(serverChangeMessage, clientSocket);

            JSONObject broadcastMessage = new JSONObject();
            broadcastMessage.put("type", ROOM_CHANGE);
            broadcastMessage.put("identity", clientID);
            broadcastMessage.put("former", formerRoomID);
            broadcastMessage.put("roomid", roomID);

            roomParticipants.forEach((i) -> {
                try {
                    send(broadcastMessage, i.getSocket());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            while (!LeaderServices.getInstance().isLeaderElected()) {
                Thread.sleep(1000);
            }

            if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                LeaderServices.getInstance().addClient(new Client(clientID, roomID, null));
            }
            else {
                JSONObject ack = new JSONObject();
                ack.put("type", ACK_MOVE_JOIN);
                ack.put("sender", String.valueOf(CurrentServer.getInstance().getServerIntID()));
                ack.put("roomid", roomID);
                ack.put("clientid", client.getClientID());

                sendLeader(ack);
            }
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void deleteRoom(String roomID, Socket clientSocket) {
        try {
            String mainHallID = CurrentServer.getInstance().getMainHall().getRoomID();

            if (CurrentServer.getInstance().getRoomMap().containsKey(roomID)) {
                Room room = CurrentServer.getInstance().getRoomMap().get(roomID);
                if (room.getOwnerIdentity().equals(client.getClientID())) {
                    ConcurrentHashMap<String, Client> formerClients = CurrentServer.getInstance().getRoomMap().get(roomID).getParticipantsMap();

                    Collection<Client> mainHallClients = CurrentServer.getInstance().getRoomMap().get(mainHallID).getParticipantsMap().values();

                    ArrayList<Socket> socketList = new ArrayList<>();

                    formerClients.values().forEach((i) -> {
                        socketList.add(i.getSocket());
                    });

                    mainHallClients.forEach((i) -> {
                        socketList.add(i.getSocket());
                    });

                    CurrentServer.getInstance().getRoomMap().remove(roomID);
                    client.setRoomOwner(false);

                    formerClients.forEach((k, v) -> {
                        v.setRoomID(mainHallID);
                        CurrentServer.getInstance().getRoomMap().get(mainHallID).addParticipants(v);

                        JSONObject message = new JSONObject();
                        message.put("type", ROOM_CHANGE);
                        message.put("identity", k);
                        message.put("former", roomID);
                        message.put("roomid", mainHallID);

                        socketList.forEach((i) -> {
                            try {
                                send(message, i);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    });

                    while (!LeaderServices.getInstance().isLeaderElected()) {
                        Thread.sleep(1000);
                    }

                    if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                        LeaderServices.getInstance().removeRoom(roomID, mainHallID, client.getClientID());
                    }
                    else {
                        JSONObject request = new JSONObject();
                        request.put("type", REQUEST_DELETE_ROOM);
                        request.put("owner", client.getClientID());
                        request.put("roomid", roomID);
                        request.put("mainhall", mainHallID);

                        sendLeader(request);
                    }

                    System.out.println(roomID + " room is deleted");

                }
                else {
                    JSONObject message = new JSONObject();
                    message.put("type", DELETE_ROOM);
                    message.put("roomid", roomID);
                    message.put("approved", "false");

                    send(message, clientSocket);

                    System.out.println("Requesting client is not the owner of the room " + roomID);
                }
            }
            else {
                JSONObject message = new JSONObject();
                message.put("type", DELETE_ROOM);
                message.put("roomid", roomID);
                message.put("approved", "false");

                send(message, clientSocket);

                System.out.println("Room ID " + roomID + " does not exist");
            }
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void quit(Socket clientSocket){
        try {
            if (client.isRoomOwner()) {
                deleteRoom(client.getRoomID(), clientSocket);
                System.out.println(client.getRoomID() + " room deleted due to owner quiting");
            }

            JSONObject quitMessage = new JSONObject();
            quitMessage.put("type", ROOM_CHANGE);
            quitMessage.put("identity", client.getClientID());
            quitMessage.put("former", client.getRoomID());
            quitMessage.put("roomid", "");

            Collection<Client> roomClients = CurrentServer.getInstance().getRoomMap().get(client.getRoomID()).getParticipantsMap().values();

            roomClients.forEach((i) -> {
                try {
                    send(quitMessage, i.getSocket());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            CurrentServer.getInstance().removeClient(client.getClientID(), client.getRoomID(), Thread.currentThread().getId());

            if (CurrentServer.getInstance().getServerIntID() == LeaderServices.getInstance().getLeaderID()) {
                LeaderServices.getInstance().removeClient(client.getClientID(), client.getRoomID());
            }
            else {
                JSONObject leaderMessage = new JSONObject();
                leaderMessage.put("type", REQUEST_QUIT);
                leaderMessage.put("clientid", client.getClientID());
                leaderMessage.put("former", client.getRoomID());

                sendLeader(leaderMessage);
            }

            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }

            System.out.println(client.getClientID() + " requestQuit");
            boolQuit = true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void message(String content) {
        String clientID = client.getClientID();
        String roomid = client.getRoomID();

        JSONObject message = new JSONObject();
        message.put("type", MESSAGE);
        message.put("identity", clientID);
        message.put("content", content);


        CurrentServer.getInstance().getRoomMap().get(roomid).getParticipantsMap().forEach((k, v) -> {
            if (!k.equals(clientID)) {
                try {
                    send(message, v.getSocket());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
