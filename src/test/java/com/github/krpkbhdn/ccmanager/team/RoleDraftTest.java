package com.github.krpkbhdn.ccmanager.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoleDraftTest {

    private static RoleDraft withTools(String tools) {
        return new RoleDraft("dev", "d", "p", null, null, tools, false);
    }

    @Test
    void toolListIsNormalizedToCommaSeparatedEntries() {
        assertThat(withTools(" Read,, Grep ,Bash ").tools()).isEqualTo("Read, Grep, Bash");
    }

    @Test
    void blankToolListMeansInheritAll() {
        assertThat(withTools(" , ").tools()).isNull();
        assertThat(withTools(null).tools()).isNull();
    }

    @Test
    void mcpServerWildcardAndToolNamesAreAccepted() {
        assertThat(withTools("Read, mcp__github__*, mcp__plugin_x__search").tools()).isEqualTo("Read, mcp__github__*, mcp__plugin_x__search");
    }

    @Test
    void permissionPatternsAndOddEntriesAreRejected() {
        assertThatThrownBy(() -> withTools("Bash(git *)")).hasMessageContaining("Bash(git *)");
        assertThatThrownBy(() -> withTools("Read Write")).hasMessageContaining("Not a valid tool");
        assertThatThrownBy(() -> withTools("Read*")).hasMessageContaining("Not a valid tool");
        assertThatThrownBy(() -> withTools("-Read")).hasMessageContaining("Not a valid tool");
    }

    @Test
    void withLeadChangesOnlyTheLeadFlag() {
        var role = withTools("Read");

        assertThat(role.withLead(true)).isEqualTo(new RoleDraft("dev", "d", "p", null, null, "Read", true));
    }
}
