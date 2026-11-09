package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Runs the generated statusLine command in the real shells of this machine against a local server. */
class CurlCommandExecutionTest {

    /** Windows argv quoting garbles a quoted command for MSYS bash, so the POSIX shells get it through the environment; eval is what -c does with it. */
    private static final String COMMAND_ENV = "CCM_TEST_COMMAND";
    private static final String TOKEN = "tok-Zx9_secretvalue";
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private static final List<List<String>> SHELLS = List.of(List.of("sh", "-c"), List.of("C:/Program Files/Git/bin/bash.exe", "-c"),
            List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand"));
    private static final HookProperties HOOK_PROPERTIES = new HookProperties(10, 1024, 500, Duration.ofSeconds(590), 10, 1000, 1000);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<String> receivedAuthorization = new CopyOnWriteArrayList<>();
    private HttpServer http;
    private HookServer server;

    @TempDir
    Path dataDir;

    @BeforeEach
    void startServer() throws Exception {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            receivedAuthorization.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        http.start();
        server = mock(HookServer.class);
        when(server.statusLineUrl("abc-1")).thenReturn("http://127.0.0.1:" + http.getAddress().getPort() + "/statusline/abc-1");
        when(server.hookUrl("abc-1")).thenReturn("http://127.0.0.1:" + http.getAddress().getPort() + "/hook/abc-1");
        when(server.mcpUrl("abc-1")).thenReturn("http://127.0.0.1/mcp/abc-1");
    }

    @AfterEach
    void stopServer() {
        http.stop(0);
    }

    @Test
    void everyAvailableShellSendsTheBearerFromTheEnvironmentWithoutTheTokenInTheCommand() throws Exception {
        assumeTrue(new CurlSupport().expandsEnvironment(WINDOWS ? "curl.exe" : "curl"), "needs curl 8.3+");
        var command = statusLineCommand(new CurlSupport());
        assertThat(command).doesNotContain(TOKEN);
        var ran = 0;
        for (var shell : SHELLS) {
            receivedAuthorization.clear();
            if (run(shell, command, TOKEN) == 0) {
                ran++;
                assertThat(receivedAuthorization).as(shell.getFirst()).containsExactly("Bearer " + TOKEN);
            }
        }
        assumeTrue(ran > 0, "no shell available");
    }

    @Test
    void anUnsetVariableMakesCurlFailInsteadOfSendingAnEmptyHeader() throws Exception {
        assumeTrue(new CurlSupport().expandsEnvironment(WINDOWS ? "curl.exe" : "curl"), "needs curl 8.3+");
        var command = statusLineCommand(new CurlSupport());
        var ran = 0;
        for (var shell : SHELLS) {
            if (available(shell)) {
                ran++;
                assertThat(run(shell, command, null)).as(shell.getFirst()).isNotZero();
            }
        }
        assumeTrue(ran > 0, "no shell available");
        assertThat(receivedAuthorization).isEmpty();
    }

    @Test
    private String statusLineCommand(CurlSupport curl) throws Exception {
        var writer = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, HOOK_PROPERTIES, curl, WINDOWS);
        return mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("statusLine").path("command").asString();
    }

    private static boolean available(List<String> shell) throws Exception {
        return run(shell, "exit 0", TOKEN) == 0;
    }

    /** The exit code, or -1 when the shell cannot be started; the token is set in the process environment only. */
    private static int run(List<String> shell, String command, String token) throws Exception {
        var line = new ArrayList<>(shell);
        var powerShell = shell.contains("-EncodedCommand");
        line.add(powerShell ? Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE)) : "eval \"$" + COMMAND_ENV + "\"");
        var builder = new ProcessBuilder(line).redirectErrorStream(true);
        builder.environment().put(COMMAND_ENV, command);
        builder.environment().remove(HookServer.SESSION_TOKEN_ENV);
        if (token != null) {
            builder.environment().put(HookServer.SESSION_TOKEN_ENV, token);
        }
        Process process;
        try {
            process = builder.start();
        } catch (java.io.IOException e) {
            return -1;
        }
        process.getOutputStream().write("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor(60, TimeUnit.SECONDS) ? process.exitValue() : -1;
        System.out.println(shell.getFirst() + " exit " + exit + (exit == 0 ? "" : ": " + output));
        return exit;
    }
}
