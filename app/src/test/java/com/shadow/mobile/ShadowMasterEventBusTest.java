package com.shadow.mobile;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShadowMasterEventBusTest {
    @Test public void publishesAndRetainsBoundedHistory() {
        ShadowMasterEventBus bus = new ShadowMasterEventBus();
        final int[] count = {0};
        bus.subscribe(e -> count[0]++);
        for (int i = 0; i < 300; i++) {
            bus.publish(new ShadowMasterEventBus.Event(
                    ShadowMasterEventBus.Type.INPUT_RECEIVED, "r"+i, "chat", "", true));
        }
        assertEquals(300, count[0]);
        assertEquals(256, bus.snapshot().size());
        assertTrue(bus.snapshot().get(0).request.equals("r44"));
    }
}
