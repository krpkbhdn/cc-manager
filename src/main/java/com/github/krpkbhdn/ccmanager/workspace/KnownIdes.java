package com.github.krpkbhdn.ccmanager.workspace;

import static com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem.LINUX;
import static com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem.MACOS;
import static com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem.WINDOWS;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Location.fixed;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Location.installed;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Root.FILESYSTEM;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Root.HOME;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Root.LOCAL_APPDATA;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Root.PROGRAM_FILES;
import static com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Root.PROGRAM_FILES_X86;

import com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The catalogue of IDEs the app can find on its own; the order is the order of the choice list. */
public final class KnownIdes {

    private static final String JETBRAINS = "JetBrains";
    private static final String TOOLBOX_WINDOWS = "JetBrains/Toolbox/scripts";
    private static final String TOOLBOX_LINUX = ".local/share/JetBrains/Toolbox/scripts";
    private static final String TOOLBOX_MACOS = "Library/Application Support/JetBrains/Toolbox/scripts";
    private static final String VISUAL_STUDIO = "Microsoft Visual Studio";
    private static final String VISUAL_STUDIO_LAUNCHER = "Common7/IDE/devenv.exe";

    public static final String INTELLIJ_IDEA_ID = "intellij-idea";

    public static final List<IdeDefinition> ALL = List.of(
            jetBrains(INTELLIJ_IDEA_ID, "IntelliJ IDEA", "idea", JETBRAINS, "IntelliJ IDEA", "idea64.exe",
                    List.of("IntelliJ IDEA", "IntelliJ IDEA CE"), "idea", List.of("intellij-idea-ultimate", "intellij-idea-community")),
            jetBrains("pycharm", "PyCharm", "pycharm", JETBRAINS, "PyCharm", "pycharm64.exe",
                    List.of("PyCharm", "PyCharm CE"), "pycharm", List.of("pycharm-professional", "pycharm-community")),
            jetBrains("webstorm", "WebStorm", "webstorm", JETBRAINS, "WebStorm", "webstorm64.exe",
                    List.of("WebStorm"), "webstorm", List.of("webstorm")),
            jetBrains("phpstorm", "PhpStorm", "phpstorm", JETBRAINS, "PhpStorm", "phpstorm64.exe",
                    List.of("PhpStorm"), "phpstorm", List.of("phpstorm")),
            jetBrains("goland", "GoLand", "goland", JETBRAINS, "GoLand", "goland64.exe",
                    List.of("GoLand"), "goland", List.of("goland")),
            jetBrains("rider", "Rider", "rider", JETBRAINS, "Rider", "rider64.exe",
                    List.of("Rider"), "rider", List.of("rider")),
            jetBrains("clion", "CLion", "clion", JETBRAINS, "CLion", "clion64.exe",
                    List.of("CLion"), "clion", List.of("clion")),
            jetBrains("rubymine", "RubyMine", "rubymine", JETBRAINS, "RubyMine", "rubymine64.exe",
                    List.of("RubyMine"), "rubymine", List.of("rubymine")),
            jetBrains("datagrip", "DataGrip", "datagrip", JETBRAINS, "DataGrip", "datagrip64.exe",
                    List.of("DataGrip"), "datagrip", List.of("datagrip")),
            jetBrains("android-studio", "Android Studio", "studio", "Android", "Android Studio", "studio64.exe",
                    List.of("Android Studio"), "android-studio", List.of("android-studio")),
            vsCodeFamily("vscode", "Visual Studio Code", "code", "Microsoft VS Code", "bin", "Visual Studio Code",
                    List.of("usr/bin", "snap/bin", "usr/share/code/bin")),
            vsCodeFamily("vscode-insiders", "Visual Studio Code Insiders", "code-insiders", "Microsoft VS Code Insiders", "bin",
                    "Visual Studio Code - Insiders", List.of("usr/bin", "snap/bin", "usr/share/code-insiders/bin")),
            vsCodeFamily("cursor", "Cursor", "cursor", "cursor", "resources/app/bin", "Cursor",
                    List.of("usr/bin", "usr/local/bin")),
            vsCodeFamily("windsurf", "Windsurf", "windsurf", "Windsurf", "bin", "Windsurf",
                    List.of("usr/bin", "usr/local/bin")),
            eclipse(),
            visualStudio());

    private KnownIdes() {
    }

    private static IdeDefinition jetBrains(String id, String name, String command, String windowsVendor, String windowsDirectory,
                                           String windowsLauncher, List<String> macApplications, String linuxDirectoryPrefix, List<String> snaps) {
        var windows = List.of(
                fixed(LOCAL_APPDATA, TOOLBOX_WINDOWS, command + ".cmd"),
                installed(PROGRAM_FILES, windowsVendor, windowsDirectory, "bin/" + windowsLauncher),
                installed(LOCAL_APPDATA, "Programs", windowsDirectory, "bin/" + windowsLauncher));
        var linux = new ArrayList<Location>();
        linux.add(fixed(HOME, TOOLBOX_LINUX, command));
        snaps.forEach(snap -> linux.add(fixed(FILESYSTEM, "snap/bin", snap)));
        linux.add(installed(FILESYSTEM, "opt", linuxDirectoryPrefix, "bin/" + command + ".sh"));
        return new IdeDefinition(id, name,
                List.of(command, command + "64", command + ".cmd", command + "64.exe", command + ".sh"), macApplications,
                Map.of(WINDOWS, windows, MACOS, List.of(fixed(HOME, TOOLBOX_MACOS, command)), LINUX, linux));
    }

    private static IdeDefinition vsCodeFamily(String id, String name, String command, String windowsDirectory, String windowsBin,
                                              String macApplication, List<String> linuxDirectories) {
        var windowsLauncher = windowsBin + "/" + command + ".cmd";
        var linux = linuxDirectories.stream().map(directory -> fixed(FILESYSTEM, directory, command)).toList();
        return new IdeDefinition(id, name, List.of(command + ".cmd", command), List.of(macApplication),
                Map.of(WINDOWS, List.of(
                        fixed(LOCAL_APPDATA, "Programs/" + windowsDirectory, windowsLauncher),
                        fixed(PROGRAM_FILES, windowsDirectory, windowsLauncher)),
                        LINUX, linux));
    }

    private static IdeDefinition eclipse() {
        var userInstall = new Location(HOME, "eclipse", List.of("", "eclipse"), "eclipse.exe");
        return new IdeDefinition("eclipse", "Eclipse", List.of("eclipse.exe", "eclipse"), List.of("Eclipse"),
                Map.of(WINDOWS, List.of(installed(PROGRAM_FILES, "", "eclipse", "eclipse.exe"), userInstall),
                        LINUX, List.of(fixed(FILESYSTEM, "opt/eclipse", "eclipse"), fixed(FILESYSTEM, "usr/bin", "eclipse"),
                                fixed(FILESYSTEM, "snap/bin", "eclipse"),
                                new Location(HOME, "eclipse", List.of("", "eclipse"), "eclipse"))));
    }

    private static IdeDefinition visualStudio() {
        return new IdeDefinition("visual-studio", "Visual Studio", List.of("devenv.exe"), List.of(),
                Map.of(WINDOWS, List.of(
                        new Location(PROGRAM_FILES, VISUAL_STUDIO, List.of("", ""), VISUAL_STUDIO_LAUNCHER),
                        new Location(PROGRAM_FILES_X86, VISUAL_STUDIO, List.of("", ""), VISUAL_STUDIO_LAUNCHER))));
    }
}
