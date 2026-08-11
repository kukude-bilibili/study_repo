# TCP 回声服务器 —— 实验室招新示例代码

## 这是什么？

一个最简单的 TCP 回声系统，包含**服务器**和**客户端**两部分。你发什么，服务器就回什么（echo），适合作为 Java 网络编程的入门示例。

---

## 核心知识点

### 服务器端

| 概念 | 一句话解释 |
|------|------------|
| `ServerSocket` | 在端口上"守门"，等待客户端敲门 |
| `Socket` | 客户端敲门后建立的一条"电话线" |
| 多线程 | 每条"电话线"配一个人接听，互不阻塞 |
| `BufferedReader` | 从"电话线"读文字 |
| `PrintWriter` | 往"电话线"写文字 |
| try-with-resources | 用完后自动挂断（关闭资源） |

### 客户端

| 概念 | 一句话解释 |
|------|------------|
| `new Socket(host, port)` | 主动拨号，连接服务器 |
| `System.in` 包装 | 把键盘输入当成"电话线"来读 |
| `ConnectException` | 服务器没开时的友好提示 |

---

## 文件清单

| 文件 | 说明 |
|------|------|
| `TcpEchoServer.java` | 服务器，先启动 |
| `TcpEchoClient.java` | 客户端，后启动，用 Java 写的交互程序 |

---

## 运行步骤

### 1. 编译

```bash
javac TcpEchoServer.java TcpEchoClient.java
```

### 2. 启动服务器（终端 1）

```bash
java TcpEchoServer
```

```
[SERVER] 回声服务器启动中...
[SERVER] 监听端口 8888，等待客户端连接...
```

### 3. 启动客户端（终端 2）

```bash
java TcpEchoClient
```

输入任意内容，回车后看到服务器原样返回。输入 `quit` 断开。

```
[CLIENT] 正在连接 localhost:8888 ...
[CLIENT] 已连接！
[SERVER] 欢迎！你已连接到回声服务器（客户端 #1）
[SERVER] 输入任意文本，服务器将原样返回。输入 quit 断开连接。
hello
[ECHO] hello
你好世界
[ECHO] 你好世界
quit
[SERVER] 再见！
```

> 如果"**懒人专用**"，也可以用 `telnet localhost 8888` 代替客户端程序来测试。

---

## 服务端 ↔ 客户端交互

```
终端 1（服务器）                    终端 2（客户端）
─────────────────                  ─────────────────
java TcpEchoServer                 java TcpEchoClient
  │                                  │
  ├─ new ServerSocket(8888)          ├─ new Socket("localhost", 8888)
  │   ↓ 开始监听                      │   ↓ 发起连接
  ├─ accept() 等到连接 ──────────────┤
  │                                  │
  ├─ new Thread(handler)             ├─ 读欢迎信息 → 打印
  │                                  ├─ 读键盘输入 "hello"
  ├─ readLine() ←── "hello" ────────┤
  ├─ println("[ECHO] hello") ───→   ├─ readLine() → 打印 "[ECHO] hello"
  │                                  │
  │         ... 循环 ...              │         ... 循环 ...
  │                                  │
  ├─ readLine() ←── "quit" ─────────┤
  ├─ println("再见") ───→           ├─ readLine() → 打印 "再见" → 退出
  └─ close()                         └─ close()
```

---

## 完整代码

### TcpEchoServer.java

```java
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP 回声服务器 —— 实验室招新示例代码
 *
 * 功能：接收客户端发来的任意文本，原样返回（echo）。
 * 特点：多线程处理并发客户端，带连接计数，代码短小精悍适合新人阅读。
 *
 * 启动：  java TcpEchoServer
 * 测试：  telnet localhost 8888   （输入任意内容，回车后看到相同内容返回）
 *        nc localhost 8888
 */
public class TcpEchoServer {

    private static final int PORT = 8888;
    private static final AtomicInteger clientCount = new AtomicInteger(0);

    public static void main(String[] args) {
        System.out.println("[SERVER] 回声服务器启动中...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] 监听端口 " + PORT + "，等待客户端连接...");

            while (true) {
                // 阻塞等待客户端连接
                Socket clientSocket = serverSocket.accept();
                int id = clientCount.incrementAndGet();

                System.out.println("[SERVER] 客户端 #" + id + " 已连接："
                        + clientSocket.getInetAddress().getHostAddress());

                // 为每个客户端创建独立线程，保证并发处理
                Thread thread = new Thread(new ClientHandler(clientSocket, id));
                thread.setDaemon(true); // 守护线程，主线程退出时自动回收
                thread.start();
            }

        } catch (IOException e) {
            System.err.println("[SERVER] 服务器异常：" + e.getMessage());
        }
    }

    /**
     * 客户端处理器：每条连接一个实例，负责读取客户端数据并回写。
     */
    private static class ClientHandler implements Runnable {

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
                out.println("[SERVER] 欢迎！你已连接到回声服务器（客户端 #" + clientId + "）");
                out.println("[SERVER] 输入任意文本，服务器将原样返回。输入 quit 断开连接。");

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[#" + clientId + "] 收到：" + line);

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
                System.out.println("[SERVER] 客户端 #" + clientId + " 已断开，当前连接数：" + remaining);
            }
        }
    }
}
```

### TcpEchoClient.java

```java
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
```

---

## 进阶思考

1. **如果不用多线程会怎样？** 第二个客户端必须等第一个断开才能被处理。
2. **`autoFlush = true` 的作用？** 每次 `println` 后自动刷新缓冲区，不写的话客户端可能收不到数据。
3. **`AtomicInteger` 为什么不用 `int`？** 多线程同时读写普通 `int` 会数据错乱，`AtomicInteger` 保证原子操作。
4. **客户端和服务端都用 `try-with-resources`，谁先关？** 服务端 `ClientHandler` 的 try 块结束后自动关 Socket，客户端的 try 块也自动关，双方都安全。
5. **如何扩展？** 可以在 `ClientHandler.run()` 里加入业务逻辑，比如识别命令、查数据库、返回计算结果，就变成了一个真正的应用服务器。

---

## 面试可能问到的问题

- `ServerSocket` 和 `Socket` 的区别？
- `BufferedReader` 和 `InputStreamReader` 的关系？
- 守护线程（daemon thread）是什么？什么时候用？
- try-with-resources 的原理？哪些类可以用？
- 客户端 `new Socket(host, port)` 和服务端 `accept()` 分别做了什么？