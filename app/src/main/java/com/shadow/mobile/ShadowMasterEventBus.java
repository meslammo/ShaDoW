package com.shadow.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** MOD-75.7: bounded master lifecycle event bus shared by voice/text routing. */
public final class ShadowMasterEventBus {
    public enum Type {
        INPUT_RECEIVED,
        IDENTITY_VERIFIED,
        ROUTE_SELECTED,
        PLAN_READY,
        APPROVAL_REQUIRED,
        ACTION_REQUESTED,
        ACTION_EXECUTED,
        VERIFICATION_RESULT,
        MEMORY_WRITE,
        COMPLETED,
        FAILED,
        PAUSED
    }

    public static final class Event {
        public final long timestampMs;
        public final Type type;
        public final String request;
        public final String route;
        public final String detail;
        public final boolean success;

        public Event(Type type, String request, String route, String detail, boolean success) {
            this.timestampMs = System.currentTimeMillis();
            this.type = type;
            this.request = request == null ? "" : request;
            this.route = route == null ? "" : route;
            this.detail = detail == null ? "" : detail;
            this.success = success;
        }
    }

    public interface Listener {
        void onEvent(Event event);
    }

    private static final int MAX_EVENTS = 256;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ArrayList<Event> history = new ArrayList<>();

    public void subscribe(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void unsubscribe(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public synchronized void publish(Event event) {
        if (event == null) return;
        history.add(event);
        if (history.size() > MAX_EVENTS) {
            history.subList(0, history.size() - MAX_EVENTS).clear();
        }
        for (Listener listener : listeners) {
            try { listener.onEvent(event); } catch (Throwable ignored) {}
        }
    }

    public synchronized List<Event> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized void clear() {
        history.clear();
    }
}
