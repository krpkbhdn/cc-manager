package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.claude.proposal.FormProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal.Suggestion
import com.github.krpkbhdn.ccmanager.claude.proposal.PlanProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Option
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Question
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.json.JsonMapper

private val mapper = JsonMapper.builder().build()

private fun json(text: String) = mapper.readTree(text)

private val PLAN = """
    # Migrate the state backend to S3

    ## Steps
    - Create the bucket with versioning and a DynamoDB lock table
    - Run `terraform init -migrate-state` against staging first
    - Switch production once the plan shows no drift

    ## Risks
    The lock table must exist before the first apply, or two runs can write the state at once.

    ```
    terraform init -migrate-state
    terraform plan -detailed-exitcode
    ```
""".trimIndent()

private val FORM_SCHEMA = """
    {"type":"object","required":["environment","replicas"],"properties":{
      "environment":{"type":"string","title":"Environment","description":"Where the release goes","enum":["staging","production"]},
      "replicas":{"type":"integer","title":"Replicas","description":"How many instances to start"},
      "ticket":{"type":"string","title":"Ticket","description":"Optional tracking id"},
      "notify":{"type":"boolean","title":"Notify the team","default":true}}}
""".trimIndent()

/** Fake pending proposals of every kind, attached to the preview sessions, answerable so the preview stays interactive. */
class PreviewProposals {
    private val pending = CopyOnWriteArrayList<Proposal>(listOf(questions(), permission(), plan(), form()))
    private val listeners = CopyOnWriteArrayList<Runnable>()

    fun service(): ProposalService {
        val service = mock(ProposalService::class.java)
        `when`(service.pending()).thenAnswer { pending.toList() }
        `when`(service.subscribe(any())).thenAnswer { val listener = it.getArgument<Runnable>(0); listeners += listener; Runnable { listeners -= listener } }
        `when`(service.answer(anyString(), any(ProposalAnswer::class.java))).thenAnswer { settle(it.getArgument(0)) }
        `when`(service.dismiss(anyString())).thenAnswer { settle(it.getArgument(0)) }
        return service
    }

    /** Removes the proposal as if the terminal, a turn end or a timeout had settled it. */
    fun settle(id: String): Boolean {
        val removed = pending.removeIf { it.id() == id }
        listeners.forEach { it.run() }
        return removed
    }

    fun add(proposal: Proposal) {
        pending += proposal
        listeners.forEach { it.run() }
    }

    private fun questions() = QuestionsProposal(
        "q1", "s-agents", Instant.now(), "", mapper.createObjectNode(),
        listOf(
            Question(
                "Which approach should the status reducer take?", "Approach",
                listOf(
                    Option("Pure function", "Reduce the old status and an event into the next status, no state of its own.", "StatusReducer.reduce(\n    AgentStatus current,\n    HookEvent event) {\n  return switch (event.type()) {\n    case STOP -> IDLE;\n    case PRE_TOOL_USE -> WORKING;\n  };\n}"),
                    Option("Stateful service", "Keep a map of sessions and mutate it on every event.", "class StatusTracker {\n  private final Map<String, AgentStatus> byId;\n  void on(HookEvent event) { ... }\n}"),
                    Option("Event sourcing", "Store every event and derive the status on read.", ""),
                ),
                false,
            ),
            Question(
                "Which checks should run before the commit?", "Checks",
                listOf(
                    Option("Unit tests", "./gradlew test", ""),
                    Option("Duplicate scan", "npx jscpd on the changed paths", ""),
                    Option("Graph update", "graphify update . keeps the knowledge graph current", ""),
                ),
                true,
            ),
            Question("Where should the notes go?", "Notes", listOf(Option("docs/", "Next to the protocol notes", ""), Option("CLAUDE.md", "In the architecture section", "")), false),
        ),
    )

    private fun permission() = PermissionProposal(
        "p1", "s-needs", Instant.now(), "", "Bash",
        json("""{"command":"./gradlew test --tests '*NotificationServiceTest' && git status --short","description":"Run the notification tests and show what changed"}"""),
        listOf(
            Suggestion(json("""{"type":"addRules"}"""), "Bash(./gradlew test:*) in this project"),
            Suggestion(json("""{"type":"setMode"}"""), "auto-accept edits for this session"),
        ),
    )

    private fun plan() = PlanProposal("pl1", "s-infra", Instant.now(), "", mapper.createObjectNode(), PLAN, "C:/Users/dev/.claude/plans/s3-backend.md")

    private fun form() = FormProposal("f1", "s-working", Instant.now(), "a1", "deploy-server", "The deploy tool needs a few details before it starts the release.", "form", "", "e1", json(FORM_SCHEMA))
}
