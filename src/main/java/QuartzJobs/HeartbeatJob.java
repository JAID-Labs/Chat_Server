package QuartzJobs;

import models.CurrentServer;
import org.json.simple.JSONObject;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.io.IOException;
import java.net.Socket;

import static constants.Constant.HEARTBEAT;
import static util.Utils.send;

public class HeartbeatJob implements Job {
    private CurrentServer currentServer = CurrentServer.getInstance();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        if (currentServer.getHeartbeatCountMap().isEmpty()){
            for (int i=1; i<=currentServer.getServers().size(); i++){
                currentServer.getHeartbeatCountMap().put(i,0);
            }
        }

        currentServer.getServers().values().forEach((server) -> {
            if (server.getServerID() != currentServer.getServerIntID()){
                JSONObject heartbeat = new JSONObject();
                heartbeat.put("type", HEARTBEAT);
                heartbeat.put("serverid", currentServer.getServerIntID());

                int heartbeatCount = currentServer.getHeartbeatCountMap().get(server.getServerID());
                heartbeatCount++;
                currentServer.getHeartbeatCountMap().put(server.getServerID(), heartbeatCount);

                try {
                    Socket destinationSocket = new Socket(server.getServerAddress(), server.getCoordinationPort());
                    send(heartbeat, destinationSocket);
                    System.out.println("Heartbeat send to server s" + server.getServerID());
                } catch (IOException e) {
                    System.out.println("Heartbeat send to server s" + server.getServerID() + " is failed");
                }

                if (heartbeatCount>5) {
                    System.out.println("server s" + server.getServerID() + " has down");
                    CurrentServer.getInstance().getServers().get(server.getServerID()).setActive(false);
                }
            }
        });
    }
}
