// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.tool.impl;

import com.mewcode.tool.Tool;
import com.mewcode.tool.ToolCategory;
import com.mewcode.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * [架构] 文件读取工具 — 位于 Tool 接口实现层的读取通道。
 * 调用链：Agent 决策 → ToolRegistry 路由 → ReadFileTool.execute() → NIO 读取 → 行号格式化。
 * 数据流：args(Map) → execute() → Files.readString() → 分页截取 → 行号标注 → ToolResult。
 * 与 Tool 接口的关系：实现 Tool 接口，归类为 ToolCategory.READ（只读操作），
 * 是 EditFileTool 和 WriteFileTool 的前置依赖——LLM 必须先 ReadFile 再编辑。
 *
 * [设计] 为什么使用 offset/limit 分页而非流式读取？
 * - LLM 的上下文窗口有限，一次返回 2000 行已足够大多数场景。
 * - offset/limit 是 LLM 友好的参数：LLM 可以自己决定"再读更多"。
 * - 流式读取需要维护文件句柄，增加状态管理复杂度。
 * 为什么行号是 1-based 而非 0-based？
 * - 编辑器（VS Code、IntelliJ）的行号都是 1-based，LLM 和用户更习惯。
 * - 但 offset 参数是 0-based（数组索引），这是一个设计不一致——值得注意。
 * 为什么使用 split("\n", -1) 而非 lines()？
 * - split("\n", -1) 保留尾部空行（limit=-1 表示不丢弃尾部空字符串），
 *   lines() 返回的 Stream 需要手动关闭，且不能保证保留尾部空行。
 *
 * [Java] split(regex, limit) 的 limit 参数：
 * - limit < 0：不丢弃尾部空字符串，保留所有行。
 * - limit = 0：丢弃尾部空字符串（默认行为）。
 * - limit > 0：最多分割 limit-1 次。
 * 此处使用 -1 确保行号与原始文件完全对应。
 * 大厂考点：String.split() 和 Pattern.split() 的性能差异？
 * - 单次调用 split() 内部编译 Pattern，多次调用应预编译 Pattern 复用。
 * - 此处只调用一次，性能差异可忽略。
 */
public class ReadFileTool implements Tool {

    private static final String DESCRIPTION = """
            Read a file and return its contents with line numbers.

            Usage notes:
            - The file_path parameter should be an absolute path when possible.//
            - By default reads up to 2000 lines from the beginning of the file.
            - Use offset and limit to read specific parts of large files. Only read what you need.
            - Results are returned with line numbers (1-based) for easy reference.
            - This tool can only read files, not directories. Use Glob to list directory contents.
            - Do NOT re-read a file you just edited to verify — EditFile would have errored if the change failed.""";
//parameter
//英 /pəˈræmɪtə(r)/ 美 /pəˈræmɪtər/
//n.（名词）
//【技术 / 计算机】参数；参量（最常用）
//function parameter 函数参数
//input parameter 输入参数
    @Override
    public String name() {
        return "ReadFile";
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "name", name(),
                "description", description(),
                "input_schema", Map.of(
                        // 输入参数规范
                        "type", "object",
                        // 输入参数描述
                        "properties", Map.of(
                                // 文件路径参数
                                "file_path", Map.of("type", "string", "description", "Absolute or relative path to the file to read"),
                                // 偏移量参数
                                "offset", Map.of("type", "integer", "description", "Line offset to start reading from (0-based)", "default", 0),
                                // 限制取行数参数
                                "limit", Map.of("type", "integer", "description", "Maximum number of lines to read", "default", 2000)
                        ),
                        // 必填参数：file_path
                        "required", List.of("file_path")
                )
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String filePath = stringArg(args, "file_path", "");
        if (filePath.isEmpty()) {
            return ToolResult.error("Error: file_path is required");
        }

        int offset = intArg(args, "offset", 0);
        int limit = intArg(args, "limit", 2000);

        Path path = Path.of(filePath);

        if (!Files.exists(path)) {
            return ToolResult.error("Error: file not found: " + filePath);
        }
        if (Files.isDirectory(path)) {
            return ToolResult.error("Error: not a file: " + filePath);
        }

        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            return ToolResult.error("Error reading file: " + e.getMessage());
        }
//imit=-1，代表不丢弃任何分割后的空字符串，保留全部结果，包括开头、中间、末尾的空串。.那么也就将空出的一行也保留了
//   lines() 返回的 Stream 需要手动关闭，且不能保证保留尾部空行。
//        所以这里使用 split("\n", -1) 而不是 lines()。
        String[] lines = content.split("\n", -1);

        if (offset >= lines.length) {
            return ToolResult.success("");
        }

        // 计算结束行号，确保不超过文件行数
        int end = offset + limit;
        if (end > lines.length) {
            end = lines.length;
        }

        var sb = new StringBuilder();

        for (int i = offset; i < end; i++) {
            if (i > offset) {
                sb.append('\n');
            }
            sb.append(i + 1).append('\t').append(lines[i]);
        }
/*那我为什么不在采购的时候直接读取整个文件,然后直接输出呢?
好问题。两个原因：
1. 文件可能很大，不需要全读
用户说："帮我看看 ReadFileTool.java 第 100-150 行"
→ offset=100, limit=50，只读 50 行，不会浪费 token

如果全读：
→ 一个 500 行的文件，用户只需要 50 行，却浪费了 450 行的 token
→ 更糟的是，读一个 5000 行的文件，可能直接撑爆上下文窗口

### 2. 行号是必需的

LLM 拿到带行号的内容后，可以直接说"第 135 行有问题"，然后精准定位修改。如果直接输出裸文本，LLM 只能说"大概在中间某处"，无法精确引用。

流程对比：

```
你想要的（全读直出）：
  Files.readString(path)  →  直接返回  →  没有行号，无法定位

实际的做法：
  Files.readString(path)  →  split 分行  →  按 offset/limit 截取  →  StringBuilder 加上行号  →  返回
```

**StringBuilder 不是为了"创建内容"，而是为了"格式化"——把原始文本加上行号前缀，让 LLM 和用户都能精确引用。**
* */
        return ToolResult.success(sb.toString());
    }

    private static String stringArg(Map<String, Object> args, String key, String def) {
        var v = args.get(key);
        return v instanceof String s ? s : def;
    }

    private static int intArg(Map<String, Object> args, String key, int def) {
        var v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }
}