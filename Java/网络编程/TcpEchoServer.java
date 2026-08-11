import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP 回声服务器 —— 实验室招新示例代码
 *
 * 功能：接收客户端发来的任意文本，原样返回（echo）。
 * 特点：线程池处理并发、支持优雅关闭、命令行配置端口、输入长度限制。
 *
 * 启动：  java TcpEchoServer [端口号]
 * 示例：  java TcpEchoServer
 *        java TcpEchoServer 9999
 * 测试：  telnet localhost 8888
 *        nc localhost 8888
 * 关闭：  在服务端终端输入 shutdown 回车，或 Ctrl+C 强制终止
 */
public class TcpEchoServer {

    private static final int DEFAULT_PORT = 8888;
    private static final int MAX_LINE_LENGTH = 4096; // 单行最大字节数
    private static final int THREAD_POOL_SIZE = 10;  // 最大并发客户端数

    private final int port;
    private final ExecutorService threadPool;
    private final AtomicInteger clientCount = new AtomicInteger(0);
    private volatile boolean running = true;

    public TcpEchoServer(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void start() {
        System.out.println("[SERVER] 回声服务器启动中...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] 监听端口 " + port + "，等待客户端连接...");
            System.out.println("[SERVER] 输入 shutdown 回车可安全关闭服务器");

            // 启动一个线程监听控制台，用于优雅关闭
            startShutdownListener();

            while (running) {
                // 设置 accept 超时，避免关闭时永久阻塞
                serverSocket.setSoTimeout(1000);
                try {
                    Socket clientSocket = serverSocket.accept();
                    int id = clientCount.incrementAndGet();

                    System.out.println("[SERVER] 客户端 #" + id + " 已连接："
                            + clientSocket.getInetAddress().getHostAddress()
                            + "（当前在线：" + clientCount.get() + "）");

                    threadPool.execute(new ClientHandler(clientSocket, id));

                } catch (SocketTimeoutException e) {
                    // accept 超时，回到 while 循环检查 running 标志
                    continue;
                }
            }

        } catch (IOException e) {
            System.err.println("[SERVER] 服务器异常：" + e.getMessage());
        } finally {
            shutdown();
        }
    }

    /**
     * 监听控制台输入，收到 "shutdown" 时优雅关闭服务器。
     */
    private void startShutdownListener() {
        Thread shutdownThread = new Thread(() -> {
            try (BufferedReader console = new BufferedReader(
                    new InputStreamReader(System.in))) {
                String cmd;
                while ((cmd = console.readLine()) != null) {
                    if ("shutdown".equalsIgnoreCase(cmd.trim())) {
                        System.out.println("[SERVER] 收到关闭命令，正在停止服务...");
                        running = false;
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        });
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    /**
     * 优雅关闭：不再接受新连接，等待现有任务完成后释放线程池。
     */
    private void shutdown() {
        System.out.println("[SERVER] 正在关闭线程池...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        System.out.println("[SERVER] 服务器已关闭。");
    }

    // ==================== 内部类：客户端处理器 ====================

    private class ClientHandler implements Runnable {

        private final Socket socket;
        private final int clientId;

        ClientHandler(Socket socket, int clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        socket.getOutputStream(), true) // autoFlush = true
            ) {
                // 发送欢迎信息
                out.println("[SERVER] 欢迎！你已连接到回声服务器（客户端 #" + clientId + "）");
                out.println("[SERVER] 输入任意文本，服务器将原样返回。输入 quit 断开连接。");

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[#" + clientId + "] 收到：" + line);

                    // 长度限制：防止恶意超长输入
                    if (line.length() > MAX_LINE_LENGTH) {
                        out.println("[SERVER] 输入过长（最大 " + MAX_LINE_LENGTH + " 字节），已截断");
                        line = line.substring(0, MAX_LINE_LENGTH);
                    }

                    if ("quit".equalsIgnoreCase(line.trim())) {
                        out.println("[SERVER] 再见！");
                        break;
                    }

                    // 回声 —— 原样返回
                    out.println("[ECHO] " + line);
                }

            } catch (IOException e) {
                System.err.println("[#" + clientId + "] 连接异常：" + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                int remaining = clientCount.decrementAndGet();
                System.out.println("[SERVER] 客户端 #" + clientId + " 已断开，当前在线：" + remaining);
            }
        }
    }

    // ==================== 入口 ====================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println("端口号范围：1-65535");
                    return;
                }
            } catch (NumberFormatException e) {
                System.err.println("无效端口号：" + args[0]);
                return;
            }
        }
        new TcpEchoServer(port).start();
    }
}