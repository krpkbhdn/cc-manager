package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import tools.jackson.databind.json.JsonMapper;

class HookEventMulticasterTest {

    private final HookEventMulticaster multicaster = new HookEventMulticaster();
    private final List<Object> received = new ArrayList<>();

    private void publish(Object payload) {
        multicaster.multicastEvent(new PayloadApplicationEvent<>(this, payload));
    }

    @Test
    void aFailingListenerDoesNotHideAHookEventFromTheOthers() {
        multicaster.addApplicationListener(event -> {
            throw new IllegalStateException("boom");
        });
        multicaster.addApplicationListener(event -> received.add(((PayloadApplicationEvent<?>) event).getPayload()));
        var event = new HookEvent("s1", HookEventType.of("Stop"), "Stop", JsonMapper.builder().build().createObjectNode(), Instant.now());

        publish(event);

        assertThat(received).containsExactly(event);
    }

    @Test
    void theSpringBeanNameIsTheMulticasterConstant() {
        assertThat(HookEventMulticaster.class.getAnnotation(org.springframework.stereotype.Component.class).value())
                .isEqualTo(org.springframework.context.support.AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME);
    }

    @Test
    void otherEventsKeepSpringsDefaultBehaviour() {
        multicaster.addApplicationListener(event -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(() -> publish("something else")).isInstanceOf(IllegalStateException.class);
    }
}
