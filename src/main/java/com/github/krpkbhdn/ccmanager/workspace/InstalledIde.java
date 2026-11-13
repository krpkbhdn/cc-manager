package com.github.krpkbhdn.ccmanager.workspace;

import java.util.List;

/** An IDE that can be opened: a stable id, a display name and the command prefix (the directory is appended last). */
public record InstalledIde(String id, String name, List<String> command) {}
