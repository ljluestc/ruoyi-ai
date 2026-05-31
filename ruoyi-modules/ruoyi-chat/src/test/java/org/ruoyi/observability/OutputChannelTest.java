package org.ruoyi.observability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputChannelTest {

    @Test
    void drain_shouldEmitMessagesInOrderUntilComplete() throws Exception {
        OutputChannel channel = new OutputChannel();
        List<String> received = new ArrayList<>();

        channel.send("A");
        channel.send("B");
        channel.complete();

        channel.drain(received::add);

        assertEquals(List.of("A", "B"), received);
        assertTrue(channel.isCompleted());
    }

    @Test
    void drain_shouldThrowWhenCompletedWithError() {
        OutputChannel channel = new OutputChannel();
        AtomicBoolean sawErrorChunk = new AtomicBoolean(false);

        channel.send("before-error");
        channel.completeWithError(new IllegalStateException("tool failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            channel.drain(chunk -> {
                if (chunk.contains("错误")) {
                    sawErrorChunk.set(true);
                }
            })
        );

        assertTrue(exception.getMessage().contains("Agent 执行出错"));
        assertTrue(sawErrorChunk.get(), "Expected error chunk to be emitted before exception");
        assertTrue(channel.isCompleted());
    }
}
