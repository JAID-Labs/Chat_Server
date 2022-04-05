package handlers;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import services.ClientServices;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import static constants.Constant.*;

public class ClientThreadHandler extends Thread{

    private final Socket clientSocket;
    private ClientServices clientServices;

    public ClientThreadHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        clientServices = new ClientServices();
    }

    public ClientServices getClientServices() {
        return clientServices;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            while (!clientServices.isBoolQuit()) {
                String clientInputLine = in.readLine();

                JSONParser jsonParser = new JSONParser();
                JSONObject clientInputData = (JSONObject) jsonParser.parse(clientInputLine);

                switch (clientInputData.get("type").toString()) {
                    case NEW_IDENTITY -> clientServices.newIdentity(clientInputData.get("identity").toString(), clientSocket);

                    case LIST -> clientServices.list(clientSocket);

                    case WHO -> clientServices.who(clientSocket);

                    case CREATE_ROOM -> clientServices.createRoom(clientInputData.get("roomid").toString(), clientSocket);

                    case JOIN_ROOM -> clientServices.joinRoom(clientInputData.get("roomid").toString(), clientSocket);

                    case MOVE_JOIN -> clientServices.moveJoin(clientInputData, clientSocket);

                    case DELETE_ROOM -> clientServices.deleteRoom(clientInputData.get("roomid").toString(), clientSocket);

                    case MESSAGE -> clientServices.message(clientInputData.get("content").toString());

                    case QUIT -> clientServices.quit(clientSocket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e){
            System.out.println("wrong json!");
        }
    }

}
