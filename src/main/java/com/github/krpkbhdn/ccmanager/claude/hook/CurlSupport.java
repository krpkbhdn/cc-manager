package com.github.krpkbhdn.ccmanager.claude.hook;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Finds out once whether the curl on the PATH can read the session token from the environment itself (--variable %NAME, curl 8.3+). */
@Component
public class CurlSupport {

    private static final Logger log = LoggerFactory.getLogger(CurlSupport.class);
    static final int MIN_MAJOR = 8;
    static final int MIN_MINOR = 3;
    private static final int VERSION_TIMEOUT_SECONDS = 3;
    private static final Pattern VERSION = Pattern.compile("^curl (\\d+)\\.(\\d+)");

    private final Function<String, String> versionOutput;
    private final Map<String, Boolean> expandsByExecutable = new ConcurrentHashMap<>();

    public CurlSupport() {
        this(CurlSupport::runVersion);
    }

    CurlSupport(Function<String, String> versionOutput) {
        this.versionOutput = versionOutput;
    }

    /** False for an older or unknown curl; the caller then falls back to the shell expanding the variable into curl's argv. */
    public boolean expandsEnvironment(String executable) {
        return expandsByExecutable.computeIfAbsent(executable, this::detect);
    }

    private boolean detect(String executable) {
        var version = versionOutput.apply(executable);
        var matcher = VERSION.matcher(version == null ? "" : version);
        var supported = matcher.find() && atLeastMinimum(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        if (!supported) {
            log.warn("{} is older than {}.{} or its version is unknown; the session token goes through the shell into curl's arguments, "
                    + "where other local processes can read it (statusLine and SessionStart need a POSIX shell then)", executable, MIN_MAJOR, MIN_MINOR);
        }
        return supported;
    }

    private static boolean atLeastMinimum(int major, int minor) {
        return major > MIN_MAJOR || major == MIN_MAJOR && minor >= MIN_MINOR;
    }

    private static String runVersion(String executable) {
        try {
            var process = new ProcessBuilder(executable, "--version").redirectErrorStream(true).start();
            if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
