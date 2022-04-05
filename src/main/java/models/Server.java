package models;

public class Server {
    private int id;
    private int coordinationPort;
    private int clientsPort;
    private String serverAddress;
    private boolean active = false;

    public Server(int serverID, int coordinationPort, int clientsPort, String serverAddress)
    {
        this.id = serverID;
        this.coordinationPort = coordinationPort;
        this.clientsPort = clientsPort;
        this.serverAddress = serverAddress;
    }

    public int getServerID()
    {
        return id;
    }

    public int getCoordinationPort()
    {
        return coordinationPort;
    }

    public int getClientsPort()
    {
        return clientsPort;
    }

    public String getServerAddress()
    {
        return serverAddress;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

}
