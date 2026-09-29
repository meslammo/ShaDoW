package com.shadow.mobile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MOD-74.12: active barge-in monitor.
 *
 * Runs only while SHADOW is speaking. It captures VOICE_COMMUNICATION audio
 * through Android's AEC/NS/AGC when supported, learns a short ambient floor,
 * then detects sustained near-field speech energy. Detection immediately
 * hands control back to ShadowVoiceStateMachine, which stops TTS and opens STT.
 */
public final class ShadowBargeInMonitor {
    public interface Listener {
        void onSpeechDetected();
        void onLevel(float level);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK = 320; // 20 ms
    private static final float MIN_THRESHOLD = 0.010f;
    private static final float NOISE_MULTIPLIER = 2.25f;
    private static final int CALIBRATION_CHUNKS = 10;
    private static final int TRIGGER_CHUNKS = 3;

    private final Context context;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile AudioRecord record;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor ns;
    private AutomaticGainControl agc;

    public ShadowBargeInMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            running.set(false);
            return;
        }

        executor.execute(() -> {
            AudioRecord local = null;
            try {
                int bufferSize = Math.max(
                        AudioRecord.getMinBufferSize(
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT
                        ),
                        CHUNK * 4
                );

                local = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );

                if (local.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.w("ShadowBargeIn", "AudioRecord failed to initialize");
                    return;
                }

                record = local;
                enableEffects(local.getAudioSessionId());
                local.startRecording();

                short[] buffer = new short[CHUNK];
                float noiseFloor = 0.004f;
                int calibration = 0;
                int consecutive = 0;
                long started = System.currentTimeMillis();

                while (running.get()) {
                    int read = local.read(buffer, 0, buffer.length);
                    if (read <= 0) continue;

                    float rms = rms(buffer, read);
                    float normalized = Math.min(1f, rms / 0.18f);
                    listener.onLevel(normalized);

                    if (calibration < CALIBRATION_CHUNKS) {
                        noiseFloor = noiseFloor * 0.75f + rms * 0.25f;
                        calibration++;
                        continue;
                    }

                    float threshold = Math.max(
                            MIN_THRESHOLD,
                            noiseFloor * NOISE_MULTIPLIER
                    );

                    // A few milliseconds after startup can contain route/echo settling noise.
                    if (System.currentTimeMillis() - started < 350L) continue;

                    if (rms >= threshold) {
                        consecutive++;
                    } else {
                        consecutive = Math.max(0, consecutive - 1);
                        noiseFloor = noiseFloor * 0.97f + rms * 0.03f;
                    }

                    if (consecutive >= TRIGGER_CHUNKS) {
                        Log.i("ShadowBargeIn",
                                "speech detected rms=" + rms + " threshold=" + threshold);
                        listener.onSpeechDetected();
                        running.set(false);
                        break;
                    }
                }
            } catch (Throwable t) {
                if (running.get()) {
                    Log.w("ShadowBargeIn", "monitor stopped: " + t.getMessage());
                }
            } finally {
                cleanup(local);
            }
        });
    }

    public void stop() {
        running.set(false);
        AudioRecord r = record;
        if (r != null) {
            try { r.stop(); } catch (Throwable ignored) {}
        }
    }

    public void destroy() {
        stop();
        cleanup(record);
        executor.shutdownNow();
    }

    private void enableEffects(int sessionId) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId);
                if (aec != null) aec.setEnabled(true);
            }
        } catch (Throwable t) {
            Log.w("ShadowBargeIn", "AEC unavailable");
        }

        try {
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId);
                if (ns != null) ns.setEnabled(true);
            }
        } catch (Throwable t) {
            Log.w("ShadowBargeIn", "NS unavailable");
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId);
                if (agc != null) agc.setEnabled(true);
            }
        } catch (Throwable t) {
            Log.w("ShadowBargeIn", "AGC unavailable");
        }
    }

    private float rms(short[] values, int count) {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            float sample = values[i] / 32768.0f;
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / Math.max(1, count));
    }

    private void cleanup(AudioRecord local) {
        try {
            if (local != null && local.getState() == AudioRecord.STATE_INITIALIZED) {
                try { local.stop(); } catch (Throwable ignored) {}
                local.release();
            }
        } catch (Throwable ignored) {
        }
        if (record == local) record = null;
        try { if (aec != null) aec.release(); } catch (Throwable ignored) {}
        try { if (ns != null) ns.release(); } catch (Throwable ignored) {}
        try { if (agc != null) agc.release(); } catch (Throwable ignored) {}
        aec = null;
        ns = null;
        agc = null;
    }
}
