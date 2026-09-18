package com.shadow.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** MOD-74.7: deterministic coverage for the native voice turn state machine. */
public class ShadowVoiceStateMachineTest {
    private static final class Recorder implements ShadowVoiceStateMachine.Callback {
        final List<String> events = new ArrayList<>();

        @Override public void onListening() { events.add("listening"); }
        @Override public void onProcessing() { events.add("processing"); }
        @Override public void onBotSpeaking() { events.add("speaking"); }
        @Override public void onIdle() { events.add("idle"); }
        @Override public void stopTtsPlayback() { events.add("stop_tts"); }
    }

    @Test
    public void handsFree_turn_loops_back_to_listening() {
        Recorder r = new Recorder();
        ShadowVoiceStateMachine sm = new ShadowVoiceStateMachine(true, r);

        sm.userStartedListening();
        sm.speechStarted();
        sm.speechEnded();
        sm.ttsStarted();
        sm.ttsFinished();

        assertEquals(ShadowVoiceStateMachine.State.LISTENING, sm.getState());
        assertEquals(
                "listening,processing,speaking,listening",
                String.join(",", r.events)
        );
    }

    @Test
    public void oneShot_turn_ends_idle_after_tts() {
        Recorder r = new Recorder();
        ShadowVoiceStateMachine sm = new ShadowVoiceStateMachine(false, r);

        sm.userStartedListening();
        sm.speechEnded();
        sm.ttsStarted();
        sm.ttsFinished();

        assertEquals(ShadowVoiceStateMachine.State.IDLE, sm.getState());
        assertEquals(
                "listening,processing,speaking,idle",
                String.join(",", r.events)
        );
    }

    @Test
    public void no_tts_response_completes_the_turn() {
        Recorder r = new Recorder();
        ShadowVoiceStateMachine sm = new ShadowVoiceStateMachine(false, r);

        sm.userStartedListening();
        sm.speechEnded();
        sm.responseFinishedWithoutTts();

        assertEquals(ShadowVoiceStateMachine.State.IDLE, sm.getState());
        assertEquals(
                "listening,processing,idle",
                String.join(",", r.events)
        );
    }
}
