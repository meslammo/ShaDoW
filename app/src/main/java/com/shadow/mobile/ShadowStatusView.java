package com.shadow.mobile;

import android.content.Context;
import android.widget.TextView;

public final class ShadowStatusView extends TextView {
    public ShadowStatusView(Context context) { super(context); }
    @Override public void setText(CharSequence text, BufferType type) {
        String s = text == null ? "" : text.toString();
        String x = s.toLowerCase();
        if (x.contains("thinking")) s = ShadowProcessIndicators.render("thinking");
        else if (x.contains("reading")) s = ShadowProcessIndicators.render("reading");
        else if (x.contains("search") || x.contains("web")) s = ShadowProcessIndicators.render("searching");
        else if (x.contains("analy")) s = ShadowProcessIndicators.render("analyzing");
        else if (x.contains("writing")) s = ShadowProcessIndicators.render("writing");
        else if (x.contains("execut") || x.contains("phone •")) s = ShadowProcessIndicators.render("executing");
        else if (x.contains("test")) s = ShadowProcessIndicators.render("testing");
        else if (x.contains("design") || x.contains("image")) s = ShadowProcessIndicators.render("designing");
        else if (x.contains("listening")) s = ShadowProcessIndicators.render("listening");
        else if (x.contains("speaking")) s = ShadowProcessIndicators.render("speaking");
        else if (x.contains("done") || x.contains("ready") || x.contains("connected")) s = ShadowProcessIndicators.render("done");
        super.setText(s, type);
    }
}
