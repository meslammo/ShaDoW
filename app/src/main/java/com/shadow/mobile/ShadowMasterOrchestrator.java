package com.shadow.mobile;

import java.util.Locale;

/**
 * MOD-75: single Android-side master routing contract for the 12-Core runtime.
 *
 * Voice is only the transport. Every request is classified into one governed
 * route before execution: chat, local device, GitHub/development, image, or
 * system. This keeps the UI from becoming a second orchestration engine.
 */
public final class ShadowMasterOrchestrator {
    private final ShadowMasterEventBus eventBus;

    public ShadowMasterOrchestrator() { this(new ShadowMasterEventBus()); }
    public ShadowMasterOrchestrator(ShadowMasterEventBus eventBus) { this.eventBus = eventBus == null ? new ShadowMasterEventBus() : eventBus; }
    public ShadowMasterEventBus events() { return eventBus; }
    public enum Route { CHAT, LOCAL_DEVICE, GITHUB, DEVELOPMENT, COMPANION, SPATIAL, IMAGE, SYSTEM }
    public enum Stage { UNDERSTANDING, AUTHENTICATING, PLANNING, EXECUTING, VERIFYING, LEARNING, DONE, FAILED, PAUSED }

    public static final class Plan {
        public final String request;
        public final Route route;
        public final Stage firstStage;
        public final boolean sensitive;
        public final boolean confirmationRequired;

        private Plan(String request, Route route, Stage firstStage, boolean sensitive, boolean confirmationRequired) {
            this.request = request;
            this.route = route;
            this.firstStage = firstStage;
            this.sensitive = sensitive;
            this.confirmationRequired = confirmationRequired;
        }

        public String routeName() {
            switch (route) {
                case LOCAL_DEVICE: return "local-device";
                case GITHUB: return "github";
                case DEVELOPMENT: return "development";
                case COMPANION: return "companion";
                case SPATIAL: return "spatial";
                case IMAGE: return "image";
                case SYSTEM: return "system";
                default: return "chat";
            }
        }

        public String stageLabel() {
            switch (firstStage) {
                case AUTHENTICATING: return "authenticating";
                case PLANNING: return "planning";
                case EXECUTING: return "executing";
                case VERIFYING: return "verifying";
                case LEARNING: return "learning";
                case DONE: return "done";
                case FAILED: return "failed";
                case PAUSED: return "paused";
                default: return "understanding";
            }
        }
    }

    public Plan plan(String raw, boolean authenticated) {
        String request = raw == null ? "" : raw.trim();
        String x = request.toLowerCase(Locale.ROOT);

        if (request.isEmpty()) {
            Plan empty = new Plan("", Route.CHAT, Stage.UNDERSTANDING, false, false);
            eventBus.publish(new ShadowMasterEventBus.Event(ShadowMasterEventBus.Type.PLAN_READY, "", "chat", "empty_input", true));
            return empty;
        }

        Route route;
        if (isImage(x)) route = Route.IMAGE;
        else if (isDevelopment(x)) route = Route.DEVELOPMENT;
        else if (isGithub(x)) route = Route.GITHUB;
        else if (isSpatial(x)) route = Route.SPATIAL;
        else if (isCompanion(x)) route = Route.COMPANION;
        else if (isSystem(x)) route = Route.SYSTEM;
        else if (isLocalDevice(x)) route = Route.LOCAL_DEVICE;
        else route = Route.CHAT;

        boolean sensitive = isSensitive(x);
        boolean confirmation = sensitive && !authenticated;
        Stage first = confirmation ? Stage.AUTHENTICATING
                : (route == Route.CHAT ? Stage.UNDERSTANDING : Stage.PLANNING);

        Plan plan = new Plan(request, route, first, sensitive, confirmation);
        eventBus.publish(new ShadowMasterEventBus.Event(
                ShadowMasterEventBus.Type.ROUTE_SELECTED, request, plan.routeName(), "route_selected", true));
        eventBus.publish(new ShadowMasterEventBus.Event(
                confirmation ? ShadowMasterEventBus.Type.APPROVAL_REQUIRED : ShadowMasterEventBus.Type.PLAN_READY,
                request, plan.routeName(), plan.stageLabel(), !confirmation));
        return plan;
    }

    private static boolean isImage(String x) {
        return containsAny(x, "صمم صورة", "اعمل صورة", "صورة لـ", "generate image",
                "create an image", "design an image");
    }

    private static boolean isDevelopment(String x) {
        return containsAny(x, "طور شادو", "طوّر شادو", "طور نفسك", "طوّر نفسك",
                "كمل شادو", "كمّل شادو", "development agent", "self development",
                "analyze project", "حلل المشروع", "حلّل المشروع", "عدل المشروع",
                "عدّل المشروع", "modify project", "build shadow", "بناء شادو");
    }

    private static boolean isSpatial(String x) {
        return containsAny(x, "شغل الرادار", "شغّل الرادار", "رادار شادو", "كاميرات الطريق",
                "كاميرات السرعة", "الموقع", "موقعى", "موقعي", "location", "gps", "spatial");
    }

    private static boolean isCompanion(String x) {
        return containsAny(x, "companion", "companion device", "الساعة الذكية", "الساعة",
                "watch", "smart home", "البيت الذكي", "السيارة الذكية", "car companion");
    }

    private static boolean isGithub(String x) {
        if (!(containsAny(x, "github", "git hub", "جيت هاب", "جيتهاب", "github.com", "meslammo/shadow"))) return false;
        return containsAny(x, "authorization", "authorize", "connect", "ربط", "اربط", "افصل",
                "disconnect", "repo", "repository", "branch", "فرع", "تطوير", "development",
                "develop", "عدل", "عدّل", "نفذ", "نفّذ", "write", "commit", "push", "pull request");
    }

    private static boolean isSystem(String x) {
        return containsAny(x, "حالة النظام", "حاله النظام", "system status", "status shadow",
                "حالة شادو", "حاله شادو", "governance", "الحوكمة", "الهوية", "identity status",
                "memory status", "حالة الذاكرة", "device status");
    }

    private static boolean isLocalDevice(String x) {
        return containsAny(x, "افتح ", "افتح", "شغل ", "شغّل ", "اقفل ", "اتصل ",
                "اتصل بـ", "كلم ", "call ", "wifi", "واي فاي", "bluetooth", "بلوتوث",
                "الكاميرا", "افتح الصور", "الصور", "المعرض", "الإعدادات", "settings",
                "الرادار", "الموقع", "location", "لوكيشن", "الهاتف", "جهازي",
                "calculator", "الحاسبة", "التقويم", "calendar", "reminder", "تذكير");
    }

    private static boolean isSensitive(String x) {
        return containsAny(x, "delete", "wipe", "payment", "credential", "private key",
                "مفتاح سري", "كلمة السر", "باسورد", "توكن", "حذف نهائي", "مسح كامل",
                "factory reset", "deploy", "publish", "merge", "commit", "push",
                "write", "تعديل github", "نفذ", "نفّذ", "اتصل ", "call ", "رسالة", "message");
    }

    private static boolean containsAny(String x, String... parts) {
        for (String part : parts) if (x.contains(part.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
