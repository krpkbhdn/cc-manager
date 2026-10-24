package com.github.krpkbhdn.ccmanager.claude.hook;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Writes the per-session settings file passed as claude --settings FILE; hooks only, nothing else. */
@Component
public class HookSettingsWriter {

    public Path write(String ccmSessionId) { throw new UnsupportedOperationException("TODO"); }

    public void delete(String ccmSessionId) { throw new UnsupportedOperationException("TODO"); }
}
