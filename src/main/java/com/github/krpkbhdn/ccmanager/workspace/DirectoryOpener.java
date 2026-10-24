package com.github.krpkbhdn.ccmanager.workspace;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Opens a directory in the OS file manager. */
@Service
public class DirectoryOpener {

    private final HostEnvironment host;

    @Autowired
    public DirectoryOpener(HostEnvironment host) {
        this.host = host;
    }

    /** @throws IllegalStateException with a user-readable message when the directory is missing or cannot be opened */
    public void open(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Directory does not exist: " + directory);
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
            } else {
                CommandRunner.startDetached(List.of(fileManagerCommand(), directory.toString()));
            }
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("Could not open the file manager: " + e.getMessage(), e);
        }
    }

    private String fileManagerCommand() {
        return switch (host.os()) {
            case WINDOWS -> "explorer";
            case MACOS -> "open";
            case LINUX -> "xdg-open";
        };
    }
}
