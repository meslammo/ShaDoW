package com.shadow.mobile;

/**
 * MOD-74: Native Shadow voice state machine adapted from the strongest
 * inspected voice-loop implementation. Keeps voice orchestration in Shadow's
 * existing Java/native architecture without importing a separate assistant.
 */
public final class ShadowVoiceStateMachine {
    public enum State {
        IDLE,
        LISTENING,
        PROCESSING,
        BOT_SPEAKING
    }

    public interface Callback {
        void onListening();
        void onProcessing();
        void onBotSpeaking();
        void onIdle();
        void stopTtsPlayback();
    }

    private boolean handsFree;
    private final Callback callback;
    private State state = State.IDLE;
    private long lastTtsStartMs = 0L;

    public ShadowVoiceStateMachine(boolean handsFree, Callback callback) {
        this.handsFree = handsFree;
        this.callback = callback;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized void setHandsFree(boolean enabled) {
        handsFree = enabled;
    }

    public synchronized void userStartedListening() {
        if (state != State.IDLE) return;
        state = State.LISTENING;
        callback.onListening();
    }

    public synchronized void userStoppedListening() {
        if (state == State.IDLE) return;
        state = State.IDLE;
        callback.onIdle();
    }

    public synchronized void speechStarted() {
        switch (state) {
            case BOT_SPEAKING:
                long elapsed = System.currentTimeMillis() - lastTtsStartMs;
                // Ignore the small audio bleed window immediately after TTS starts.
                if (elapsed < 300L) return;
                callback.stopTtsPlayback();
                state = State.LISTENING;
                callback.onListening();
                break;
            case LISTENING:
                callback.onListening();
                break;
            default:
                // Do not begin a new turn while Shadow is still planning/processing.
                break;
        }
    }

    public synchronized void speechEnded() {
        if (state == State.LISTENING || state == State.BOT_SPEAKING) {
            state = State.PROCESSING;
            callback.onProcessing();
        }
    }

    public synchronized void ttsStarted() {
        lastTtsStartMs = System.currentTimeMillis();
        if (state == State.PROCESSING || state == State.LISTENING) {
            state = State.BOT_SPEAKING;
            callback.onBotSpeaking();
        }
    }

    public synchronized void ttsFinished() {
        if (state != State.BOT_SPEAKING) return;
        if (handsFree) {
            state = State.LISTENING;
            callback.onListening();
        } else {
            state = State.IDLE;
            callback.onIdle();
        }
    }

    /** Complete a voice turn when no TTS output was requested. */
    public synchronized void responseFinishedWithoutTts() {
        if (state != State.PROCESSING) return;
        if (handsFree) {
            state = State.LISTENING;
            callback.onListening();
        } else {
            state = State.IDLE;
            callback.onIdle();
        }
    }
}
