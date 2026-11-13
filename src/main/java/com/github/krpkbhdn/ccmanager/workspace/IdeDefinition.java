package com.github.krpkbhdn.ccmanager.workspace;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One known IDE: launcher names looked up on PATH, per-OS install locations and macOS application names.
 * The directory to open is appended by the caller as the last argument.
 */
public record IdeDefinition(String id, String name, List<String> pathLaunchers, List<String> macApplications,
                            Map<OperatingSystem, List<Location>> locations) {

    private static final String WINDOWS_PROGRAM_FILES = "C:\\Program Files";
    private static final String WINDOWS_PROGRAM_FILES_X86 = "C:\\Program Files (x86)";

    /** Base directory a {@link Location} is relative to. */
    public enum Root {
        PROGRAM_FILES, PROGRAM_FILES_X86, LOCAL_APPDATA, HOME, FILESYSTEM;

        Path resolve(HostEnvironment host) {
            return switch (this) {
                case PROGRAM_FILES -> pathOf(host.env("ProgramFiles"), Path.of(WINDOWS_PROGRAM_FILES));
                case PROGRAM_FILES_X86 -> pathOf(host.env("ProgramFiles(x86)"), Path.of(WINDOWS_PROGRAM_FILES_X86));
                case LOCAL_APPDATA -> pathOf(host.env("LOCALAPPDATA"), host.home().resolve("AppData").resolve("Local"));
                case HOME -> host.home();
                case FILESYSTEM -> Path.of("/");
            };
        }

        private static Path pathOf(String value, Path fallback) {
            return value == null || value.isBlank() ? fallback : Path.of(value);
        }
    }

    /**
     * Launcher below {@code root/parent}; each entry of {@code directoryPrefixes} descends one level into the child
     * directories starting with it, newest version first (an empty list means the parent itself).
     */
    public record Location(Root root, String parent, List<String> directoryPrefixes, String launcher) {

        public static Location fixed(Root root, String parent, String launcher) {
            return new Location(root, parent, List.of(), launcher);
        }

        public static Location installed(Root root, String parent, String directoryPrefix, String launcher) {
            return new Location(root, parent, List.of(directoryPrefix), launcher);
        }
    }
}
