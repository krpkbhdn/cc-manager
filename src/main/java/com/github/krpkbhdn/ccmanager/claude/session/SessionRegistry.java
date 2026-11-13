package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Live sessions and their status, derived from hook events and process lifecycle. Thread-safe. */
@Component
@EnableConfigurationProperties(SessionProperties.class)
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final Map<String, SessionState> states = new ConcurrentHashMap<>();
    private final ChangeListeners listeners = new ChangeListeners("Session");
    private final SessionProperties properties;

    private final TranscriptService transcripts;
    private final Clock clock;
    private volatile BiPredicate<String, HookEvent> heldProposal = (ccmSessionId, event) -> false;

    @Autowired
    public SessionRegistry(SessionProperties properties, TranscriptService transcripts) {
        this(properties, transcripts, Clock.systemUTC());
    }

    public SessionRegistry(SessionProperties properties, TranscriptService transcripts, Clock clock) {
        this.properties = properties;
        this.transcripts = transcripts;
        this.clock = clock;
    }

    /** The proposal holder says whether a session still waits for the user on a proposal that this event does not settle (set by it, as it depends on this registry). */
    public void holdActionWhile(BiPredicate<String, HookEvent> proposalHeld) {
        this.heldProposal = proposalHeld;
    }

    /** Creates a live session in IDLE state and returns its ccm id. */
    public String register(long projectId) {
        var ccmSessionId = UUID.randomUUID().toString();
        states.put(ccmSessionId, SessionState.initial(ccmSessionId, projectId, clock.instant()));
        listeners.fire();
        return ccmSessionId;
    }

    /** Process exited: the session leaves the registry. */
    public void markEnded(String ccmSessionId, int exitCode) {
        log.debug("Session {} ended with exit code {}", ccmSessionId, exitCode);
        discard(ccmSessionId);
    }

    /** The user closed the session: it leaves at once so late hooks of the exiting process raise no status or notification. */
    public void discard(String ccmSessionId) {
        if (states.remove(ccmSessionId) != null) {
            listeners.fire();
        }
    }

    /** User pressed Esc in the terminal: Claude fires no Stop on interrupt, so WORKING becomes IDLE here. */
    public void markInterrupted(String ccmSessionId) {
        update(ccmSessionId, StatusReducer::interrupted);
    }

    /** Events of sessions this app did not register are ignored. */
    @EventListener
    public void on(HookEvent event) {
        var topic = topicAfter(event);
        update(event.ccmSessionId(), state -> {
            var next = StatusReducer.reduce(state, event, heldProposal.test(event.ccmSessionId(), event));
            return topic.map(next::withTopic).orElse(next);
        });
    }

    /** Claude writes the generated title to the transcript around prompts and turn ends; tool events never change it. */
    private Optional<String> topicAfter(HookEvent event) {
        var mayCarryTopic = switch (event.type()) {
            case SESSION_START, USER_PROMPT_SUBMIT, STOP -> !event.fromSubagent() && states.containsKey(event.ccmSessionId());
            default -> false;
        };
        return mayCarryTopic ? transcripts.title(event.text("transcript_path")) : Optional.empty();
    }

    public List<SessionSnapshot> sessions() {
        return states.values().stream()
                .map(this::snapshotOf)
                .sorted(Comparator.comparing(SessionSnapshot::startedAt))
                .toList();
    }

    public List<SessionSnapshot> sessions(long projectId) {
        return sessions().stream().filter(session -> session.projectId() == projectId).toList();
    }

    public Optional<SessionSnapshot> find(String ccmSessionId) {
        return Optional.ofNullable(states.get(ccmSessionId)).map(this::snapshotOf);
    }

    /** Max urgency over the project's live sessions; OFFLINE when there are none. */
    public AgentStatus projectStatus(long projectId) {
        return sessions(projectId).stream()
                .map(SessionSnapshot::status)
                .max(Comparator.naturalOrder())
                .orElse(AgentStatus.OFFLINE);
    }

    /** @return unsubscribe handle; callback fires (any thread) after any session or status change */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private SessionSnapshot snapshotOf(SessionState state) {
        return state.toSnapshot(clock.instant(), properties.subagentStaleAfter());
    }

    private void update(String ccmSessionId, UnaryOperator<SessionState> change) {
        if (states.computeIfPresent(ccmSessionId, (id, state) -> change.apply(state)) != null) {
            listeners.fire();
        }
    }
}
