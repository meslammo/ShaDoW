package com.shadow.mobile;

/** MOD-37.9: canonical live process labels for the SHADOW UI. */
public final class ShadowProcessIndicators {
    private ShadowProcessIndicators() {}
    public static String render(String key) {
        if (key == null) return "";
        switch (key) {
            case "thinking": return "🧠 THINKING";
            case "reading": return "📖 READING";
            case "searching": return "🔎 SEARCHING";
            case "analyzing": return "📐 ANALYZING";
            case "writing": return "✍️ WRITING";
            case "executing": return "🛠️ EXECUTING";
            case "testing": return "🧪 TESTING";
            case "designing": return "🎨 DESIGNING";
            case "listening": return "🎙️ LISTENING";
            case "speaking": return "🔊 SPEAKING";
            case "done": return "✅ DONE";
            default: return key.toUpperCase();
        }
    }
}
