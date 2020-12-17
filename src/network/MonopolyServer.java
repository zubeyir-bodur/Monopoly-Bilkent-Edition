package network;

import com.dosse.upnp.UPnP;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.google.gson.Gson;
import control.MonopolyGame;
import control.action.Action;
import control.action.PassAction;
import entity.Player;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;

// 1. Player clicks at create new game and MonopolyServer gets created.
// 2. MonopolyClient object gets created for the owner and connects to the server
// 3. MonopolyServer class sends "buttons active" command to the client.
// 4. Every time a player joins, MonopolyServer class adds this player and sends the list to the clients
public class MonopolyServer {

    // Singleton
    private static MonopolyServer instance = null;

    Server server;
    private final Set<Connection> clients = new HashSet<>();
    private final Map<Connection, Player> registeredPlayer = new HashMap<>();
    private ArrayList<Player> players = new ArrayList<>();
    private MonopolyGame monopolyGame;
    private Connection activeConnection = null;
    boolean gameStarted = false;
    boolean checkboxes[] = new boolean[3];

    // LobbyController lobbyController

    private MonopolyServer() throws IOException {

        server = new Server();
        server.start();

        server.bind(MonopolyNetwork.PORT);

        MonopolyNetwork.register(server);
        System.out.println("IP Address: " + InetAddress.getLocalHost().getHostAddress());
        MonopolyNetwork.ipAddress = InetAddress.getLocalHost().getHostAddress();
        System.out.println("[SERVER] Server initialized and ready for connections...");

        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                if (!clients.contains(connection)) {
                    clients.add(connection);
                }
                System.out.println("[SERVER] New client connected --> " + connection.getID());

                //server.sendToAllTCP("update lobby");

                // lobbyController.update()
                // activeConnection = connection;
//
//                if (clients.size() >= 2) {
//                    try {
//                        startGame(); // temporary
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }

            @Override
            public void disconnected(Connection connection) {
                if (clients.contains(connection)) {
                    clients.remove(connection);
                }
                System.out.println("[SERVER] Client disconnected --> " + connection.getID());
                registeredPlayer.remove(connection); // not sure whether it works or not
            }

            @Override
            public void idle(Connection connection) {
            }

            @Override
            public void received(Connection connection, Object o) {
                if (gameStarted) {
                    if (activeConnection == null) {
                        System.out.println("[SERVER] ERROR:Active connection is null!");
                    }
                    else {
                        if (connection.equals(activeConnection)) {
                            if (o instanceof Action) {
                                server.sendToAllExceptTCP(activeConnection.getID(), o);
                            }
                            else if (o instanceof String) {
                                String s = (String) o;
                                System.out.println("[SERVER] Message from " + connection.getID() + " --> " + s);
                            }
                        }
                        else {
                            if (o instanceof ChatMessage) {
                                server.sendToAllExceptTCP(connection.getID(), o);
                            }
                        }
                    }
                }
                else {
                    if (o instanceof String) {
                        String s = (String) o;
                        if (s.equals("start game")) {
                            try {
                                startGame();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else if(s.contains("player name")) {
                            String name = s.substring((s.indexOf(":") + 1));
                            System.out.println("name:" + name);
                            Player player = new Player(connection.getID() - 1, name, Player.Token.NONE, 0);
                            registeredPlayer.put(connection, player);

                            ArrayList<Player> players = new ArrayList<>(registeredPlayer.values());
                            server.sendToAllTCP(players);
                            server.sendToAllTCP(checkboxes);
                            server.sendToAllTCP("update lobby");
                        } else if (s.contains("leave lobby")) {
                            int id = Integer.getInteger(s.substring(s.indexOf(":") + 1));
                            System.out.println("player with id: " + id + " left the lobby");
                            for (Map.Entry<Connection, Player> entry : registeredPlayer.entrySet()) {
                                if (entry.getValue().getPlayerId() == id) {
                                    registeredPlayer.remove(entry.getKey());
                                    break;
                                }
                            }
                            ArrayList<Player> player = new ArrayList<>(registeredPlayer.values());
                            server.sendToAllTCP(players);
                            server.sendToAllTCP(checkboxes);
                            server.sendToAllTCP("update lobby");
                        }
                    }
                    else if (o instanceof boolean[]) {
                        boolean[] checkboxes = (boolean[]) o;
                        MonopolyServer.this.checkboxes = checkboxes;
                        server.sendToAllExceptTCP(connection.getID(), checkboxes);
                        server.sendToAllExceptTCP(connection.getID(), "update lobby");
                    }
                    else if (o instanceof Player) {
                        Player player = (Player) o;
                        registeredPlayer.put(connection, player);
                        server.sendToAllExceptTCP(connection.getID(), new ArrayList<Player>(registeredPlayer.values()));
                        server.sendToAllTCP("update lobby");
                    }
                }

            }
        });
    }

    public static MonopolyServer getInstance()
    {
        if (instance == null) {
            try {
                instance = new MonopolyServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return instance;
    }

    public void startGame() throws IOException {
        // ToDo get players from clients
        long seed = System.currentTimeMillis();
        ArrayList<Player> players = new ArrayList<Player>(registeredPlayer.values());
        System.out.println("Game starts with players --> " + players);
        monopolyGame = new MonopolyGame(players, seed);

        // send this game to clients, or send the players and seed (more efficient)
        server.sendToAllTCP(players);
        server.sendToAllTCP(seed);
        // bind the ui to the game
        // start the game and get the first player
        Player activePlayer = monopolyGame.startGame();
        // go to the game screen in clients
        server.sendToAllTCP("game started");
        //server.sendToAllTCP("Game started with these players --> " + players);
        //server.sendToAllTCP("Game started with this seed --> " + seed);
        gameStarted = true;

        //System.out.println(new Gson().toJson(players));
        //System.out.println(new Gson().toJson(monopolyGame.getBoard().getProperties()));

        //activeConnection = registeredPlayer.get(activePlayer);
        //activeConnection.sendTCP("activate buttons"); // continue this method, inactive for other connections

        for (Connection c : clients) {
            if (activeConnection != c) {
                c.sendTCP("deactivate buttons");
            }
        }

    }

    public void stopGame() {
        server.stop(); // too harsh??
    }

    public static void main(String[] args) throws IOException {
        MonopolyServer monopolyServer = new MonopolyServer();
    }

}
