package com.github.krpkbhdn.ccmanager.workspace;

/** Kills a process with everything it started; descendants come from the pid because pty4j's processes have no handle of their own. */
public final class ProcessTree {

    private ProcessTree() {}

    public static void kill(Process process) {
        ProcessHandle.of(process.pid()).ifPresent(handle -> handle.descendants().forEach(ProcessHandle::destroyForcibly));
        process.destroyForcibly();
    }
}
