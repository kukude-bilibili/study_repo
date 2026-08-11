import java.io.*;
import java.net.*;

/**
 * TCP 回声客户端 —— 与 TcpEchoServer 配套
 *
 * 功能：连接回声服务器，从控制台读取用户输入发送给服务器，并打印服务器返回的内容。
 *
 * 启动：  java TcpEchoClient
 */
public class TcpEchoClient {

    private static final String HOST = "localhost";
    private static final int PORT = 8888;

    public static void main(String[] args) {
        System.out.println("[CLIENT] 正在连接 " + HOST + ":" + PORT + " ...");

        try (
            Socket socket = new Socket(HOST, PORT);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);
            BufferedReader console = new BufferedReader(
                    new InputStreamReader(System.in))
        ) {
            System.out.println("[CLIENT] 已连接！");

            // 先打印服务器发来的欢迎信息
            String serverMsg;
            while ((serverMsg = in.readLine()) != null) {
                // 服务器欢迎信息以 [SERVER] 开头，打印完就进入交互循环
                if (serverMsg.startsWith("[SERVER]")) {
                    System.out.println(serverMsg);
                    if (serverMsg.contains("quit 断开连接")) {
                        break;
                    }
                }
            }

            // 交互循环：读用户输入 → 发送 → 接收回声
            String userInput;
            while ((userInput = console.readLine()) != null) {
                out.println(userInput);

                if ("quit".equalsIgnoreCase(userInput.trim())) {
                    // 读取服务器的告别消息
                    String farewell = in.readLine();
                    System.out.println(farewell);
                    break;
                }

                // 读取服务器回声
                String echo = in.readLine();
                System.out.println(echo);
            }

        } catch (ConnectException e) {
            System.err.println("[CLIENT] 无法连接服务器，请确保 TcpEchoServer 已启动。");
        } catch (IOException e) {
            System.err.println("[CLIENT] 连接异常：" + e.getMessage());
        }
    }
}