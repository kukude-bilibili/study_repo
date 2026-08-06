**WriteFileTool vs ReadFileTool 核心差异：**

1. category() 返回 WRITE 而非 READ
2. schema 有两个必填参数（file_path + content），而非一个必填 + 两个可选
3. execute() 多了：
   - POSIX 权限检测（supportedFileAttributeViews）
   - 父目录创建（createDirectories + 755 权限）
   - 文件写入（writeString + 644 权限）
   - FileHistory 追踪
4. 没有 stringArg/intArg 辅助方法（只有 stringArg），因为不需要 int 参数

**BashTool vs ReadFileTool 核心差异：**

1. category() 返回 COMMAND 而非 READ
2. schema 参数是 command（必填）+ timeout（可选，默认120）
3. execute() 完全不同：
   - ProcessBuilder 而非 Files API
   - 并行读取 stdout/stderr 防止死锁
   - 超时控制 + destroyForcibly()
   - exitCode 判断成功/失败（而非异常）
   - 返回格式：$ command \n stdout \n stderr \n (exit code N)
4. 有 intArg 辅助方法（和 ReadFileTool 一样）
5. 直接 new ToolResult(...) 而非调用 factory 方法（因为要用 exitCode 动态判断 isError）

Let me focus on the key differences to present concisely.
## WriteFileTool 与 ReadFileTool 的核心差异

**对比 ReadFileTool，WriteFileTool 多了 3 个东西：**

```java
// 1. category 不同
ToolCategory.WRITE  // vs READ

// 2. schema 两个必填参数（vs 一个必填 + 两个可选）
"required", List.of("file_path", "content")  // vs List.of("file_path")

// 3. execute() 里多了这三段（ReadFileTool 没有的）
// ① POSIX 权限检测
boolean posix = path.getFileSystem().supportedFileAttributeViews().contains("posix");

// ② 创建父目录 + 设 755 权限
Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(dirPerms));

// ③ 写入文件 + 设 644 权限
Files.writeString(path, content);
Files.setPosixFilePermissions(path, filePerms);
```

---

## BashTool 与 ReadFileTool 的核心差异

**BashTool 完全不用 Files API，而是用 ProcessBuilder 管理子进程。4 个关键不同：**

```java
// 1. category 不同
ToolCategory.COMMAND  // vs READ，最高权限级别

// 2. execute() 核心流程完全不同
ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
Process process = pb.start();

// 3. 并行读取 stdout/stderr —— 防止死锁（经典面试题）
try (InputStream stdoutStream = process.getInputStream();
     InputStream stderrStream = process.getErrorStream()) {
    byte[] stdoutBytes = stdoutStream.readAllBytes();  // 并行读，不能先后读
    stderr = new String(stderrStream.readAllBytes());
}

// 4. 超时控制 + 强制终止
boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
if (!finished) {
    process.destroyForcibly();  // SIGKILL，不是 destroy() 的 SIGTERM
}

// 5. 返回值用 exitCode 判断成败，而非 try-catch
return new ToolResult(sb.toString(), exitCode != 0);
// ReadFileTool 用 ToolResult.success()/error()，BashTool 直接 new
```

**一句话总结：** ReadFileTool 操作文件内容，WriteFileTool 操作文件系统状态，BashTool 操作操作系统进程。三个工具代表了 Tool 接口的三种典型实现模式——读、写、执行。