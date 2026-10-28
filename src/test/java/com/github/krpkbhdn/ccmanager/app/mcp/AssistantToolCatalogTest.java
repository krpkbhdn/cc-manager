package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Keeps the protocol doc (section 17) and the bundled help in step with the tool beans. */
class AssistantToolCatalogTest {

    private static final String AUTO_BULLET = "- Auto-approved";
    private static final String PERMISSION_BULLET = "- Need the permission dialog";

    @Test
    void protocolDocListsEveryToolUnderTheBulletOfItsApproval() throws IOException {
        var section = section(Files.readString(Path.of("docs/claude-code-protocol.md"), StandardCharsets.UTF_8));
        var autoStart = section.indexOf(AUTO_BULLET);
        var permissionStart = section.indexOf(PERMISSION_BULLET);
        assertThat(autoStart).isNotNegative();
        assertThat(permissionStart).isGreaterThan(autoStart);
        var auto = section.substring(autoStart, permissionStart);
        var permission = section.substring(permissionStart);

        for (var tool : AssistantTools.all()) {
            var listed = "`" + tool.name() + "`";
            assertThat(tool.autoApproved() ? auto : permission).as(tool.name()).contains(listed);
            assertThat(tool.autoApproved() ? permission : auto).as(tool.name() + " in the wrong bullet").doesNotContain(listed);
        }
    }

    @Test
    void helpMentionsEveryTool() throws IOException {
        var help = Files.readString(Path.of("src/main/resources/assistant/help.md"), StandardCharsets.UTF_8);

        for (var tool : AssistantTools.all()) {
            assertThat(help).as(tool.name()).contains(tool.name());
        }
    }

    private static String section(String document) {
        var start = document.indexOf("## 17.");
        assertThat(start).isNotNegative();
        var end = document.indexOf("\n## ", start + 1);
        return end < 0 ? document.substring(start) : document.substring(start, end);
    }
}
