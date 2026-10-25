package com.github.krpkbhdn.ccmanager.claude.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanApproval;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.QuestionsAnswer;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.PayloadApplicationEvent;

/**
 * Real claude (haiku) through the whole stack, three tiny prompts: a question set, a Bash approval and a plan approval are each
 * answered through {@link ProposalService} and the following hook payloads must show the answer took effect.
 */
class RealProposalTest {

    private static final Duration WAIT = Duration.ofSeconds(180);
    private static final Duration TRUST_DIALOG_WAIT = Duration.ofSeconds(8);
    private static final long TYPING_DELAY_MILLIS = 8;
    private static final int MODE_CYCLE_ATTEMPTS = 6;
    private static final String SHIFT_TAB = "\u001b[Z";
    private static final String DOWN = "\u001b[B";

    @TempDir
    Path dataDir;

    @TempDir
    Path workDir;

    private final List<HookEvent> events = new CopyOnWriteArrayList<>();
    private final StringBuilder terminal = new StringBuilder();

    @Test
    void answeringProposalsFromTheAppSettlesTheRealPrompts() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            context.addApplicationListener(event -> {
                if (event instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof HookEvent hook) {
                    events.add(hook);
                }
            });
            var projects = context.getBean(ProjectService.class);
            var project = projects.add(workDir);
            projects.selectModel(project.id(), new ModelSelection("haiku", null));
            var proposals = context.getBean(ProposalService.class);
            var registry = context.getBean(SessionRegistry.class);
            var session = context.getBean(ClaudeLauncher.class).launch(project.id(), LaunchMode.NEW);
            drain(session);
            var id = session.ccmSessionId();
            try {
                passTrustDialog(session, registry);
                questionsAreAnswered(session, proposals);
                bashIsApprovedOnce(session, proposals);
                planIsApprovedWithAutoAcceptEdits(session, proposals);
            } finally {
                var plain = terminal.toString().replaceAll("\u001b\\[[0-9;?]*[A-Za-z]", " ").replaceAll("\\s+", " ");
                System.out.println("TERMINAL_TAIL=" + plain.substring(Math.max(0, plain.length() - 2500)));
                session.process().destroyForcibly();
            }
            assertThat(id).isNotEmpty();
        }
    }

    private void questionsAreAnswered(LaunchedSession session, ProposalService proposals) throws Exception {
        submit(session, "Call the AskUserQuestion tool once with two questions. Question 1 (single choice): 'Pick a color' with options Red and Blue. "
                + "Question 2 (multiSelect): 'Pick fruits' with options Apple, Pear and Plum. Do nothing else.\r");
        var proposal = (QuestionsProposal) awaitProposal(proposals, session, QuestionsProposal.class);
        System.out.println("QUESTIONS=" + proposal.questions());
        var single = proposal.questions().stream().filter(q -> !q.multiSelect()).findFirst().orElseThrow();
        var multi = proposal.questions().stream().filter(QuestionsProposal.Question::multiSelect).findFirst().orElseThrow();
        assertThat(proposals.answer(proposal.id(), new QuestionsAnswer(Map.of(
                single.text(), List.of(single.options().get(1).label()),
                multi.text(), List.of(multi.options().get(0).label(), multi.options().get(2).label()))))).isTrue();

        var done = awaitEvent(HookEventType.POST_TOOL_USE, "AskUserQuestion");
        System.out.println("QUESTIONS_POST_TOOL_USE=" + done.payload());
        assertThat(done.payload().toString()).contains(single.options().get(1).label()).contains(multi.options().get(0).label()).contains(multi.options().get(2).label());
        awaitStop();
    }

    /** Shift+Tab until the footer offers "? for shortcuts", which is how the default mode looks; the user's own default may be another. */
    private void switchToDefaultMode(LaunchedSession session) throws Exception {
        for (int attempt = 0; attempt < MODE_CYCLE_ATTEMPTS; attempt++) {
            int from;
            synchronized (terminal) {
                from = terminal.length();
            }
            write(session, SHIFT_TAB);
            Thread.sleep(1000);
            synchronized (terminal) {
                if (terminal.substring(from).contains("for shortcuts")) {
                    return;
                }
            }
        }
    }

    private void bashIsApprovedOnce(LaunchedSession session, ProposalService proposals) throws Exception {
        switchToDefaultMode(session);
        submit(session, "Run exactly this Bash command and nothing else: node -e \"require('fs').writeFileSync('x.txt','1')\"\r");
        var proposal = (PermissionProposal) awaitProposal(proposals, session, PermissionProposal.class);
        System.out.println("PERMISSION=" + proposal.toolName() + " " + proposal.toolInput() + " suggestions=" + proposal.suggestions());
        assertThat(proposal.toolName()).isEqualTo("Bash");
        assertThat(proposals.answer(proposal.id(), PermissionAllow.once())).isTrue();

        var done = awaitEvent(HookEventType.POST_TOOL_USE, "Bash");
        System.out.println("BASH_POST_TOOL_USE=" + done.payload());
        await(() -> Files.exists(workDir.resolve("x.txt")));
        awaitStop();
    }

    private void planIsApprovedWithAutoAcceptEdits(LaunchedSession session, ProposalService proposals) throws Exception {
        submit(session, "/plan Plan a one-line change: create a file y.txt containing 2. Keep the plan to one bullet.");
        var proposal = (PlanProposal) awaitProposal(proposals, session, PlanProposal.class);
        System.out.println("PLAN=" + proposal.plan() + " file=" + proposal.planFilePath());
        var modeAtAsk = events.stream().filter(e -> e.type() == HookEventType.PERMISSION_REQUEST && "ExitPlanMode".equals(e.text("tool_name"))).reduce((a, b) -> b).orElseThrow().text("permission_mode");
        System.out.println("MODE_WHEN_ASKED=" + modeAtAsk);
        assertThat(modeAtAsk).isEqualTo("plan");
        int seen = events.size();
        assertThat(proposals.answer(proposal.id(), new PlanApproval(true))).isTrue();

        await(() -> events.stream().skip(seen).anyMatch(e -> e.ccmSessionId().equals(session.ccmSessionId()) && "acceptEdits".equals(e.text("permission_mode"))));
        System.out.println("MODES_AFTER_APPROVAL=" + events.stream().skip(seen).map(e -> e.type() + ":" + e.text("permission_mode")).toList());
    }

    private Proposal awaitProposal(ProposalService proposals, LaunchedSession session, Class<? extends Proposal> kind) throws InterruptedException {
        await(() -> proposals.pending(session.ccmSessionId()).stream().anyMatch(kind::isInstance));
        return proposals.pending(session.ccmSessionId()).stream().filter(kind::isInstance).findFirst().orElseThrow();
    }

    private HookEvent awaitEvent(HookEventType type, String tool) throws InterruptedException {
        await(() -> events.stream().anyMatch(e -> e.type() == type && tool.equals(e.text("tool_name"))));
        return events.stream().filter(e -> e.type() == type && tool.equals(e.text("tool_name"))).reduce((first, second) -> second).orElseThrow();
    }

    private int stops = 0;

    private void awaitStop() throws InterruptedException {
        int target = ++stops;
        await(() -> events.stream().filter(e -> e.type() == HookEventType.STOP).count() >= target);
    }

    /** The folder-trust dialog defaults to "No, exit": Down then Enter picks "Yes" when it shows up. */
    private void passTrustDialog(LaunchedSession session, SessionRegistry registry) throws Exception {
        long deadline = System.nanoTime() + TRUST_DIALOG_WAIT.toNanos();
        while (System.nanoTime() < deadline && registry.find(session.ccmSessionId()).map(s -> s.claudeSessionId() == null).orElse(true)) {
            Thread.sleep(200);
        }
        if (registry.find(session.ccmSessionId()).map(s -> s.claudeSessionId() == null).orElse(true)) {
            write(session, DOWN);
            Thread.sleep(300);
            write(session, "\r");
        }
        await(() -> registry.find(session.ccmSessionId()).map(s -> s.claudeSessionId() != null).orElse(false));
    }

    private void drain(LaunchedSession session) {
        var thread = new Thread(() -> {
            try (var in = session.process().getInputStream()) {
                var buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    synchronized (terminal) {
                        terminal.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ignored) {
                // process ended
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** Types the text key by key, then Enter: a single write reads as a paste and is not submitted. */
    private static void submit(LaunchedSession session, String text) throws Exception {
        for (char c : text.toCharArray()) {
            write(session, String.valueOf(c));
            Thread.sleep(TYPING_DELAY_MILLIS);
        }
        Thread.sleep(500);
        write(session, "\r");
    }

    private static void write(LaunchedSession session, String text) throws Exception {
        session.process().getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        session.process().getOutputStream().flush();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(condition.getAsBoolean()).as("condition within " + WAIT).isTrue();
    }
}
