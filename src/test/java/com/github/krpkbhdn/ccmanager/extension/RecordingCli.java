package com.github.krpkbhdn.ccmanager.extension;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** A claude stand-in that records every call and answers with one canned result. */
public class RecordingCli implements ClaudeCli {

    public final List<List<String>> calls = new ArrayList<>();
    public final List<Path> directories = new ArrayList<>();
    private CommandRunner.Result result = new CommandRunner.Result(0, "", "", false);

    public RecordingCli answering(int exitCode, String stdout, String stderr) {
        result = new CommandRunner.Result(exitCode, stdout, stderr, false);
        return this;
    }

    public RecordingCli answering(String stdout) {
        return answering(0, stdout, "");
    }

    @Override
    public CommandRunner.Result execute(Path directory, Duration timeout, List<String> arguments) {
        directories.add(directory);
        calls.add(arguments);
        return result;
    }

    public List<String> lastCall() {
        return calls.getLast();
    }
}
