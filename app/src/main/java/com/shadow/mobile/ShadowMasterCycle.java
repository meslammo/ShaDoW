package com.shadow.mobile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** MOD-85.1: correlated request cycle across the SHADOW master layers. */
public final class ShadowMasterCycle {
    private String request = "";
    private String route = "";
    private final Set<String> visited = new LinkedHashSet<>();
    private boolean active;
    private boolean failed;

    public synchronized void begin(String request, String route) {
        this.request = request == null ? "" : request;
        this.route = route == null ? "" : route;
        visited.clear();
        active = true;
        failed = false;
    }

    public synchronized void record(String coreLayer) {
        if (!active) begin("", "");
        if (coreLayer != null && !coreLayer.trim().isEmpty()) visited.add(coreLayer.trim());
    }

    public synchronized void fail() { failed = true; }
    public synchronized void finish() { active = false; }

    public synchronized boolean complete() {
        if (failed || visited.isEmpty()) return false;
        boolean base = visited.contains("identity")
                && visited.contains("understanding")
                && visited.contains("reasoning")
                && visited.contains("action-security")
                && visited.contains("verification")
                && visited.contains("communication");
        if (!base) return false;
        switch (route) {
            case "development":
            case "github": return visited.contains("development");
            case "companion": return visited.contains("companion");
            case "local-device": return visited.contains("device");
            case "spatial": return visited.contains("spatial");
            case "image": return visited.contains("integration");
            case "chat": return visited.contains("web/personal") || visited.contains("memory");
            default: return true;
        }
    }

    public synchronized String status() {
        return "MASTER CYCLE"
                + "\nrequest=" + request
                + "\nroute=" + route
                + "\nactive=" + active
                + "\nfailed=" + failed
                + "\ncomplete=" + complete()
                + "\nvisited=" + visited;
    }

    public synchronized Set<String> visited() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(visited));
    }
}
