package com.github.krpkbhdn.ccmanager.claude.launch;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BackgroundAgentService {

    /** Empty when Claude is missing or reports none; never throws for a CLI failure. */
    public List<BackgroundAgent> list(Path projectDirectory) { throw new UnsupportedOperationException("TODO"); }
}
