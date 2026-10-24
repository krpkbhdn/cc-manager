package com.github.krpkbhdn.ccmanager.notification;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final long PROJECT_ID = 9;

    private final SessionRegistry registry = new SessionRegistry();
    private final NotificationService service = new NotificationService(registry);
    private final List<AppNotification> received = new ArrayList<>();
    private String id;

    @BeforeEach
    void setUp() {
        service.subscribe(received::add);
        id = registry.register(PROJECT_ID);
    }

    private void fire(String name, String json) {
        var template = of(name, json);
        registry.on(new HookEvent(id, template.type(), name, template.payload(), template.receivedAt()));
    }

    @Test
    void initialIdleProducesNothing() {
        assertThat(service.history()).isEmpty();
    }

    @Test
    void workingToIdleIsFinishedWithLastMessage() {
        fire("UserPromptSubmit", "{\"prompt\":\"go\"}");
        fire("Stop", "{\"last_assistant_message\":\"Refactoring complete\"}");

        assertThat(received).singleElement().satisfies(notification -> {
            assertThat(notification.kind()).isEqualTo(NotificationKind.FINISHED);
            assertThat(notification.title()).isEqualTo("Claude finished");
            assertThat(notification.body()).isEqualTo("Finished: Refactoring complete");
            assertThat(notification.projectId()).isEqualTo(PROJECT_ID);
            assertThat(notification.ccmSessionId()).isEqualTo(id);
        });
    }

    @Test
    void needsActionIsReportedOnceWithReasonDespiteFollowUpSignals() {
        fire("UserPromptSubmit", "{}");
        fire("PermissionRequest", "{\"tool_name\":\"Bash\"}");
        fire("Notification", "{\"notification_type\":\"permission_prompt\",\"message\":\"needs you\"}");

        assertThat(received).singleElement().satisfies(notification -> {
            assertThat(notification.kind()).isEqualTo(NotificationKind.NEEDS_ACTION);
            assertThat(notification.body()).isEqualTo("Waiting for permission: Bash");
        });
    }

    @Test
    void stopFailureIsAnErrorWithTheErrorType() {
        fire("UserPromptSubmit", "{}");
        fire("StopFailure", "{\"error\":\"rate_limit\"}");

        assertThat(received).singleElement().satisfies(notification -> {
            assertThat(notification.kind()).isEqualTo(NotificationKind.ERROR);
            assertThat(notification.body()).isEqualTo("rate_limit");
        });
    }

    @Test
    void interruptDoesNotNotify() {
        fire("UserPromptSubmit", "{}");

        registry.markInterrupted(id);

        assertThat(received).isEmpty();
    }

    @Test
    void historyIsNewestLastWithIncreasingIdsAndUnsubscribeStopsDelivery() {
        var unsubscribe = service.subscribe(ignored -> { });
        fire("UserPromptSubmit", "{}");
        fire("Stop", "{}");
        fire("UserPromptSubmit", "{}");
        fire("PermissionRequest", "{\"tool_name\":\"Bash\"}");

        assertThat(service.history()).extracting(AppNotification::kind)
                .containsExactly(NotificationKind.FINISHED, NotificationKind.NEEDS_ACTION);
        assertThat(service.history()).extracting(AppNotification::id).isSorted().doesNotHaveDuplicates();
        unsubscribe.run();
    }

    @Test
    void historyIsCapped() {
        for (var i = 0; i < 250; i++) {
            fire("UserPromptSubmit", "{}");
            fire("Stop", "{}");
            fire("UserPromptSubmit", "{}");
            fire("Stop", "{}");
        }

        assertThat(service.history()).hasSizeLessThanOrEqualTo(200);
    }
}
