package com.shadow.mobile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Build;

import java.util.ArrayList;
import java.util.Locale;

/**
 * MOD-74.2: Reusable native STT gateway with partial transcripts.
 *
 * Adapted from the inspected open-source voice recognizer patterns:
 * reuse the recognizer instance across turns, expose partial/final events,
 * and keep on-device recognition when Android provides it.
 */
public final class ShadowVoiceRecognizer {
    public interface Listener {
        void onListening();
        void onText(String text);
        void onError(String message);

        default void onReady() {}
        default void onBeginningOfSpeech() {}
        default void onPartial(String text) {}
        default void onFinal(String text) { onText(text); }
        default void onEndOfSpeech() {}
        default void onAudioLevel(float level) {}
    }

    private final Context context;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private boolean active;
    private boolean destroyed;
    private String language = "ar-EG";

    public ShadowVoiceRecognizer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isAvailable() {
        return !destroyed && SpeechRecognizer.isRecognitionAvailable(context);
    }

    public boolean isOnDeviceAvailable() {
        return !destroyed && Build.VERSION.SDK_INT >= 31
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }

    public synchronized void start(String language) {
        if (destroyed) return;
        this.language = (language == null || language.isEmpty()) ? "ar-EG" : language;
        if (!isAvailable()) {
            listener.onError("خدمة التعرف على الصوت غير متاحة على الجهاز.");
            return;
        }

        try {
            ensureRecognizer();
            // Cancel the previous turn without destroying the underlying binding.
            if (active) {
                try { recognizer.cancel(); } catch (Exception ignored) {}
            }

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, this.language);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, this.language);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, isOnDeviceAvailable());

            active = false;
            recognizer.startListening(intent);
        } catch (Throwable t) {
            active = false;
            listener.onError("التعرف على الصوت غير متاح حالياً.");
            // Destroy only after a hard failure; next start can rebuild it.
            destroyRecognizer();
        }
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;

        recognizer = (Build.VERSION.SDK_INT >= 31 && isOnDeviceAvailable())
                ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                : SpeechRecognizer.createSpeechRecognizer(context);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                active = true;
                listener.onReady();
                listener.onListening();
            }

            @Override public void onBeginningOfSpeech() {
                active = true;
                listener.onBeginningOfSpeech();
            }

            @Override public void onRmsChanged(float rmsdB) {
                // Android RMS is reported in dB. Normalize a practical voice range to 0..1.
                float level = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
                listener.onAudioLevel(level);
            }

            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {
                active = false;
                listener.onEndOfSpeech();
            }

            @Override public void onError(int error) {
                active = false;
                listener.onError(mapError(error));
            }

            @Override public void onResults(Bundle results) {
                active = false;
                ArrayList<String> values = results == null
                        ? null
                        : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty()
                        && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                    String text = values.get(0).trim();
                    listener.onFinal(text);
                } else {
                    listener.onError("مش سامع كلام واضح — قولها تاني.");
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {
                if (partialResults == null) return;
                ArrayList<String> values =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty()
                        && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                    listener.onPartial(values.get(0).trim());
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private String mapError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "مش سامع كلام واضح — قولها تاني.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "التعرف على الصوت مشغول حالياً — جرّب تاني.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "صلاحية الميكروفون مش متاحة لـ SHADOW.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "خدمة الصوت حصل لها خطأ مؤقت.";
            default:
                return "حصل خطأ في التعرف على الصوت (" + error + ").";
        }
    }

    private void destroyRecognizer() {
        active = false;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    public synchronized void stop() {
        active = false;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
    }

    public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        destroyRecognizer();
    }

    public synchronized boolean isActive() {
        return active;
    }
}
