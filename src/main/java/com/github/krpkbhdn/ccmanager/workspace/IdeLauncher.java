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

    /** @throws IllegalStateException with a user-readable message when IDEA cannot be found or started */
    public void open(Path directory) {
        var launcher = locator.find(host).orElseThrow(() -> new IllegalStateException(
                "IntelliJ IDEA was not found. Install it or add its launcher (idea) to PATH."));
        var command = new ArrayList<>(launcher);
        command.add(directory.toString());
        try {
            CommandRunner.startDetached(command);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start IntelliJ IDEA: " + e.getMessage(), e);
        }
    }
}
