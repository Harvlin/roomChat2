package Java;
import java.util.*;
import java.io.*;
import java.sql.*;
import java.net.*;

public class Server {
    private static final String URL = "jdbc:mysql://localhost/roomchat2";
    private static final String UNAME = "root";
    private static final String PASS = "";
    private static final int PORT = 3000;
    private static Map<String, PrintWriter> clients = new HashMap<>();

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (ServerSocket serverSocket = new ServerSocket(PORT);
                Connection connection = DriverManager.getConnection(URL, UNAME, PASS)) {
                System.out.println("Server Started");
                loadConversation(connection);

                while (true) {
                    Socket socket = serverSocket.accept();
                    System.out.println("Client Connected" + socket);
                    ClientHandler clientHandler = new ClientHandler(socket);
                    new Thread (clientHandler).start();
                }
            } catch (SQLException | IOException e) {
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            System.out.println("JDBC driver not found" + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadConversation(Connection connection) throws SQLException{
        String query = "SELECT message, sender FROM message";
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                String message = resultSet.getString("message");
                String sender = resultSet.getString("sender");
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

    static class ClientHandler implements Runnable{
        private String nickname;
        private Socket socket;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Connection connection = DriverManager.getConnection(URL, UNAME, PASS)) {
                out = new PrintWriter(socket.getOutputStream(), true);
                if (!authenticateUser(connection, in, out)) {
                    return;
                }
                synchronized (clients) {
                    clients.put(nickname, out);
                }
                broadcastMessage(nickname + " joined", out);
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

        private boolean authenticateUser(Connection connection, BufferedReader in, PrintWriter out) throws SQLException, IOException {
            out.println("Enter your username: ");
            nickname = in.readLine();

            String query = "SELECT * FROM user WHERE name = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        out.println("User doesn't exist. Please register."); out.flush();
                        out.println("Enter a password: "); out.flush();
                        String password = in.readLine();
                        registerUser(connection, password);
                        out.println("Registered, You can now log in."); out.flush();
                        return false;
                    } else {
                        out.println("Enter your password: "); out.flush();
                        String password = in.readLine();
                        if (!password.equals(resultSet.getString("password"))) {
                            System.out.println("Wrong password.");
                            return false;
                        }
                        return true;
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