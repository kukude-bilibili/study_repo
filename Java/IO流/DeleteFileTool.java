package com.mewcode.tool.impl;

import com.mewcode.tool.Tool;
import com.mewcode.tool.ToolCategory;
import com.mewcode.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DeleteFileTool implements Tool {

    @Override
    public String name() {
        return "DeleteFile";
    }

    @Override
    public String description() {
        return "Delete a file at the specified path. Does not delete directories.";
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
                    "file_path", Map.of("type", "string", "description", "Path to the file to delete")
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
        if (Files.isDirectory(path)) {
            return ToolResult.error("Error: not a file (use a directory tool): " + filePath);
        }

        try {
            Files.delete(path);
            return ToolResult.success("Successfully deleted: " + filePath);
        } catch (IOException e) {
            return ToolResult.error("Error deleting file: " + e.getMessage());
        }
    }

    private static String stringArg(Map<String, Object> args, String key, String def) {
        var v = args.get(key);
        return v instanceof String s ? s : def;
    }
}