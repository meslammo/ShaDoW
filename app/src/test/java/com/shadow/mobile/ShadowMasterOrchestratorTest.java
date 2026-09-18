package com.shadow.mobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShadowMasterOrchestratorTest {
    private final ShadowMasterOrchestrator router = new ShadowMasterOrchestrator();

    @Test public void routesGithubSeparatelyFromChat() {
        ShadowMasterOrchestrator.Plan p = router.plan("افتح GitHub repo meslammo/ShaDoW", true);
        assertEquals(ShadowMasterOrchestrator.Route.GITHUB, p.route);
    }

    @Test public void routesPhoneCommandsToLocalDevice() {
        ShadowMasterOrchestrator.Plan p = router.plan("افتح إعدادات WiFi", true);
        assertEquals(ShadowMasterOrchestrator.Route.LOCAL_DEVICE, p.route);
    }

    @Test public void normalConversationStaysChat() {
        ShadowMasterOrchestrator.Plan p = router.plan("عامل ايه يا شادو؟", true);
        assertEquals(ShadowMasterOrchestrator.Route.CHAT, p.route);
        assertTrue(p.firstStage == ShadowMasterOrchestrator.Stage.UNDERSTANDING);
    }

    @Test public void sensitiveRequestWithoutAuthStopsAtAuthentication() {
        ShadowMasterOrchestrator.Plan p = router.plan("نفذ التعديل وادفع commit", false);
        assertTrue(p.sensitive);
        assertTrue(p.confirmationRequired);
        assertEquals(ShadowMasterOrchestrator.Stage.AUTHENTICATING, p.firstStage);
    }
}
