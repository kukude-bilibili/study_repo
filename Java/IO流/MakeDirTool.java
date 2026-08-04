package com.mewcode.tool.impl;

import com.mewcode.tool.Tool;
import com.mewcode.tool.ToolCategory;
import com.mewcode.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MkdirTool implements Tool {

    @Override
    public String name() {
        return "Mkdir";
    }

    @Override
    public String description() {
        return "Create a directory, including any necessary parent directories.";
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.WRITE;
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "name", name(),
            "description", description(),
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "dir_path", Map.of("type", "string", "description", "Path to the directory to create")
                ),
                "required", List.of("dir_path")
            )
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String dirPath = stringArg(args, "dir_path", "");
        if (dirPath.isEmpty()) {
            return ToolResult.error("Error: dir_path is required");
        }

        Path path = Path.of(dirPath);
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                return ToolResult.success("Directory already exists: " + dirPath);
            }
            return ToolResult.error("Error: path exists but is not a directory: " + dirPath);
        }

        try {
            Files.createDirectories(path);
            return ToolResult.success("Successfully created directory: " + dirPath);
        } catch (IOException e) {
            return ToolResult.error("Error creating directory: " + e.getMessage());
        }
    }

    private static String stringArg(Map<String, Object> args, String key, String def) {
        var v = args.get(key);
        return v instanceof String s ? s : def;
    }
}