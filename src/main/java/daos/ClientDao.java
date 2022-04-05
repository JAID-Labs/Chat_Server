package daos;

import java.util.ArrayList;

public class ClientDao {

    private final ArrayList<String> clients;

    private ClientDao() {
        clients = new ArrayList<>();
    }

    private static ClientDao instance;

    public static ClientDao getInstance() {
        if (instance == null) {
            synchronized (ClientDao.class) {
                if (instance == null) {
                    instance = new ClientDao();
                }
            }
        }
        return instance;
    }

    public ArrayList<String> getClients() {
        return clients;
    }

}
