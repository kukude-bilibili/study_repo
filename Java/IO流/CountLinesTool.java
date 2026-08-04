package com.mewcode.tool.impl;

import com.mewcode.tool.Tool;
import com.mewcode.tool.ToolCategory;
import com.mewcode.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CountLinesTool implements Tool {

    @Override
    public String name() {
        return "CountLines";
    }

    @Override
    public String description() {
        return "Count the number of lines in a file.";
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
                "type", "object",
                "properties", Map.of(
                    "file_path", Map.of("type", "string", "description", "Path to the file")
                ),
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

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return ToolResult.error("Error: file not found: " + filePath);
        }

        try {
            long lineCount = Files.lines(path).count();
            return ToolResult.success("文件共 " + lineCount + " 行");
        } catch (IOException e) {
            return ToolResult.error("Error reading file: " + e.getMessage());
        }
    }

    private static String stringArg(Map<String, Object> args, String key, String def) {
        var v = args.get(key);
        return v instanceof String s ? s : def;
    }
}