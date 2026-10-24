package com.github.krpkbhdn.ccmanager.workspace;

import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** Opens a directory in IntelliJ IDEA (Windows, macOS, Linux; PATH launcher, Toolbox, default install dirs). */
@Service
public class IdeLauncher {

    public boolean available() { throw new UnsupportedOperationException("TODO"); }

    /** @throws IllegalStateException with a user-readable message when IDEA cannot be found or started */
    public void open(Path directory) { throw new UnsupportedOperationException("TODO"); }
}
