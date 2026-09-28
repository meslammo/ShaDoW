package com.shadow.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOD-116: ChatGPT-style Shadow shell built on the existing Shadow Brain/Cloud stack.
 * Brain, memory, image engine and online routing remain server-side.
 */
public final class ChatGPTStyleMainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int FILE_PICK = 1201;
    private static final int CAMERA_PICK = 1202;
    private static final int AUDIO_PICK = 1203;
    private static final int VOICE_PICK = 1204;
    private static final int PHOTO_PICK = 1205;

    private final int BG = Color.rgb(247, 247, 248);
    private final int SURFACE = Color.WHITE;
    private final int TEXT = Color.rgb(32, 33, 35);
    private final int MUTED = Color.rgb(108, 108, 117);
    private final int DIVIDER = Color.rgb(226, 226, 230);
    private final int BLUE = Color.rgb(37, 99, 235);
    private final int THINK_RED = Color.rgb(209, 53, 69);
    private final int GREEN = Color.rgb(38, 135, 86);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();

    private FrameLayout root;
    private LinearLayout messages;
    private EditText input;
    private TextView title;
    private TextView status;
    private TextView sendButton;
    private ShadowCloudClient cloud;
    private TextToSpeech tts;
    private boolean busy;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(SURFACE);
        getWindow().setNavigationBarColor(SURFACE);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        cloud = new ShadowCloudClient(this);
        tts = new TextToSpeech(this, this);
        buildUi();
        assistant("أهلاً يا محمد 👋\nأنا SHADOW.\nاكتب أو اتكلم عادي. كل أمر له مسار مناسب: 🧠 تفكير • 🌐 بحث • 💻 كود • 🎨 صور • 🧮 حساب • ✅ تحقق.");
        checkBackend();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView tv(String text, float size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(size);
        v.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return v;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView menu = tv("☰", 25);
        menu.setTextDirection(View.TEXT_DIRECTION_LTR);
        menu.setGravity(Gravity.CENTER);
        top.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(50)));

        title = tv("SHADOW", 19);
        title.setTextDirection(View.TEXT_DIRECTION_LTR);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));

        status = tv("● Online", 11);
        status.setTextDirection(View.TEXT_DIRECTION_LTR);
        status.setTextColor(GREEN);
        status.setGravity(Gravity.CENTER);
        top.addView(status, new LinearLayout.LayoutParams(dp(75), dp(50)));

        TextView search = tv("⌕", 28);
        search.setTextDirection(View.TEXT_DIRECTION_LTR);
        search.setGravity(Gravity.CENTER);
        top.addView(search, new LinearLayout.LayoutParams(dp(46), dp(50)));

        TextView more = tv("⋮", 27);
        more.setTextDirection(View.TEXT_DIRECTION_LTR);
        more.setGravity(Gravity.CENTER);
        top.addView(more, new LinearLayout.LayoutParams(dp(42), dp(50)));
        page.addView(top);

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        page.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(18), dp(14), dp(18), dp(18));
        scroll.addView(messages);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composerShell = new LinearLayout(this);
        composerShell.setOrientation(LinearLayout.VERTICAL);
        composerShell.setPadding(dp(10), dp(6), dp(10), dp(8));
        composerShell.setBackgroundColor(BG);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(4), dp(8), dp(4));
        composer.setBackground(rounded(SURFACE, 24));
        composer.setElevation(dp(1));

        TextView plus = tv("+", 28);
        plus.setTextDirection(View.TEXT_DIRECTION_LTR);
        plus.setGravity(Gravity.CENTER);
        composer.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(54)));

        input = new EditText(this);
        input.setHint("Message SHADOW");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setMaxLines(6);
        input.setSingleLine(false);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(8), 0, dp(6), 0);
        composer.addView(input, new LinearLayout.LayoutParams(0, dp(54), 1));

        TextView mic = tv("🎙", 22);
        mic.setTextDirection(View.TEXT_DIRECTION_LTR);
        mic.setGravity(Gravity.CENTER);
        composer.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(54)));

        sendButton = tv("↑", 25);
        sendButton.setTextDirection(View.TEXT_DIRECTION_LTR);
        sendButton.setTextColor(BLUE);
        sendButton.setGravity(Gravity.CENTER);
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(46), dp(54)));

        composerShell.addView(composer);

        TextView helper = tv("＋ ملفات/صور/كاميرا/صوت  •  🧠 Think  •  💻 Flutter/Python  •  🌐 Web  •  🔊 Voice", 10);
        helper.setTextColor(MUTED);
        helper.setGravity(Gravity.CENTER);
        helper.setTextDirection(View.TEXT_DIRECTION_LTR);
        composerShell.addView(helper, new LinearLayout.LayoutParams(-1, dp(24)));
        page.addView(composerShell);

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        menu.setOnClickListener(v -> openDrawer());
        search.setOnClickListener(v -> {
            input.requestFocus();
            input.setHint("ابحث أو اكتب لـ SHADOW...");
        });
        more.setOnClickListener(v -> mainMenu());
        plus.setOnClickListener(v -> attachmentMenu(plus));
        sendButton.setOnClickListener(v -> send());
        mic.setOnClickListener(v -> startVoiceInput());
        input.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && "Message SHADOW".equals(input.getText().toString())) {
                input.setText("");
            }
            return false;
        });
        setContentView(root);
    }

    private void openDrawer() {
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(145, 0, 0, 0));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(24), dp(18), dp(18), dp(14));
        drawer.setBackgroundColor(SURFACE);
        drawer.setElevation(dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = tv("SHADOW", 20);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        brand.setTextDirection(View.TEXT_DIRECTION_LTR);
        head.addView(brand, new LinearLayout.LayoutParams(0, dp(56), 1));

        TextView search = tv("⌕", 28);
        search.setTextDirection(View.TEXT_DIRECTION_LTR);
        search.setGravity(Gravity.CENTER);
        head.addView(search, new LinearLayout.LayoutParams(dp(52), dp(56)));
        drawer.addView(head);

        String[][] tools = new String[][]{
                {"▧", "Images"},
                {"▥", "Library"},
                {"□", "Projects"},
                {"▱", "Remote"},
                {"◷", "Scheduled"},
                {"◉", "Plugins"}
        };

        for (String[] item : tools) {
            TextView row = drawerItem(item[0] + "   " + item[1], 17);
            drawer.addView(row, new LinearLayout.LayoutParams(-1, dp(52)));
            row.setOnClickListener(v -> {
                input.setText("افتح " + item[1] + " في SHADOW");
                closeDrawer(overlay);
                send();
            });
        }

        View d = new View(this);
        d.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
        dlp.setMargins(0, dp(12), 0, dp(10));
        drawer.addView(d, dlp);

        drawer.addView(drawerItem("Recent", 12), new LinearLayout.LayoutParams(-1, dp(30)));

        String[] history = new String[]{
                "محادثة إيفا",
                "سرقة 95 مليون يورو",
                "متابعة مشروع Shadow",
                "توضيح قراءة الإيميل",
                "تحديث محرك الصور APK",
                "تحميل نسخة Shadow الشغالة"
        };

        for (String h : history) {
            TextView row = drawerItem(h, 15);
            drawer.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
            row.setOnClickListener(v -> {
                input.setText(h);
                input.setSelection(input.length());
                closeDrawer(overlay);
            });
        }

        Space spacer = new Space(this);
        drawer.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        TextView chatPill = tv("✎   Chat", 16);
        chatPill.setTextColor(Color.WHITE);
        chatPill.setTextDirection(View.TEXT_DIRECTION_LTR);
        chatPill.setGravity(Gravity.CENTER);
        chatPill.setBackground(rounded(BLUE, 28));
        bottom.addView(chatPill, new LinearLayout.LayoutParams(dp(126), dp(52)));

        bottom.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));

        TextView avatar = tv("MM", 13);
        avatar.setTextDirection(View.TEXT_DIRECTION_LTR);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(Color.rgb(152, 89, 177), 28));
        bottom.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        drawer.addView(bottom);

        FrameLayout.LayoutParams dparms = new FrameLayout.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels * .80f), -1, Gravity.LEFT);
        root.addView(drawer, dparms);

        overlay.setOnClickListener(v -> closeDrawer(overlay));
        search.setOnClickListener(v -> {
            closeDrawer(overlay);
            input.requestFocus();
            input.setHint("ابحث في محادثات Shadow...");
        });
        chatPill.setOnClickListener(v -> closeDrawer(overlay));
    }

    private TextView drawerItem(String s, float size) {
        TextView v = tv(s, size);
        v.setTextColor(TEXT);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void closeDrawer(View overlay) {
        ViewGroup parent = (ViewGroup) overlay.getParent();
        if (parent == null) return;
        if (parent.getChildCount() > 1) parent.removeViewAt(parent.getChildCount() - 1);
        parent.removeView(overlay);
    }

    private void mainMenu() {
        PopupWindow p = new PopupWindow(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.setBackground(rounded(SURFACE, 18));

        String[] items = {
                "🎙 SHADOW Deep Male — Original",
                "♀ SHADOW Warm Female — Original",
                "○ SHADOW Neutral — Original",
                "🧠 Think mode",
                "⚙ Diagnostics"
        };
        for (String item : items) {
            TextView row = drawerItem(item, 14);
            box.addView(row, new LinearLayout.LayoutParams(dp(245), dp(46)));
            row.setOnClickListener(v -> {
                p.dismiss();
                if (item.contains("Deep Male")) {
                    setVoiceProfile("deep_male");
                    Toast.makeText(this, "Voice profile: SHADOW Deep Male — Original", Toast.LENGTH_SHORT).show();
                } else if (item.contains("Warm Female")) {
                    setVoiceProfile("warm_female");
                    Toast.makeText(this, "Voice profile: SHADOW Warm Female — Original", Toast.LENGTH_SHORT).show();
                } else if (item.contains("Neutral")) {
                    setVoiceProfile("neutral");
                    Toast.makeText(this, "Voice profile: SHADOW Neutral — Original", Toast.LENGTH_SHORT).show();
                } else if (item.contains("Think")) {
                    input.setText("فكّر بعمق وراجع الافتراضات قبل الإجابة.");
                    input.requestFocus();
                } else {
                    io.submit(() -> {
                        try {
                            String json = cloud.platformStatus();
                            main.post(() -> assistantWithActions("Diagnostics الحالية:\n" + json));
                        } catch (Throwable e) {
                            main.post(() -> assistantWithActions("تعذر جلب Diagnostics: " + String.valueOf(e.getMessage())));
                        }
                    });
                }
            });
        }
        p.setContentView(box);
        p.setWidth(dp(260));
        p.setHeight(dp(255));
        p.setBackgroundDrawable(rounded(SURFACE, 18));
        p.setOutsideTouchable(true);
        p.setFocusable(true);
        p.showAtLocation(root, Gravity.RIGHT | Gravity.TOP, dp(8), dp(58));
    }

    private void setVoiceProfile(String profile) {
        if (tts == null) return;
        try {
            if ("deep_male".equals(profile)) {
                tts.setPitch(.72f);
                tts.setSpeechRate(.92f);
            } else if ("warm_female".equals(profile)) {
                tts.setPitch(1.08f);
                tts.setSpeechRate(.98f);
            } else {
                tts.setPitch(.90f);
                tts.setSpeechRate(.95f);
            }
        } catch (Throwable ignored) {}
    }

    private void attachmentMenu(View anchor) {
        PopupWindow p = new PopupWindow(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.setBackground(rounded(SURFACE, 18));

        String[] items = {"📁 Files", "🖼 Photos", "📷 Camera", "🔊 Audio", "💻 Flutter App", "🐍 Python Tool"};
        for (String item : items) {
            TextView row = drawerItem(item, 15);
            box.addView(row, new LinearLayout.LayoutParams(dp(220), dp(44)));
            row.setOnClickListener(v -> {
                p.dismiss();
                handleAttachment(item);
            });
        }
        p.setContentView(box);
        p.setWidth(dp(235));
        p.setHeight(dp(282));
        p.setBackgroundDrawable(rounded(SURFACE, 18));
        p.setOutsideTouchable(true);
        p.setFocusable(true);
        p.showAsDropDown(anchor, -dp(12), -dp(300));
    }

    private void handleAttachment(String item) {
        if (item.contains("Files")) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, FILE_PICK);
        } else if (item.contains("Photos")) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, PHOTO_PICK);
        } else if (item.contains("Camera")) {
            try {
                startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), CAMERA_PICK);
            } catch (Exception e) {
                Toast.makeText(this, "الكاميرا غير متاحة.", Toast.LENGTH_SHORT).show();
            }
        } else if (item.contains("Audio")) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(i, AUDIO_PICK);
        } else if (item.contains("Flutter")) {
            input.setText("أنشئ تطبيق Flutter كامل بالمجلدات والملفات والكود والاختبارات ثم جهّزه للـbuild.");
            input.setSelection(input.length());
        } else {
            input.setText("أنشئ أداة Python كاملة بالملفات والاختبارات وتشغيلها بشكل آمن أونلاين.");
            input.setSelection(input.length());
        }
    }

    private void startVoiceInput() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم مع SHADOW");
            startActivityForResult(i, VOICE_PICK);
        } catch (Throwable e) {
            Toast.makeText(this, "ميزة التعرف الصوتي غير متاحة على الجهاز حاليًا.", Toast.LENGTH_SHORT).show();
        }
    }

    private byte[] readUriBytes(Uri uri) throws Exception {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("file_open_failed");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == VOICE_PICK) {
            java.util.ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                input.setText(matches.get(0));
                input.setSelection(input.length());
                send();
            }
            return;
        }

        if (requestCode == CAMERA_PICK) {
            Object raw = data.getExtras() == null ? null : data.getExtras().get("data");
            if (raw instanceof android.graphics.Bitmap) {
                android.graphics.Bitmap bitmap = (android.graphics.Bitmap) raw;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out);
                analyzeSelectedImage(out.toByteArray(), "image/jpeg", "حلل الصورة بدقة، واذكر فقط ما يمكن التحقق منه.");
            }
            return;
        }

        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == AUDIO_PICK) {
            io.submit(() -> {
                try {
                    byte[] bytes = readUriBytes(uri);
                    final String text = cloud.transcribeSpeech(bytes, getContentResolver().getType(uri));
                    main.post(() -> {
                        input.setText(text);
                        input.setSelection(input.length());
                        assistantWithActions("تم تحويل الملف الصوتي إلى نص بواسطة مسار الصوت الأونلاين.");
                    });
                } catch (Throwable e) {
                    main.post(() -> assistantWithActions("تعذر تحويل الملف الصوتي أونلاين: " + String.valueOf(e.getMessage())));
                }
            });
        } else if (requestCode == PHOTO_PICK) {
            io.submit(() -> {
                try {
                    byte[] bytes = readUriBytes(uri);
                    final String answer = cloud.analyzeImage(bytes, getContentResolver().getType(uri), "حلل الصورة بدقة، واذكر فقط ما يمكن التحقق منه.");
                    main.post(() -> assistantWithActions(answer));
                } catch (Throwable e) {
                    main.post(() -> assistantWithActions("تعذر تحليل الصورة أونلاين: " + String.valueOf(e.getMessage())));
                }
            });
        } else if (requestCode == FILE_PICK) {
            input.setText("حلّل الملف المرفق: " + (getContentResolver().getType(uri) == null ? "ملف غير معروف" : getContentResolver().getType(uri)));
            input.setSelection(input.length());
            Toast.makeText(this, "تم تجهيز الملف داخل المحادثة.", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeSelectedImage(byte[] bytes, String mime, String prompt) {
        io.submit(() -> {
            try {
                final String answer = cloud.analyzeImage(bytes, mime, prompt);
                main.post(() -> assistantWithActions(answer));
            } catch (Throwable e) {
                main.post(() -> assistantWithActions("تعذر تحليل الصورة أونلاين: " + String.valueOf(e.getMessage())));
            }
        });
    }

    private void send() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty() || "Message SHADOW".equals(text)) return;

        user(text);
        input.setText("");
        final OperationCard op = addOperation(text);
        busy = true;
        sendButton.setText("■");
        status.setTextColor(THINK_RED);
        status.setText("● Thinking");
        classifyAndAnimate(op, text);

        io.submit(() -> {
            try {
                if (isImageRequest(text)) {
                    final String b64 = cloud.generateImage(text);
                    main.post(() -> {
                        op.step("🖼", "Image Engine", true);
                        addImage(b64);
                        op.finish(true, "الصورة رجعت من Image Engine");
                        assistantWithActions("الصورة جاهزة. أقدر أعمل نسخة أدق، أغير المقاس، أو أعدّل التصميم.");
                        finishBusy();
                    });
                } else {
                    final ShadowCloudClient.CloudReply reply = cloud.chat(text, "none");
                    main.post(() -> {
                        op.step("💬", "Conversation Engine", true);
                        op.finish(true, (reply.provider == null ? "online" : reply.provider) + " • " + (reply.model == null ? "model" : reply.model));
                        assistantWithActions(reply.answer == null ? "وصل الطلب من غير نص رد." : reply.answer);
                        speak(reply.answer);
                        finishBusy();
                    });
                }
            } catch (Throwable e) {
                String detail = e.getMessage() == null ? "online_provider_error" : e.getMessage();
                main.post(() -> {
                    op.finish(false, "المسار الأونلاين فشل: " + detail);
                    assistantWithActions("المحرك الأونلاين فشل في الطلب ده. SHADOW لم يستخدم دماغًا أوفلاين بدل الخدمة السحابية.");
                    busy = false;
                    sendButton.setText("↑");
                    status.setTextColor(THINK_RED);
                    status.setText("● Needs retry");
                });
            }
        });
    }

    private void finishBusy() {
        busy = false;
        sendButton.setText("↑");
        status.setTextColor(GREEN);
        status.setText("● Online");
    }

    private void classifyAndAnimate(OperationCard op, String text) {
        String x = text.toLowerCase(Locale.ROOT);
        if (isImageRequest(text)) {
            op.step("🎨", "Visual Director → Image Engine", false);
            main.postDelayed(() -> op.step("🖼", "Render", false), 500);
            main.postDelayed(() -> op.step("✅", "Verify image payload", false), 1100);
        } else if (x.matches(".*(احسب|calculator|calc).*") || x.matches(".*[0-9٠-٩]+\\s*[+\\-*/×÷].*")) {
            op.step("🧮", "Calculator", false);
            main.postDelayed(() -> op.step("✅", "Verify exact result", false), 450);
        } else if (x.matches(".*(github|git hub|repo|كود|برمج|flutter|python|build|apk|تطوير|اصلح).*")) {
            op.step("💻", "Development Engine", false);
            main.postDelayed(() -> op.step("🧪", "Test / Verify", false), 650);
        } else if (x.matches(".*(ابحث|بحث|دور|latest|today|current|news|خبر|أخبار|مصدر|لينك).*")) {
            op.step("🌐", "Web Research", false);
            main.postDelayed(() -> op.step("✅", "Evaluate sources", false), 650);
        } else if (x.matches(".*(تذكر|افتكر|فاكر|memory|احفظ).*")) {
            op.step("🧠", "Memory Engine", false);
            main.postDelayed(() -> op.step("🔐", "Governance check", false), 650);
        } else if (x.matches(".*(فكر|حلل|خطة|قارن|reason|deep|think).*")) {
            op.step("🧠", "Deep Reasoning", false);
            main.postDelayed(() -> op.step("🧠", "Cross-check assumptions", false), 700);
        } else {
            op.step("🧠", "Conversation Engine", false);
            main.postDelayed(() -> op.step("✅", "Verify response", false), 600);
        }
    }

    private boolean isImageRequest(String s) {
        String x = s.toLowerCase(Locale.ROOT)
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا');
        return x.matches(".*(صورة|صوره|صور|image|picture|drawing|artwork|poster|wallpaper).*")
                && x.matches(".*(اعمل|ارسم|صمم|ولد|انشئ|generate|create|draw|design|make|render).*");
    }

    private OperationCard addOperation(String command) {
        OperationCard card = new OperationCard(this, command);
        messages.addView(card.container, new LinearLayout.LayoutParams(-1, -2));
        bottom();
        return card;
    }

    private void user(String s) {
        TextView v = tv(s, 16);
        v.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        v.setGravity(Gravity.RIGHT);
        v.setTextColor(TEXT);
        v.setPadding(dp(14), dp(9), dp(14), dp(9));
        v.setBackground(rounded(Color.rgb(232, 232, 237), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.RIGHT;
        lp.setMargins(0, dp(5), 0, dp(8));
        messages.addView(v, lp);
        bottom();
    }

    private void assistant(String s) {
        TextView v = tv(s, 16);
        v.setTextColor(TEXT);
        v.setPadding(0, dp(8), dp(10), dp(4));
        v.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        messages.addView(v, new LinearLayout.LayoutParams(-1, -2));
        bottom();
    }

    private void assistantWithActions(String s) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(3), 0, dp(7));

        TextView body = tv(s, 16);
        body.setTextColor(TEXT);
        body.setPadding(0, dp(7), dp(8), dp(7));
        body.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        block.addView(body);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView follow = actionButton("↪ Follow up");
        TextView copy = actionButton("⧉");
        TextView like = actionButton("👍");
        TextView dislike = actionButton("👎");
        TextView read = actionButton("🔊");
        TextView share = actionButton("↗");
        TextView more = actionButton("⋮");

        actions.addView(follow);
        actions.addView(copy);
        actions.addView(like);
        actions.addView(dislike);
        actions.addView(read);
        actions.addView(share);
        actions.addView(more);
        hs.addView(actions);
        block.addView(hs, new LinearLayout.LayoutParams(-1, dp(40)));
        messages.addView(block);
        bottom();

        follow.setOnClickListener(v -> {
            input.setText("تابع معايا على آخر نتيجة.");
            input.requestFocus();
        });
        copy.setOnClickListener(v -> copyText(s));
        read.setOnClickListener(v -> speak(s));
        share.setOnClickListener(v -> shareText(s));
        like.setOnClickListener(v -> Toast.makeText(this, "تم تسجيل التفاعل.", Toast.LENGTH_SHORT).show());
        dislike.setOnClickListener(v -> Toast.makeText(this, "تم تسجيل التفاعل.", Toast.LENGTH_SHORT).show());
        more.setOnClickListener(v -> messageMenu(s));
    }

    private TextView actionButton(String text) {
        TextView v = tv(text, 12);
        v.setTextColor(MUTED);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(9), 0, dp(9), 0);
        return v;
    }

    private void messageMenu(String s) {
        PopupWindow p = new PopupWindow(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.setBackground(rounded(SURFACE, 18));

        String[] items = {"نسخ", "مشاركة", "قراءة بصوت", "Follow up", "Think"};
        for (String item : items) {
            TextView row = drawerItem(item, 14);
            box.addView(row, new LinearLayout.LayoutParams(dp(175), dp(42)));
            row.setOnClickListener(v -> {
                p.dismiss();
                if (item.contains("نسخ")) copyText(s);
                else if (item.contains("مشاركة")) shareText(s);
                else if (item.contains("قراءة")) speak(s);
                else if (item.contains("Follow")) {
                    input.setText("تابع وفسّر النتيجة بمزيد من التفاصيل.");
                    input.requestFocus();
                } else {
                    input.setText("فكّر بعمق في الإجابة السابقة وراجعها.");
                    input.requestFocus();
                }
            });
        }
        p.setContentView(box);
        p.setWidth(dp(190));
        p.setHeight(dp(225));
        p.setBackgroundDrawable(rounded(SURFACE, 18));
        p.setOutsideTouchable(true);
        p.setFocusable(true);
        p.showAtLocation(root, Gravity.RIGHT | Gravity.CENTER_VERTICAL, dp(12), 0);
    }

    private void addImage(String b64) {
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(320));
            lp.setMargins(0, dp(6), 0, dp(6));
            messages.addView(image, lp);
            bottom();
        } catch (Throwable e) {
            assistant("رجعت الصورة ببيانات غير قابلة للعرض داخل التطبيق.");
        }
    }

    private void checkBackend() {
        io.submit(() -> {
            boolean ok = false;
            try { ok = cloud.health(); } catch (Throwable ignored) {}
            final boolean online = ok;
            main.post(() -> {
                status.setText(online ? "● Online" : "● Offline link");
                status.setTextColor(online ? GREEN : THINK_RED);
            });
        });
    }

    private void bottom() {
        messages.post(() -> {
            android.view.ViewParent p = messages.getParent();
            if (p instanceof ScrollView) ((ScrollView)p).fullScroll(View.FOCUS_DOWN);
        });
    }

    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("SHADOW", s));
        Toast.makeText(this, "اتنسخ للحافظة.", Toast.LENGTH_SHORT).show();
    }

    private void shareText(String s) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(Intent.createChooser(i, "مشاركة رد SHADOW"));
    }

    private void speak(String s) {
        if (tts == null || s == null || s.trim().isEmpty()) return;
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "shadow_" + System.currentTimeMillis());
    }

    @Override
    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS && tts != null) {
            try { tts.setLanguage(new Locale("ar", "EG")); } catch (Exception ignored) {}
            try { tts.setSpeechRate(.95f); tts.setPitch(.82f); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        io.shutdownNow();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private final class OperationCard {
        final LinearLayout container;
        final TextView header;
        final TextView detail;
        final LinearLayout rows;
        final String command;

        OperationCard(Context ctx, String text) {
            command = text;
            container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(12), dp(9), dp(12), dp(9));
            container.setBackground(rounded(Color.rgb(238, 238, 242), 16));

            LinearLayout head = new LinearLayout(ctx);
            head.setGravity(Gravity.CENTER_VERTICAL);
            header = tv("🧠  SHADOW • Thinking", 13);
            header.setTextColor(THINK_RED);
            header.setTextDirection(View.TEXT_DIRECTION_LTR);
            head.addView(header, new LinearLayout.LayoutParams(0, dp(38), 1));

            TextView collapse = tv("⌄", 20);
            collapse.setTextDirection(View.TEXT_DIRECTION_LTR);
            collapse.setGravity(Gravity.CENTER);
            head.addView(collapse, new LinearLayout.LayoutParams(dp(34), dp(38)));
            container.addView(head);

            detail = tv("جاري تصنيف الأمر واختيار المحرك المناسب…", 12);
            detail.setTextColor(MUTED);
            container.addView(detail);

            rows = new LinearLayout(ctx);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setPadding(0, dp(5), 0, 0);
            container.addView(rows);

            TextView follow = tv("Follow up", 11);
            follow.setTextColor(BLUE);
            follow.setTextDirection(View.TEXT_DIRECTION_LTR);
            follow.setGravity(Gravity.CENTER);
            LinearLayout more = new LinearLayout(ctx);
            more.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            more.addView(follow, new LinearLayout.LayoutParams(dp(80), dp(30)));
            container.addView(more);

            collapse.setOnClickListener(v -> {
                boolean show = rows.getVisibility() != View.VISIBLE;
                rows.setVisibility(show ? View.VISIBLE : View.GONE);
                detail.setText(show ? "جاري التنفيذ والمتابعة…" : "تم طي تفاصيل التنفيذ.");
            });
            follow.setOnClickListener(v -> {
                input.setText("تابع آخر عملية وكمّل من النتيجة الحالية.");
                input.requestFocus();
            });
        }

        void step(String icon, String name, boolean done) {
            TextView row = tv(icon + "   " + name + (done ? "   ✓" : ""), 12);
            row.setTextColor(done ? GREEN : TEXT);
            row.setTextDirection(View.TEXT_DIRECTION_LTR);
            row.setPadding(0, dp(3), 0, dp(3));
            rows.addView(row);
            detail.setText(done ? "تم تسجيل نتيجة من المحرك." : "جاري تنفيذ: " + name);
            bottom();
        }

        void finish(boolean ok, String info) {
            header.setText(ok ? "✓  SHADOW • Completed" : "⚠  SHADOW • Retry needed");
            header.setTextColor(ok ? GREEN : THINK_RED);
            detail.setText(info == null ? "" : info);
        }
    }
}
