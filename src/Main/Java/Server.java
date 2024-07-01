package Java;
import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.*;

public class Server {
    private static final String URL = "jdbc:mysql://localhost:3306/roomchat2";
    private static final String UNAME = "root";
    private static final String PASS = "";
    private static final int PORT = 3000;
    private static final Map<String, PrintWriter> clients = new HashMap<>();

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("Server Started");
                while (true) {
                    Socket socket = serverSocket.accept();
                    System.out.println("Client connected " + socket);
                    ClientHandler clientHandler = new ClientHandler(socket);
                    new Thread(clientHandler).start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private String nickname;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 Connection connection = DriverManager.getConnection(URL, UNAME, PASS)) {

                if (!authenticateUser(connection, in, out)) {
                    return;
                }

                synchronized (clients) {
                    clients.put(nickname, out);
                }

                loadConversation(connection, out);
                broadcastMessage(nickname + " joined the chat", out);

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        break;
                    }
                    broadcastMessage(nickname + ": " + message, out);
                }
            } catch (SQLException | IOException e) {
                e.printStackTrace();
            } finally {
                if (nickname != null) {
                    synchronized (clients) {
                        clients.remove(nickname);
                    }
                    broadcastMessage(nickname + " left the chat", null);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void loadConversation(Connection connection, PrintWriter out) throws SQLException {
            String query = "SELECT message, sender FROM message";
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    String message = resultSet.getString("message");
                    String sender = resultSet.getString("sender");
                    out.println(sender + ": " + message);
                }
            }
        }

        private void broadcastMessage(String message, PrintWriter excludeOut) {
            synchronized (clients) {
                for (PrintWriter clientOut : clients.values()) {
                    if (clientOut != excludeOut) {
                        clientOut.println(message);
                    }
                }
            }
        }

        private boolean authenticateUser(Connection connection, BufferedReader in, PrintWriter out) throws SQLException, IOException {
            out.println("Enter your username: ");
            nickname = in.readLine();

            String query = "SELECT * FROM user WHERE name = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        out.println("User doesn't exist. Please register.");
                        out.println("Enter a password: ");
                        String password = in.readLine();
                        registerUser(connection, password);
                        out.println("Registered. You can now log in.");
                        return false;
                    } else {
                        out.println("Enter your password: ");
                        String password = in.readLine();
                        if (!password.equals(resultSet.getString("password"))) {
                            out.println("Wrong password");
                            return false;
                        } else {
                            return true;
                        }
                    }
                }
            }
        }

        private void registerUser(Connection connection, String password) throws SQLException {
            String query = "INSERT INTO user (name, password) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                preparedStatement.setString(2, password);
                preparedStatement.executeUpdate();
            }
        }
    }
}
