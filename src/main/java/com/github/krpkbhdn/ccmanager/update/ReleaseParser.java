package com.github.krpkbhdn.ccmanager.update;

import java.net.URI;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Reads a GitHub release; a release without both an .msi and its .msi.sha256 is not installable and is skipped. */
final class ReleaseParser {

    private static final String INSTALLER_SUFFIX = ".msi";
    private static final String CHECKSUM_SUFFIX = ".sha256";

    private ReleaseParser() {
    }

    static Optional<AvailableUpdate> newerThan(JsonNode release, ReleaseVersion current) {
        var version = ReleaseVersion.parse(release.path("tag_name").asString(""));
        if (version.isEmpty() || version.get().compareTo(current) <= 0) {
            return Optional.empty();
        }
        var installer = asset(release, INSTALLER_SUFFIX);
        var checksum = installer == null ? null : asset(release, installer.path("name").asString() + CHECKSUM_SUFFIX);
        if (installer == null || checksum == null) {
            return Optional.empty();
        }
        return Optional.of(new AvailableUpdate(
                version.get().toString(),
                installer.path("name").asString(),
                URI.create(installer.path("browser_download_url").asString()),
                URI.create(checksum.path("browser_download_url").asString()),
                URI.create(release.path("html_url").asString())));
    }

    private static JsonNode asset(JsonNode release, String nameSuffix) {
        for (var asset : release.path("assets")) {
            if (asset.path("name").asString("").endsWith(nameSuffix)) {
                return asset;
            }
        }
        return null;
    }
}
