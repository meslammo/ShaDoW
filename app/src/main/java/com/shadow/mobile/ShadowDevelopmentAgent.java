package com.shadow.mobile;

import android.content.Context;
import java.util.Locale;

public final class ShadowDevelopmentAgent {
    public enum State { IDLE, PLANNING, AWAITING_APPROVAL, EXECUTING, VERIFYING }
    private final ShadowProjectMemory memory;
    private State state = State.IDLE;
    private String pendingPlan = "";

    public ShadowDevelopmentAgent(Context context) {
        memory = new ShadowProjectMemory(context.getApplicationContext());
        memory.setProject("SHADOW", "Phone-first assistant with cloud intelligence, tools and project-aware development workflow.");
    }
    public boolean isDevelopmentIntent(String input) {
        String s = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return s.contains("project") || s.contains("development agent") || s.contains("analyze project")
                || s.contains("analyze files") || s.contains("modify project") || s.contains("rollback");
    }
    public String plan(String request) {
        state = State.AWAITING_APPROVAL;
        pendingPlan = "Analyze project -> identify affected files -> propose changes -> run tests -> create release.\nRequest: " + request;
        memory.record("plan", pendingPlan);
        return pendingPlan + "\nApproval is required before execution.";
    }
    public String approve() {
        if (state != State.AWAITING_APPROVAL) return "No pending plan.";
        state = State.EXECUTING;
        memory.record("approval", "approved");
        return "Plan approved. Safe execution and verification are ready.";
    }
    public String status() { return "Development Agent: " + state + "\n" + memory.snapshot(); }
    public ShadowProjectMemory memory() { return memory; }
    public State state() { return state; }
}
