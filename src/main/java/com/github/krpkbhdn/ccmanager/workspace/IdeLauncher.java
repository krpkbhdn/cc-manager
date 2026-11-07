package com.github.krpkbhdn.ccmanager.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Opens a directory in IntelliJ IDEA (Windows, macOS, Linux; PATH launcher, Toolbox, default install dirs). */
@Service
public class IdeLauncher {

    private final HostEnvironment host;
    private final IdeLocator locator;

    @Autowired
    public IdeLauncher(HostEnvironment host, IdeLocator locator) {
        this.host = host;
        this.locator = locator;
    }

    public boolean available() {
        return locator.find(host).isPresent();
    }

    private static boolean isBatchFile(String launcher) {
        var name = launcher.toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".cmd") || name.endsWith(".bat");
    }

    /** @throws IllegalStateException with a user-readable message when IDEA cannot be found or started */
    public void open(Path directory) {
        var launcher = locator.find(host).orElseThrow(() -> new IllegalStateException(
                "IntelliJ IDEA was not found. Install it or add its launcher (idea) to PATH."));
        if (host.windows() && isBatchFile(launcher.getFirst()) && directory.toString().indexOf('%') >= 0) {
            throw new IllegalStateException("IntelliJ IDEA is started through a .cmd launcher, which would expand the % in " + directory.getFileName()
                    + ". Rename the folder or install the native idea64.exe launcher.");
        }
        var command = new ArrayList<>(launcher);
        command.add(WindowsArgument.directoryArgument(launcher.getFirst(), directory));
        try {
            CommandRunner.startDetached(command);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start IntelliJ IDEA: " + e.getMessage(), e);
        }
    }
}
