package handlers;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import services.CoordinationServices;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import services.LeaderServices;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import static constants.Constant.*;
import static handlers.ElectionThreadHandler.receiveMessages;

public class ServerThreadHandler extends Thread {
    private final ServerSocket CoordinationSocket;

    public ServerThreadHandler(ServerSocket CoordinationSocket) {
        this.CoordinationSocket = CoordinationSocket;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Socket serverSocket = CoordinationSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                String serverInputLine = in.readLine();

                JSONParser jsonParser = new JSONParser();
                JSONObject serverInputData = (JSONObject) jsonParser.parse(serverInputLine);

                if (serverInputData.containsKey("option")) {
                    receiveMessages(serverInputData);
                }
                else if (serverInputData.containsKey("type")) {

                    switch (serverInputData.get("type").toString()) {
                        case REQUEST_NEW_IDENTITY_APPROVAL -> LeaderServices.getInstance().requestNewIdentityApproval(
                                serverInputData.get("clientid").toString(),
                                Integer.parseInt(serverInputData.get("sender").toString()),
                                serverInputData.get("threadid").toString()
                        );

                        case REPLY_NEW_IDENTITY_APPROVAL -> CoordinationServices.getInstance().replyNewIdentityApproval(
                                Long.parseLong(serverInputData.get("threadid").toString()),
                                Boolean.parseBoolean(serverInputData.get("approved").toString())
                        );

                        case REQUEST_CREATE_ROOM_APPROVAL -> LeaderServices.getInstance().requestCreateRoomApproval(
                                serverInputData.get("clientid").toString(),
                                serverInputData.get("roomid").toString(),
                                Integer.parseInt(serverInputData.get("sender").toString()),
                                serverInputData.get("threadid").toString()
                        );

                        case REPLY_CREATE_ROOM_APPROVAL -> CoordinationServices.getInstance().replyCreateRoomApproval(
                                Long.parseLong(serverInputData.get("threadid").toString()),
                                Boolean.parseBoolean(serverInputData.get("approved").toString())
                        );

                        case REQUEST_JOIN_ROOM_APPROVAL -> LeaderServices.getInstance().requestJoinRoomApproval(
                                serverInputData.get("clientid").toString(),
                                serverInputData.get("roomid").toString(),
                                serverInputData.get("former").toString(),
                                Integer.parseInt(serverInputData.get("sender").toString()),
                                serverInputData.get("threadid").toString(),
                                Boolean.parseBoolean(serverInputData.get("isLocalRoomChange").toString())
                        );


                        case REPLY_JOIN_ROOM_APPROVAL -> CoordinationServices.getInstance().replyJoinRoomApproval(
                                Boolean.parseBoolean(serverInputData.get("approved").toString()),
                                Long.parseLong(serverInputData.get("threadid").toString()),
                                serverInputData.get("host").toString(),
                                serverInputData.get("port").toString()
                        );

                        case ACK_MOVE_JOIN -> LeaderServices.getInstance().ackMoveJoin(
                                serverInputData.get("clientid").toString(),
                                serverInputData.get("roomid").toString(),
                                Integer.parseInt(serverInputData.get("sender").toString())
                        );

                        case REQUEST_LIST -> LeaderServices.getInstance().requestList(
                                serverInputData.get("threadid").toString(),
                                Integer.parseInt(serverInputData.get("sender").toString())
                        );

                        case REPLY_LIST -> CoordinationServices.getInstance().replyList(
                                Long.parseLong(serverInputData.get("threadid").toString()),
                                (JSONArray) serverInputData.get("rooms")
                        );

                        case REQUEST_DELETE_ROOM -> LeaderServices.getInstance().requestDeleteRoom(
                                serverInputData.get("roomid").toString(),
                                serverInputData.get("mainhall").toString(),
                                serverInputData.get("owner").toString()
                        );

                        case REQUEST_QUIT -> LeaderServices.getInstance().requestQuit(
                                serverInputData.get("clientid").toString(),
                                serverInputData.get("former").toString()
                        );

                        case HEARTBEAT -> CoordinationServices.getInstance().replyHeartbeat(Integer.parseInt(serverInputData.get("serverid").toString()));

                        case REPLY_HEARTBEAT -> CoordinationServices.getInstance().markHeartbeat(
                                Integer.parseInt(serverInputData.get("serverid").toString())
                        );

                        case LEADER_UPDATE -> CoordinationServices.getInstance().leaderUpdate(serverInputData);

                        case LEADER_UPDATE_COMPLETE -> CoordinationServices.getInstance().leaderUpdateComplete(
                                Integer.parseInt(serverInputData.get("serverid").toString())
                        );
                    }
                }
                else {
                    System.out.println("Invalid JSON from Server");
                }
                serverSocket.close();
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }
}
