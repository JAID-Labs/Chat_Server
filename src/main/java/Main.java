import handlers.ClientThreadHandler;
import handlers.ElectionThreadHandler;
import QuartzJobs.HeartbeatJob;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import handlers.ServerThreadHandler;
import models.CurrentServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;

import static constants.Constant.HEARTBEAT;
import static handlers.ElectionThreadHandler.initialize;

public class Main {

    public static void main(String[] args) {

        String serverID = args[0];
        String serverConfPath = args[1];
        CurrentServer.getInstance().initializeWithConfigs(serverID, serverConfPath);

        try {
            //Coordination server socket
            ServerSocket coordinationServerSocket = new ServerSocket();
            SocketAddress endPointCoordination = new InetSocketAddress("0.0.0.0", CurrentServer.getInstance().getCoordinationPort());
            coordinationServerSocket.bind(endPointCoordination);
            System.out.println(coordinationServerSocket.getLocalSocketAddress());

            System.out.println("Coordination server socket address: "+ CurrentServer.getInstance().getServerAddress());
            System.out.println("Coordination server socket port: " + CurrentServer.getInstance().getCoordinationPort());

            ServerThreadHandler serverThreadHandler = new ServerThreadHandler(coordinationServerSocket);
            serverThreadHandler.start();

            //leader election
            initialize();

            Runnable leaderElection = new ElectionThreadHandler(HEARTBEAT);
            new Thread(leaderElection).start();

            //Heartbeat
            JobDetail heartbeatJob = JobBuilder.newJob(HeartbeatJob.class).withIdentity("Heartbeat").build();

            Trigger heartbeatTrigger = TriggerBuilder
                    .newTrigger()
                    .withIdentity("Heartbeat_Trigger")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(5).repeatForever())
                    .build();

            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.start();
            scheduler.scheduleJob(heartbeatJob, heartbeatTrigger);

            // Client connection server socket
            ServerSocket clientServerSocket = new ServerSocket();
            SocketAddress endPointClient = new InetSocketAddress("0.0.0.0", CurrentServer.getInstance().getClientsPort());
            clientServerSocket.bind(endPointClient);

            System.out.println("Client connection server socket address: "+ CurrentServer.getInstance().getServerAddress());
            System.out.println("Client connection server socket port: " + CurrentServer.getInstance().getClientsPort());

            while (true) {
                ClientThreadHandler clientThreadHandler = new ClientThreadHandler(clientServerSocket.accept());
                CurrentServer.getInstance().addClientHandlerThreadToMap(clientThreadHandler);
                clientThreadHandler.start();
            }
        } catch (SchedulerException | IOException e) {
            e.printStackTrace();
        }

    }

}
