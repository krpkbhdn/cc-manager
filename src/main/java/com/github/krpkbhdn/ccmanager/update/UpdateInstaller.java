package com.github.krpkbhdn.ccmanager.update;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** Windows only: a detached script waits for this process to exit, runs the per-user MSI upgrade and starts the app again. */
@Component
class UpdateInstaller {

    private static final String APP_PATH_PROPERTY = "jpackage.app-path";

    private final HostEnvironment host;

    UpdateInstaller(HostEnvironment host) {
        this.host = host;
    }

    boolean supported() {
        return host.os() == HostEnvironment.OperatingSystem.WINDOWS && System.getProperty(APP_PATH_PROPERTY) != null;
    }

    /** @throws IllegalStateException when the installer script cannot be started */
    void launchAfterExit(Path msi) {
        var appExecutable = System.getProperty(APP_PATH_PROPERTY);
        if (appExecutable == null) {
            throw new IllegalStateException("The app was not started from an installed package");
        }
        try {
            var script = Files.createTempFile("cc-manager-update", ".cmd");
            Files.writeString(script, script(ProcessHandle.current().pid(), msi, Path.of(appExecutable)));
            CommandRunner.startDetached(List.of("cmd.exe", "/c", "start", "\"\"", "/min", script.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the installer: " + e.getMessage(), e);
        }
    }

    static String script(long pid, Path msi, Path appExecutable) {
        return """
                @echo off
                :wait
                tasklist /FI "PID eq %d" 2>nul | find "%d" >nul
                if not errorlevel 1 (
                  timeout /t 1 /nobreak >nul
                  goto wait
                )
                msiexec /i "%s" /passive /norestart
                start "" "%s"
                del "%%~f0"
                """.formatted(pid, pid, msi, appExecutable);
    }
}
