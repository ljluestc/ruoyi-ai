package org.ruoyi.common.chat.domain.dto.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestMcpToolsTest {

    @Test
    void enableMcpTools_defaultIsFalse() {
        ChatRequest request = new ChatRequest();
        assertFalse(request.getEnableMcpTools(), "enableMcpTools should default to false");
    }

    @Test
    void enableMcpTools_canBeEnabled() {
        ChatRequest request = new ChatRequest();
        request.setEnableMcpTools(true);
        assertTrue(request.getEnableMcpTools(), "enableMcpTools should be true after setting");
    }

    @Test
    void enableMcpTools_toggleBackToFalse() {
        ChatRequest request = new ChatRequest();
        request.setEnableMcpTools(true);
        request.setEnableMcpTools(false);
        assertEquals(Boolean.FALSE, request.getEnableMcpTools());
    }
}
