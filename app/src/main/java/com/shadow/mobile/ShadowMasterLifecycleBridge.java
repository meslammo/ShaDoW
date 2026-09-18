package com.shadow.mobile;

/**
 * MOD-75: fan-out bridge from the master lifecycle bus into the existing
 * governance, development memory, companion/device boundaries and Python 12-Core runtime.
 */
public final class ShadowMasterLifecycleBridge implements ShadowMasterEventBus.Listener {
    private final ShadowMasterEventBus bus;
    private final ShadowCore core;
    private final ShadowDevelopmentAgent development;
    private final ShadowRemoteController remotes;
    private final ShadowPythonRuntimeBridge python;

    public ShadowMasterLifecycleBridge(
            ShadowMasterEventBus bus,
            ShadowCore core,
            ShadowDevelopmentAgent development,
            ShadowRemoteController remotes,
            ShadowPythonRuntimeBridge python) {
        this.bus = bus;
        this.core = core;
        this.development = development;
        this.remotes = remotes;
        this.python = python;
        this.bus.subscribe(this);
    }

    @Override public void onEvent(ShadowMasterEventBus.Event event) {
        String route = event.route == null ? "" : event.route;
        String detail = event.type + " | " + route + " | " + event.detail + " | ok=" + event.success;

        // Core 07: shared governance journal.
        core.governance().record("MASTER BUS | " + detail + " | request=" + event.request);

        // Core 08 + Core 11: project/development memory follows the same lifecycle.
        if (development != null && development.memory() != null) {
            development.memory().record("master_bus", detail + " | " + event.request);
        }

        // Core 04: companion boundary records the currently active routed capability.
        if (route.equals("local-device")) {
            core.governance().companionState("phone", event.type.toString());
            if (event.request.toLowerCase(java.util.Locale.ROOT).contains("موقع")
                    || event.request.toLowerCase(java.util.Locale.ROOT).contains("location")
                    || event.request.toLowerCase(java.util.Locale.ROOT).contains("رادار")) {
                core.governance().companionState("spatial", "ACTIVE");
            }
        } else if (route.equals("github")) {
            core.governance().companionState("github", event.type.toString());
        } else if (route.equals("chat")) {
            core.governance().companionState("cloud-ai", event.type.toString());
        } else if (route.equals("image")) {
            core.governance().companionState("image-provider", event.type.toString());
        }

        // Core 10/12: record the cross-runtime orchestration handoff.
        if (python != null && event.type == ShadowMasterEventBus.Type.ROUTE_SELECTED) {
            core.governance().record("12-CORE ROUTE HANDOFF | " + route);
        }
    }

    public String status() {
        StringBuilder b = new StringBuilder();
        b.append("MASTER BUS\nEvents: ").append(bus.snapshot().size()).append("\n");
        b.append(core.governance().status()).append("\n\n");
        if (development != null) b.append(development.status()).append("\n\n");
        if (remotes != null) b.append("Remote: ").append(remotes.summary()).append("\n");
        if (python != null) b.append("Python: ").append(python.status());
        return b.toString();
    }

    public void destroy() {
        bus.unsubscribe(this);
    }
}
