package models;

import handlers.ClientThreadHandler;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class CurrentServer {
    private String serverID;
    private int serverIntID;
    private String serverAddress = null;
    private int coordinationPort;
    private int clientsPort;
    private int numberOfServersWithHigherIds;

    private final ConcurrentHashMap<Integer, Integer> heartbeatCountMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, Server> servers = new ConcurrentHashMap<>();

    private Room mainHall;

    private final ConcurrentHashMap<Long, ClientThreadHandler> clientHandlerThreadMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Room> roomMap = new ConcurrentHashMap<>();

    private static CurrentServer currentServerInstance;

    private CurrentServer() {}

    public static CurrentServer getInstance() {
        if (currentServerInstance == null) {
            synchronized (CurrentServer.class) {
                if (currentServerInstance == null) {
                    currentServerInstance = new CurrentServer();
                }
            }
        }
        return currentServerInstance;
    }

    public void initializeWithConfigs(String serverID, String serverConfPath) {
        this.serverID = serverID;
        try {
            File conf = new File(serverConfPath);
            Scanner myReader = new Scanner(conf);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String[] params = data.split(" ");
                if (params[0].equals(serverID)) {
                    this.serverAddress = params[1];
                    this.clientsPort = Integer.parseInt(params[2]);
                    this.coordinationPort = Integer.parseInt(params[3]);
                    this.serverIntID = Integer.parseInt(params[0].substring(1, 2));
                }

                Server s = new Server(Integer.parseInt(params[0].substring(1, 2)),
                        Integer.parseInt(params[3]),
                        Integer.parseInt(params[2]),
                        params[1]);
                servers.put(s.getServerID(), s);
            }
            myReader.close();

            this.servers.get(this.serverIntID).setActive(true);

        } catch (FileNotFoundException e) {
            System.out.println("Configs file not found");
            e.printStackTrace();
        }

        numberOfServersWithHigherIds = servers.size() - serverIntID;

        this.mainHall = new Room("", getMainHallID(), serverIntID);
        this.roomMap.put(getMainHallID(), mainHall);

    }

    public void addClientHandlerThreadToMap(ClientThreadHandler clientThreadHandler) {
        clientHandlerThreadMap.put(clientThreadHandler.getId(), clientThreadHandler);
    }

    public ClientThreadHandler getClientHandlerThread(Long threadID) {
        return clientHandlerThreadMap.get(threadID);
    }

    public List<String> getClientIdList() {
        List<String> clientIdList = new ArrayList<>();
        roomMap.forEach((roomID, room) -> {
            clientIdList.addAll(room.getParticipantsMap().keySet());
        });
        return clientIdList;
    }


    public List<List<String>> getChatRoomList() {
        List<List<String>> chatRoomList = new ArrayList<>();
        for (Room room: roomMap.values()) {
            List<String> roomInfo = new ArrayList<>();
            roomInfo.add(room.getOwnerIdentity());
            roomInfo.add(room.getRoomID());
            roomInfo.add(String.valueOf(room.getServerID()));

            chatRoomList.add(roomInfo);
        }
        return chatRoomList;
    }

    public void removeClient (String clientID, String formerRoom, Long threadID){
        this.roomMap.get(formerRoom).removeParticipants(clientID);
        this.clientHandlerThreadMap.remove(threadID);
    }

    public String getServerID() {
        return serverID;
    }

    public int getClientsPort() {
        return clientsPort;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getCoordinationPort() {
        return coordinationPort;
    }

    public int getServerIntID() {
        return serverIntID;
    }

    public int getNumberOfServersWithHigherIds() {
        return numberOfServersWithHigherIds;
    }

    public ConcurrentHashMap<Integer, Server> getServers() {
        return servers;
    }

    public Room getMainHall() {
        return mainHall;
    }

    public ConcurrentHashMap<String, Room> getRoomMap() {
        return roomMap;
    }

    public String getMainHallID() {
        return getMainHallIDbyServerInt(this.serverIntID);
    }

    public static String getMainHallIDbyServerInt(int server) {
        return "MainHall-s" + server;
    }

    public ConcurrentHashMap<Integer, Integer> getHeartbeatCountMap() {
        return heartbeatCountMap;
    }

}
