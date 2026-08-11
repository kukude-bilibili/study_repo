import java.io.*;
import java.net.*;

/**
 * TCP 回声客户端 —— 与 TcpEchoServer 配套
 *
 * 功能：连接回声服务器，从控制台读取用户输入发送给服务器，并打印服务器返回的内容。
 *
 * 启动：  java TcpEchoClient [主机] [端口]
 * 示例：  java TcpEchoClient
 *        java TcpEchoClient localhost 9999
 *        java TcpEchoClient 192.168.1.100 8888
 */
public class TcpEchoClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("无效端口号：" + args[1]);
                return;
            }
        }

        System.out.println("[CLIENT] 正在连接 " + host + ":" + port + " ...");

        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);
            BufferedReader console = new BufferedReader(
                    new InputStreamReader(System.in))
        ) {
            System.out.println("[CLIENT] 已连接！");

            // 读取欢迎信息：服务端以 [SERVER] 前缀发送，读完即进入交互循环
            // 设计说明：不依赖"欢迎行数"，而是通过前缀判断，修改服务端措辞时无需同步改客户端
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("[SERVER]")) {
                    System.out.println(line);
                } else {
                    // 如果读到非 [SERVER] 行（如残留回声），打印后退出欢迎阶段
                    if (line.startsWith("[ECHO]")) {
                        System.out.println(line);
                    }
                    break;
                }
            }

            // 交互循环：读用户输入 → 发送 → 接收回声
            String userInput;
            while ((userInput = console.readLine()) != null) {
                out.println(userInput);

                if ("quit".equalsIgnoreCase(userInput.trim())) {
                    // 读取服务器的告别消息
                    String farewell = in.readLine();
                    if (farewell != null) {
                        System.out.println(farewell);
                    }
                    break;
                }

                // 读取服务器回声
                String echo = in.readLine();
                if (echo != null) {
                    System.out.println(echo);
                }
            }

        } catch (ConnectException e) {
            System.err.println("[CLIENT] 无法连接服务器，请确保 TcpEchoServer 已启动。");
        } catch (IOException e) {
            System.err.println("[CLIENT] 连接异常：" + e.getMessage());
        }
    }
}