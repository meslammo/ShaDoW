package com.shadow.mobile;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** MOD-46.17: approved local project execution layer for the Development Engine. */
public final class ShadowDevelopmentExecution {
    private final ShadowWorkspace workspace;

    public ShadowDevelopmentExecution(Context context) {
        workspace = new ShadowWorkspace(context.getApplicationContext());
    }

    public Result executeApproved(String relativePath, String content, boolean approved) {
        if (!approved) return new Result(false, "approval_required", "Explicit approval is required before writing project files.");
        try {
            String path = workspace.writeText(relativePath, content);
            return new Result(true, "written", path);
        } catch (Exception e) {
            return new Result(false, "write_failed", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public Result inspect(String relativePath) {
        try {
            String text = workspace.readText(relativePath);
            return new Result(true, "read", "bytes=" + text.getBytes(StandardCharsets.UTF_8).length + ", lines=" + text.split("\\R", -1).length);
        } catch (Exception e) {
            return new Result(false, "read_failed", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public Result verifyWorkspace() {
        try {
            List<String> files = workspace.listFiles();
            return new Result(true, "verified", "workspace_files=" + files.size());
        } catch (Exception e) {
            return new Result(false, "verify_failed", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public Result backup(String label) {
        try { return new Result(true, "backup", workspace.backup(label)); }
        catch (Exception e) { return new Result(false, "backup_failed", e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    public static final class Result {
        public final boolean ok;
        public final String action;
        public final String detail;
        Result(boolean ok, String action, String detail) { this.ok = ok; this.action = action; this.detail = detail == null ? "" : detail; }
        @Override public String toString() { return action + ": " + detail; }
    }
}
