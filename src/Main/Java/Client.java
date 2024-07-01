package Java;
import java.io.*;
import java.net.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 3000;
    private static String username;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))
        ) {
            authenticateUser(in, out, consoleInput);

            Thread messageReceiverThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            messageReceiverThread.start();

            String userInput;
            while ((userInput = consoleInput.readLine()) != null) {
                out.println(userInput);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void authenticateUser(BufferedReader in, PrintWriter out, BufferedReader consoleInput) throws IOException {
        String response;
        while (true) {
            response = in.readLine();
            if (response != null) {
                System.out.println(response);
                if (response.contains("Enter your username: ")) {
                    username = consoleInput.readLine();
                    out.println(username);
                } else if (response.contains("Enter your password: ") || response.contains("Enter a password: ")) {
                    String password = consoleInput.readLine();
                    out.println(password);
                } else if (response.contains("Registered, You can now log in.")) {
                    break;
                } else if (response.contains("Wrong password.") || response.contains("User doesn't exist.")) {
                    continue;
                }
            }
        }
    }
}
