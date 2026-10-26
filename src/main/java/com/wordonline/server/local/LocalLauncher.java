package com.wordonline.server.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.WordOnlineServerApplication;

/**
 * Starts a server the desktop client runs as its child process, controlled over stdin and stdout
 * (root plan, local hosting contracts, section 4).
 *
 * <ol>
 *   <li>Point {@link System#out} at stderr, so the real stdout carries protocol lines only.</li>
 *   <li>Read the {@code init} line. No port is opened before the token is known.</li>
 *   <li>Start Spring with the {@code local} profile: loopback only, port 0, logs under
 *       {@code dataDir/logs}. The in-memory database is filled during the context refresh.</li>
 *   <li>Write {@code ready} with the port the operating system picked.</li>
 *   <li>Keep reading stdin: {@code shutdown} ends the running match and exits; end of input means
 *       the client is gone, and the server exits within {@link #EOF_EXIT_LIMIT}.</li>
 * </ol>
 *
 * Any failure before {@code ready} writes a single {@code error} line and exits with status 1.
 */
public final class LocalLauncher {

    public static final String PROFILE = "local";
    static final String LOG_FILE_NAME = "local-server.log";
    static final Duration EOF_EXIT_LIMIT = Duration.ofSeconds(2);
    static final Duration SHUTDOWN_EXIT_LIMIT = Duration.ofSeconds(8);

    private static final String PROFILES_ARGUMENT = "--spring.profiles.active=";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LocalProtocol protocol;
    private final BufferedReader stdin;
    private final AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();

    private LocalLauncher(LocalProtocol protocol, BufferedReader stdin) {
        this.protocol = protocol;
        this.stdin = stdin;
    }

    /** Whether the {@code local} profile was asked for, the only way this launcher takes over. */
    public static boolean isRequested(String[] args) {
        boolean inArguments = Arrays.stream(args)
                .filter(arg -> arg.startsWith(PROFILES_ARGUMENT))
                .anyMatch(arg -> containsLocal(arg.substring(PROFILES_ARGUMENT.length())));
        return inArguments
                || containsLocal(System.getProperty("spring.profiles.active"))
                || containsLocal(System.getenv("SPRING_PROFILES_ACTIVE"));
    }

    private static boolean containsLocal(String profiles) {
        if (profiles == null) {
            return false;
        }
        return Arrays.stream(profiles.split(","))
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PROFILE::equals);
    }

    public static void launch(String[] args) {
        PrintStream stdout = System.out;
        System.setOut(System.err);
        LocalProtocol protocol = new LocalProtocol(stdout);
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new LocalLauncher(protocol, stdin).run(args);
    }

    private void run(String[] args) {
        try {
            LocalInit init = LocalInit.parse(stdin.readLine());
            Files.createDirectories(init.logDirectory());
            useDataDirForTemporaryFiles(init);
            startControlThread();

            ConfigurableApplicationContext started = start(args, init);
            context.set(started);

            int port = ((WebServerApplicationContext) started).getWebServer().getPort();
            String serverVersion = started.getBean(BuildProperties.class).getVersion();
            String staticVersion = started.getBean(LocalDatabase.class).staticVersion();
            protocol.ready(port, serverVersion, LocalStaticDataImporter.SUPPORTED_SCHEMA_VERSION, staticVersion);
        } catch (Throwable e) {
            protocol.error(reasonOf(e));
            System.exit(1);
        }
    }

    private ConfigurableApplicationContext start(String[] args, LocalInit init) {
        SpringApplication application = new SpringApplication(WordOnlineServerApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.addInitializers(applicationContext -> {
            applicationContext.getBeanFactory().registerSingleton("localInit", init);
            applicationContext.getBeanFactory().registerSingleton("localProtocol", protocol);
        });
        String[] arguments = Arrays.copyOf(args, args.length + 2);
        arguments[args.length] = "--logging.file.name=" + init.logDirectory().resolve(LOG_FILE_NAME);
        arguments[args.length + 1] = "--server.tomcat.basedir=" + init.temporaryDirectory().resolve("tomcat");
        return application.run(arguments);
    }

    // Unity hands over a folder under AppData\LocalLow on Windows, and the bundle is unpacked there
    // too. An executable carrying LocalLow's low integrity label runs as a low integrity process,
    // which may write nowhere else: Tomcat's work folder in %TEMP% fails with AccessDeniedException.
    // Everything this process writes therefore stays under dataDir. Set before Spring starts, while
    // nothing has read java.io.tmpdir yet.
    private static void useDataDirForTemporaryFiles(LocalInit init) throws IOException {
        Files.createDirectories(init.temporaryDirectory());
        System.setProperty("java.io.tmpdir", init.temporaryDirectory().toString());
    }

    // Started right after init so a client that dies during startup does not leave the server
    // running until it is ready.
    private void startControlThread() {
        Thread thread = new Thread(this::readCommands, "local-stdin");
        thread.setDaemon(true);
        thread.start();
    }

    private void readCommands() {
        try {
            String line;
            while ((line = stdin.readLine()) != null) {
                if ("shutdown".equals(commandType(line))) {
                    shutdown();
                    return;
                }
            }
        } catch (IOException ignored) {
            // A broken pipe is the same as end of input: the client is gone.
        }
        exitWithin(EOF_EXIT_LIMIT);
    }

    private static String commandType(String line) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(line);
            return node == null ? null : node.path("type").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    // Ends the running match first, so its matchResult line still reaches the client.
    private void shutdown() {
        ConfigurableApplicationContext running = context.get();
        if (running != null) {
            try {
                running.getBean(LocalSessionService.class).abandonRunningSessions();
            } catch (RuntimeException ignored) {
                // Exiting matters more than a result for a match the client is abandoning anyway.
            }
        }
        exitWithin(SHUTDOWN_EXIT_LIMIT);
    }

    // System.exit runs Spring's shutdown hook, which closes the context. A context that does not
    // close in time is not allowed to keep the process alive.
    private static void exitWithin(Duration limit) {
        Thread halt = new Thread(() -> {
            try {
                Thread.sleep(limit.toMillis());
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "local-exit-limit");
        halt.setDaemon(true);
        halt.start();
        System.exit(0);
    }

    static String reasonOf(Throwable throwable) {
        Throwable root = throwable;
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof LocalStartupException) {
                return cause.getMessage();
            }
            root = cause;
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
