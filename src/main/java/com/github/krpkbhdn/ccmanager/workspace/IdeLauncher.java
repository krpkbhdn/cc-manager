package com.github.krpkbhdn.ccmanager.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Opens a directory in one of the detected or user-defined IDEs. */
@Service
public class IdeLauncher {

    private final HostEnvironment host;
    private final IdeDetector detector;
    private final CustomIdeService customIdes;

    @Autowired
    public IdeLauncher(HostEnvironment host, IdeDetector detector, CustomIdeService customIdes) {
        this.host = host;
        this.detector = detector;
        this.customIdes = customIdes;
    }

    /** Detected IDEs first, then the custom ones. */
    public List<InstalledIde> list() {
        var all = new ArrayList<>(detector.detect());
        customIdes.list().forEach(custom -> all.add(new InstalledIde(custom.ideId(), custom.name(), custom.command())));
        return List.copyOf(all);
    }

    public boolean available() {
        return !list().isEmpty();
    }

    /** @return the IDE with the preferred id, else the first one in {@link #list()} */
    public Optional<InstalledIde> resolve(Optional<String> preferredId) {
        var all = list();
        return preferredId.flatMap(id -> all.stream().filter(ide -> ide.id().equals(id)).findFirst())
                .or(() -> all.stream().findFirst());
    }

    /** Opens in the first available IDE. @throws IllegalStateException with a user-readable message when there is none or it cannot start */
    public void open(Path directory) {
        open(resolve(Optional.empty()).orElseThrow(() -> new IllegalStateException(
                "No IDE was found. Install one, add its launcher to PATH or add a custom IDE in the settings.")), directory);
    }

    /** @throws IllegalStateException with a user-readable message for an unknown id or an IDE that cannot start */
    public void open(String ideId, Path directory) {
        var ide = list().stream().filter(candidate -> candidate.id().equals(ideId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown IDE: " + ideId));
        open(ide, directory);
    }

    private void open(InstalledIde ide, Path directory) {
        var launcher = ide.command().getFirst();
        if (host.windows() && isBatchFile(launcher) && directory.toString().indexOf('%') >= 0) {
            throw new IllegalStateException(ide.name() + " is started through a .cmd launcher, which would expand the % in " + directory.getFileName()
                    + ". Rename the folder or use the native launcher.");
        }
        var command = new ArrayList<>(ide.command());
        command.add(WindowsArgument.directoryArgument(launcher, directory));
        try {
            CommandRunner.startDetached(command);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start " + ide.name() + ": " + e.getMessage(), e);
        }
    }

    private static boolean isBatchFile(String launcher) {
        var name = launcher.toLowerCase(Locale.ROOT);
        return name.endsWith(".cmd") || name.endsWith(".bat");
    }
}
