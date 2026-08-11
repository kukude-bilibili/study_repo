# TCP 回声服务器 —— 实验室招新示例代码

---

## 目录

- [这是什么？](#这是什么)
- [快速体验（先跑起来）](#快速体验先跑起来)
- [核心知识点](#核心知识点)
- [文件清单](#文件清单)
- [运行步骤](#运行步骤)
- [服务端 ↔ 客户端交互](#服务端--客户端交互)
- [完整代码](#完整代码)
- [常见错误及解决方法](#常见错误及解决方法)
- [进阶思考](#进阶思考)
- [拓展练习](#拓展练习)
- [面试可能问到的问题](#面试可能问到的问题)
- [推荐阅读](#推荐阅读)

---

## 这是什么？

一个最简单的 TCP 回声系统，包含**服务器**和**客户端**两部分。你发什么，服务器就回什么（echo），适合作为 Java 网络编程的入门示例。

> **比喻**：`ServerSocket` 是大楼门卫，`accept()` 是放行一人；`Socket` 是两人之间的专用电话线。门卫守在门口（端口），有人敲门就接通一条电话线，双方通过这条线说话。

---

## 快速体验（先跑起来）

```bash
# 终端 1：编译 + 启动服务器
javac TcpEchoServer.java TcpEchoClient.java
java TcpEchoServer
```

```bash
# 终端 2：启动客户端
java TcpEchoClient
```

输入任意内容，回车，看到服务器回你一模一样的话。输入 `quit` 断开。

```
[CLIENT] 已连接！
[SERVER] 欢迎！你已连接到回声服务器（客户端 #1）
[SERVER] 输入任意文本，服务器将原样返回。输入 quit 断开连接。
hello
[ECHO] hello
quit
[SERVER] 再见！
```

---

## 核心知识点

### 服务器端

| 概念 | 一句话解释 |
|------|------------|
| `ServerSocket` | 在端口上"守门"，等待客户端敲门 |
| `accept()` | **阻塞方法**——没有客户端连接时，主线程会一直卡在这里，直到有连接到来 |
| `Socket` | 客户端敲门后建立的一条专用"电话线" |
| `ExecutorService`（线程池） | 复用固定数量的线程，避免频繁创建销毁线程的开销 |
| `BufferedReader` + `readLine()` | 从"电话线"读一行文字；**客户端断开时 `readLine()` 返回 `null`** |
| `PrintWriter` + `autoFlush` | 往"电话线"写文字；`autoFlush=true` 保证每次 `println` 立即发送，**不设置则数据会积在缓冲区** |
| `AtomicInteger` | 线程安全的计数器，比 `synchronized` 轻量（无锁原子操作） |
| `setSoTimeout()` | 给 `accept()` 设超时，避免优雅关闭时永久阻塞 |

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
| `TcpEchoClient.java` | 客户端，后启动 |

---

## 运行步骤

### 1. 编译

```bash
javac TcpEchoServer.java TcpEchoClient.java
```

### 2. 启动服务器（终端 1）

```bash
java TcpEchoServer [端口号]
```

**示例：**

```bash
java TcpEchoServer              # 默认 8888 端口
java TcpEchoServer 9999         # 指定 9999 端口
```

预期输出：

```
[SERVER] 回声服务器启动中...
[SERVER] 监听端口 8888，等待客户端连接...
[SERVER] 输入 shutdown 回车可安全关闭服务器
```

### 3. 启动客户端（终端 2）

```bash
java TcpEchoClient [主机] [端口]
```

**示例：**

```bash
java TcpEchoClient                          # 默认 localhost:8888
java TcpEchoClient localhost 9999           # 指定端口
java TcpEchoClient 192.168.1.100 8888       # 连接远程服务器
```

### 4. 停止服务器

**方法一（推荐）：** 在服务器终端输入 `shutdown` 回车，等待线程池关闭。

**方法二：** `Ctrl + C` 强制终止。

> 如果 `Ctrl + C` 后端口仍被占用（`BindException`），Windows 执行 `netstat -ano | findstr 8888` 找到 PID 后 `taskkill /PID <PID>`，Linux/Mac 执行 `lsof -i :8888` 后 `kill <PID>`。

### 测试方式

| 方式 | 命令 | 适用系统 |
|------|------|----------|
| Java 客户端 | `java TcpEchoClient` | 所有 |
| telnet | `telnet localhost 8888` | 需启用 telnet 功能 |
| netcat | `nc localhost 8888` | Linux/Mac/WSL |
| PowerShell | `Test-NetConnection localhost -Port 8888` | Windows |

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
  ├─ threadPool.execute(handler)     ├─ 读欢迎信息（[SERVER] 前缀）→ 打印
  │                                  ├─ 读键盘输入 "hello"
  ├─ readLine() ←── "hello" ────────┤
  ├─ 长度检查（≤ 4096）              │
  ├─ println("[ECHO] hello") ───→   ├─ readLine() → 打印 "[ECHO] hello"
  │                                  │
  │         ... 循环 ...              │         ... 循环 ...
  │                                  │
  ├─ readLine() ←── "quit" ─────────┤
  ├─ println("再见") ───→           ├─ readLine() → 打印 "再见" → 退出
  └─ 线程归还线程池                    └─ close()
```

---

## 完整代码

### TcpEchoServer.java

```java
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
            // 设计说明：不依赖"欢迎行数"，通过前缀判断，修改服务端措辞时无需同步改客户端
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
```

---

## 常见错误及解决方法

| 错误 | 含义 | 解决方法 |
|------|------|----------|
| `BindException: Address already in use` | 端口被占用（上次未正常关闭） | 等几秒让系统释放端口，或手动 kill 占用进程 |
| `ConnectException: Connection refused` | 客户端连不上——服务器没启动 | 检查服务器是否已启动，确认端口号一致 |
| `SocketException: Connection reset` | 对方突然断开（如强制关闭客户端） | 检查网络连接，服务器端会自动捕获并打印日志 |
| `Port out of range` | 端口号不在 1-65535 范围内 | 使用合法端口号 |
| 服务器启动后端口被防火墙拦截 | Windows 防火墙阻止 Java 监听端口 | 控制面板 → 防火墙 → 允许 Java 通过，或临时关闭防火墙测试 |

---

## 进阶思考

1. **如果不用线程池会怎样？** 每个客户端 `new Thread`，100 个客户端就创建 100 个线程，创建销毁开销大，且线程数无上限可能导致内存耗尽。
2. **`autoFlush = true` 的作用？** 每次 `println` 后自动刷新缓冲区。如果不设且不手动 `flush()`，数据会滞留在缓冲区，客户端收不到响应。可尝试改为 `false` 并注释掉 `flush()` 验证。
3. **`AtomicInteger` 为什么不用 `int`？** 多线程同时读写普通 `int` 会数据错乱（竞态条件），`AtomicInteger` 基于 CAS 无锁算法保证原子操作，比 `synchronized` 更轻量。
4. **`setSoTimeout(1000)` 是干什么的？** 给 `accept()` 设 1 秒超时，超时后抛出 `SocketTimeoutException`，主循环得以检查 `running` 标志，实现优雅关闭。不设超时的话 `accept()` 永久阻塞，`shutdown` 命令无法生效。
5. **客户端欢迎信息为什么用前缀判断而不是固定行数？** 用 `startsWith("[SERVER]")` 解耦了服务端和客户端——修改服务端欢迎语时无需同步改客户端，更健壮。
6. **如何扩展？** 可以在 `ClientHandler.run()` 里加入业务逻辑，比如识别命令、查数据库、返回计算结果，就变成了一个真正的应用服务器。

---

## 拓展练习

| 等级 | 练习 | 提示 |
|------|------|------|
| 入门 | 把回声改成"全部大写返回" | `line.toUpperCase()` |
| 入门 | 增加行号计数 | 在 `ClientHandler` 中维护一个计数器 |
| 进阶 | 实现"多房间聊天室" | 用 `Map<String, List<Socket>>` 管理房间 |
| 进阶 | 增加心跳检测 | 客户端定时发送 `PING`，服务端回复 `PONG`，超时断开 |
| 进阶 | 引入 JSON 格式交互 | 用 `Gson` 或 `Jackson` 序列化消息 |
| 挑战 | 用 NIO 重写服务端 | 学习 `Selector`、`ServerSocketChannel`，对比 BIO 和 NIO 的区别 |

---

## 面试可能问到的问题

**基础：**
- `ServerSocket` 和 `Socket` 的区别？
- `BufferedReader` 和 `InputStreamReader` 的关系？
- try-with-resources 的原理？哪些类可以用？

**进阶：**
- 如果多个客户端同时连接，服务端如何处理？各个客户端之间会互相阻塞吗？
- `PrintWriter` 和 `BufferedWriter` 有什么区别？什么时候用哪个？
- 如何检测客户端意外断开（如拔网线、强制关机）？
- 如何实现超时自动断开空闲连接？
- `ExecutorService` 线程池 vs `new Thread`，各自适用什么场景？
- `AtomicInteger` 底层是怎么做到线程安全的？（CAS 原理）

---

## 推荐阅读

- [Java 官方教程：Custom Networking](https://docs.oracle.com/javase/tutorial/networking/)
- [BIO / NIO / AIO 对比](https://zhuanlan.zhihu.com/p/54917626)
- [Netty 实战](https://netty.io/) —— 高性能网络框架
- [Java 并发编程实战](https://book.douban.com/subject/10484692/) —— 深入理解线程池与并发