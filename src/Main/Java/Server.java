package Java;
import java.io.*;
import java.net.*;
import java.sql.*;

public class Server {
    private static final int PORT = 6666;
    private static final String URL = "jdbc:mysql://localhost:3306/roomchat";

    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("Server Started");
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

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String nickname;
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 Connection connection = DriverManager.getConnection(URL, DB_USERNAME, DB_PASSWORD)) {
                if (!authenticateUser(connection, in, out)) {
                    return;
                }
                broadcastMessage(connection, nickname + " joined the chat");

                String message;
                while((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        break;
                    }
                    broadcastMessage(connection, nickname + ": " + message);
                }
            } catch (SQLException | IOException e ) {
                e.printStackTrace();
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

        private void broadcastMessage(Connection connection, String message) throws SQLException {
            String query = "INSERT INTO message (sender, message) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, nickname);
                preparedStatement.setString(2, message);
            }

        }
    }
}
