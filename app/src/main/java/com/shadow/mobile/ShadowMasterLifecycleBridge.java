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
    private final ShadowCompanionRegistry companions;
    private final ShadowVerificationLedger verification;
    private final ShadowRecoveryLedger recovery;

    public ShadowMasterLifecycleBridge(
            ShadowMasterEventBus bus,
            ShadowCore core,
            ShadowDevelopmentAgent development,
            ShadowRemoteController remotes,
            ShadowPythonRuntimeBridge python,
            ShadowCompanionRegistry companions,
            ShadowVerificationLedger verification,
            ShadowRecoveryLedger recovery) {
        this.bus = bus;
        this.core = core;
        this.development = development;
        this.remotes = remotes;
        this.python = python;
        this.companions = companions;
        this.verification = verification;
        this.recovery = recovery;
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

        // Core 04: persistent companion/device capability boundary.
        if (route.equals("local-device")) {
            core.governance().companionState("phone", event.type.toString());
            if (companions != null) companions.discover("phone", "android", java.util.Arrays.asList("device.control", "status", "voice"));
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
        } else if (route.equals("spatial")) {
            core.governance().companionState("spatial", event.detail);
            if (companions != null) companions.discover("spatial", "sensor-domain", java.util.Arrays.asList("location.read"));
        } else if (route.equals("development")) {
            core.governance().companionState("development-agent", event.type.toString());
        } else if (route.equals("companion")) {
            core.governance().companionState("companion", event.type.toString());
            if (companions != null) companions.discover("companion", "external", java.util.Arrays.asList("companion.status"));
        }

        // Core 06 + 09: durable verification/recovery evidence.
        if (verification != null && event.type == ShadowMasterEventBus.Type.VERIFICATION_RESULT) {
            verification.record(event.request, route, event.success, event.detail);
        }
        if (recovery != null && event.type == ShadowMasterEventBus.Type.PLAN_READY) {
            recovery.checkpoint(event.request, route, event.detail);
        }
        if (recovery != null && (event.type == ShadowMasterEventBus.Type.FAILED || event.type == ShadowMasterEventBus.Type.PAUSED)) {
            recovery.failure(event.detail);
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
        if (python != null) b.append("Python: ").append(python.status()).append("\n");
        if (companions != null) b.append("Companions: ").append(companions.snapshot()).append("\n");
        if (verification != null) b.append("Verification: ").append(verification.snapshot()).append("\n");
        if (recovery != null) b.append("Recovery: ").append(recovery.status());
        return b.toString();
    }

    public void destroy() {
        bus.unsubscribe(this);
    }
}
