package Java;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int PORT = 6666;
    private static final String URL = "jdbc:mysql://localhost:3306/roomchat";
    private static final Map<String , PrintWriter> clients = new HashMap<>();
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (ServerSocket serverSocket = new ServerSocket(PORT);
                 Connection connection = DriverManager.getConnection(URL, DB_USERNAME, DB_PASSWORD)) {
                System.out.println("Server Started");
                loadConversationHistory(connection);
                while (true) {
                    Socket socket = serverSocket.accept();
                    System.out.println("New client connected: " + socket);
                    ClientHandler clientHandler = new ClientHandler(socket);
                    new Thread(clientHandler).start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void loadConversationHistory(Connection connection) throws SQLException {
        String query = "SELECT sender, message FROM message";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                String sender = resultSet.getString("sender");
                String message = resultSet.getString("message");
                broadcastMessage(sender + ": " + message, null);
            }
        }
    }
    private static void broadcastMessage(String message, PrintWriter out) {
        synchronized (clients) {
            for (PrintWriter printWriter : clients.values()) {
                if (printWriter != out) {
                    printWriter.println(message);
                }
            }
        }
    }
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String nickname;
        PrintWriter out;
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 Connection connection = DriverManager.getConnection(URL, DB_USERNAME, DB_PASSWORD)) {
                out = new PrintWriter(socket.getOutputStream(), true);
                if (!authenticateUser(connection, in, out)) {
                    return;
                }
                synchronized (clients) {
                    clients.put(nickname, out);
                }
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
                    if (out != null) {
                        broadcastMessage(nickname + " left the chat", out);
                    }
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        private boolean authenticateUser(Connection connection, BufferedReader in, PrintWriter out) throws IOException, SQLException {
            out.println("Enter your username: ");
            nickname = in.readLine();

            String checkUserQuery = "SELECT * FROM user WHERE name = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(checkUserQuery)) {
                preparedStatement.setString(1, nickname);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        out.println("User doesn't exist. Please register");
                        out.println("Enter a password: ");
                        String password = in.readLine();
                        registerUser(connection, password);
                        out.println("User registered. You can now log in.");
                        return false;
                    } else {
                        out.println("Enter your password: ");
                        String password = in.readLine();
                        if (!password.equals(resultSet.getString("password"))) {
                            out.println("Invalid password.");
                            return false;
                        }
                        return true;
                    }
                }
            }
        }
        private void registerUser(Connection connection, String password) throws SQLException {
            String query = "INSERT INTO USER (name, password) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                preparedStatement.setString(2, password);
                preparedStatement.executeUpdate();
            }
        }
    }
}