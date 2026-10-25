package com.github.krpkbhdn.ccmanager.update;

import java.net.URI;

/** A published release newer than the running app, with the installer and its SHA-256 file. */
public record AvailableUpdate(String version, String installerName, URI installerUri, URI checksumUri, URI releasePageUri) {}
