package handlers;

import models.Server;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import models.CurrentServer;
import services.LeaderServices;
import java.net.Socket;
import java.util.List;

import static constants.Constant.LEADER_UPDATE_COMPLETE;
import static util.Utils.send;

public class LeaderUpdateThreadHandler extends Thread {

    int numberOfServersWithLowerIds = CurrentServer.getInstance().getServerIntID() - 1;
    int numberOfUpdatesReceived = 0;
    volatile boolean leaderUpdateInProgress = true;

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        long end = start + 5000;
        try {
            while (leaderUpdateInProgress) {
                if(System.currentTimeMillis() > end || numberOfUpdatesReceived == numberOfServersWithLowerIds) {
                    leaderUpdateInProgress = false;
                    System.out.println("Leader update completed");

                    ElectionThreadHandler.leaderUpdateComplete = true;

                    List<String> selfClients = CurrentServer.getInstance().getClientIdList();
                    List<List<String>> selfRooms = CurrentServer.getInstance().getChatRoomList();

                    for(String clientID : selfClients) {
                        LeaderServices.getInstance().addClientLeaderUpdate(clientID);
                    }

                    for(List<String> chatRoom : selfRooms) {
                        LeaderServices.getInstance().addApprovedRoom(
                                chatRoom.get(0),
                                chatRoom.get(1),
                                Integer.parseInt(chatRoom.get(2))
                        );
                    }
                    System.out.println("Finalized clients: " + LeaderServices.getInstance().getClientIDList() + ", rooms: " + LeaderServices.getInstance().getRoomIDList());

                    for (int key : CurrentServer.getInstance().getServers().keySet()) {
                        if (key != CurrentServer.getInstance().getServerIntID()){
                            Server destServer = CurrentServer.getInstance().getServers().get(key);

                            JSONObject message = new JSONObject();
                            message.put("type", LEADER_UPDATE_COMPLETE);
                            message.put("serverid", String.valueOf(CurrentServer.getInstance().getServerIntID()));

                            try {
                                Socket destinationSocket = new Socket(destServer.getServerAddress(), destServer.getCoordinationPort());

                                send(message, destinationSocket);

                                System.out.println("Sent leader update complete message to s"+destServer.getServerID());
                            }
                            catch(Exception e) {
                                System.out.println("Server s"+destServer.getServerID()+ " has failed, it will not receive the leader update complete message");
                            }
                        }
                    }
                }
                Thread.sleep(10);
            }

        } catch(Exception e) {
            System.out.println("Exception in leader update thread");
        }

    }

    public void receiveUpdate( JSONObject data ) {
        numberOfUpdatesReceived++;
        JSONArray clientIdList = (JSONArray) data.get("clients");
        JSONArray chatRoomsList = (JSONArray) data.get("chatrooms");

        for( Object clientID : clientIdList ) {
            LeaderServices.getInstance().addClientLeaderUpdate(clientID.toString());
        }

        for( Object chatRoom : chatRoomsList ) {
            JSONObject j_room = (JSONObject)chatRoom;
            LeaderServices.getInstance().addApprovedRoom(j_room.get("clientid").toString(), j_room.get("roomid").toString(), Integer.parseInt(j_room.get("serverid").toString()));
        }
    }

}
