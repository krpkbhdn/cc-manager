package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CheckMcpHealthApprovalTest {

    @Test
    void checkMcpHealthIsNeverAutoApproved() {
        var endpoint = new AssistantMcpEndpoint(AssistantTools.all(), JsonMapper.builder().build(), mock(ProposalService.class), mock(SessionRegistry.class));

        assertThat(endpoint.autoApprovedToolNames()).doesNotContain("check_mcp_health");
    }
}
