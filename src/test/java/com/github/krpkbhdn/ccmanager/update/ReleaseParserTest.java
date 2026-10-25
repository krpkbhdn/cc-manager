package com.github.krpkbhdn.ccmanager.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReleaseParserTest {

    private static final ReleaseVersion CURRENT = new ReleaseVersion(1, 0, 58);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private String release(String tag, String... assetNames) {
        var assets = new StringBuilder();
        for (var name : assetNames) {
            assets.append("{\"name\":\"").append(name).append("\",\"browser_download_url\":\"https://example.com/").append(name.replace(" ", "%20")).append("\"},");
        }
        var list = assets.isEmpty() ? "" : assets.substring(0, assets.length() - 1);
        return "{\"tag_name\":\"" + tag + "\",\"html_url\":\"https://example.com/release\",\"assets\":[" + list + "]}";
    }

    @Test
    void offersNewerReleaseWithInstallerAndChecksum() {
        var update = ReleaseParser.newerThan(mapper.readTree(release("v1.0.59", "app-1.0.59.msi", "app-1.0.59.msi.sha256")), CURRENT);

        assertThat(update).hasValueSatisfying(found -> {
            assertThat(found.version()).isEqualTo("1.0.59");
            assertThat(found.installerName()).isEqualTo("app-1.0.59.msi");
            assertThat(found.checksumUri().toString()).endsWith("app-1.0.59.msi.sha256");
        });
    }

    @Test
    void ignoresSameAndOlderVersions() {
        assertThat(ReleaseParser.newerThan(mapper.readTree(release("v1.0.58", "a.msi", "a.msi.sha256")), CURRENT)).isEmpty();
        assertThat(ReleaseParser.newerThan(mapper.readTree(release("v1.0.9", "a.msi", "a.msi.sha256")), CURRENT)).isEmpty();
    }

    @Test
    void comparesNumericallyNotAsText() {
        assertThat(ReleaseParser.newerThan(mapper.readTree(release("1.0.100", "a.msi", "a.msi.sha256")), CURRENT)).isPresent();
    }

    @Test
    void skipsReleaseWithoutChecksumOrInstaller() {
        assertThat(ReleaseParser.newerThan(mapper.readTree(release("v1.0.59", "a.msi")), CURRENT)).isEmpty();
        assertThat(ReleaseParser.newerThan(mapper.readTree(release("v1.0.59", "a.dmg")), CURRENT)).isEmpty();
    }

    @Test
    void skipsUnparsableTag() {
        assertThat(ReleaseParser.newerThan(mapper.readTree(release("nightly", "a.msi", "a.msi.sha256")), CURRENT)).isEmpty();
    }

    @Test
    void installerScriptWaitsForProcessThenUpgradesAndRestarts() {
        var msi = Path.of("tmp", "app.msi");
        var exe = Path.of("Apps", "app.exe");

        var script = UpdateInstaller.script(42, msi, exe);

        assertThat(script).contains("PID eq 42").contains("msiexec /i \"" + msi + "\" /passive").contains("start \"\" \"" + exe + "\"");
    }
}
