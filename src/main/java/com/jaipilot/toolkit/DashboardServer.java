package com.jaipilot.toolkit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaipilot.toolkit.core.OwnerPermissions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Loopback-only local dashboard lifecycle and HTTP server. */
final class DashboardServer {

    static final int DEFAULT_PORT = 7433;
    private static final int METADATA_SCHEMA_VERSION = 1;
    private static final int CONNECT_TIMEOUT_MILLIS = 250;
    private static final int START_ATTEMPTS = 50;
    private static final long START_POLL_MILLIS = 40L;
    private static final String SERVICE_NAME = "jaipilot-dashboard";
    private static final String DASHBOARD_DIRECTORY = "dashboard";
    private static final String METADATA_FILE = "server.json";
    private static final String READY_FILE = "server.ready";
    private static final String LOCK_FILE = "server.lock";
    private static final String LOG_FILE = "dashboard.log";
    private static final String RESOURCE_ROOT = "/com/jaipilot/toolkit/dashboard/";
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private DashboardServer() {
    }

    static DashboardStatus ensureRunning(
            ObjectMapper mapper,
            Path stateRoot,
            java.io.PrintStream stderr,
            boolean awaitReady
    ) {
        Objects.requireNonNull(stderr, "stderr");
        if (disabled()) {
            return DashboardStatus.stopped(preferredPort(stderr));
        }
        DashboardStatus current = quickStatus(stateRoot);
        if (current == null) {
            current = readStatus(mapper, stateRoot, false);
        }
        int preferred = preferredPort(stderr);
        boolean currentConfiguration = current.running()
                && implementationVersion().equals(current.version())
                && current.preferredPort() == preferred;
        if (currentConfiguration && !awaitReady) {
            return current;
        }
        if (current.running()) {
            DashboardStatus verified = currentStatus(mapper, stateRoot);
            if (verified.running() && currentConfiguration) {
                return verified;
            }
            if (verified.running()) {
                retireStaleDashboard(verified);
            }
        }
        Path directory = dashboardDirectory(stateRoot);
        createPrivateDirectory(directory);
        try {
            startDetached(stateRoot, directory.resolve(LOG_FILE));
        } catch (IOException exception) {
            stderr.println("jaipilot: dashboard could not start: " + rootMessage(exception));
            return DashboardStatus.stopped(preferred);
        }
        if (!awaitReady) {
            stderr.println("jaipilot: dashboard starting on loopback; query the private runner's dashboard command for its URL");
            return DashboardStatus.stopped(preferred);
        }
        for (int attempt = 0; attempt < START_ATTEMPTS; attempt++) {
            DashboardStatus started = currentStatus(mapper, stateRoot);
            if (started.running()) {
                stderr.println("jaipilot: dashboard available at " + started.url());
                return started;
            }
            try {
                Thread.sleep(START_POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        stderr.println("jaipilot: dashboard startup did not become ready; see " + directory.resolve(LOG_FILE));
        return DashboardStatus.stopped(preferred);
    }

    private static void retireStaleDashboard(DashboardStatus current) {
        ProcessHandle process = ProcessHandle.of(current.pid()).filter(ProcessHandle::isAlive).orElse(null);
        if (process == null) {
            return;
        }
        process.destroy();
        for (int attempt = 0; attempt < START_ATTEMPTS / 2; attempt++) {
            if (!process.isAlive()) {
                return;
            }
            try {
                Thread.sleep(START_POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static DashboardStatus currentStatus(ObjectMapper mapper, Path stateRoot) {
        return readStatus(mapper, stateRoot, true);
    }

    private static DashboardStatus readStatus(ObjectMapper mapper, Path stateRoot, boolean verifyHealth) {
        Path metadataPath = dashboardDirectory(stateRoot).resolve(METADATA_FILE);
        if (!Files.isRegularFile(metadataPath)) {
            return DashboardStatus.stopped(preferredPort(null));
        }
        try {
            ServerMetadata metadata = mapper.readValue(metadataPath.toFile(), ServerMetadata.class);
            if (metadata.schemaVersion() != METADATA_SCHEMA_VERSION
                    || metadata.port() < 1
                    || metadata.port() > 65_535
                    || metadata.instanceId() == null
                    || metadata.instanceId().isBlank()) {
                return DashboardStatus.stopped(preferredPort(null));
            }
            if (!ProcessHandle.of(metadata.pid()).map(ProcessHandle::isAlive).orElse(false)
                    || (verifyHealth && !healthy(mapper, metadata))) {
                return DashboardStatus.stopped(metadata.preferredPort());
            }
            return new DashboardStatus(
                    true,
                    url(metadata.port()),
                    metadata.port(),
                    metadata.preferredPort(),
                    metadata.fallbackPort(),
                    metadata.pid(),
                    metadata.startedAt(),
                    metadata.version()
            );
        } catch (IOException | RuntimeException exception) {
            return DashboardStatus.stopped(preferredPort(null));
        }
    }

    static int serve(ObjectMapper mapper, Path stateRoot, java.io.PrintStream stderr) {
        int preferred = preferredPort(stderr);
        try (RunningDashboard running = start(mapper, stateRoot, preferred)) {
            if (running == null) {
                return 0;
            }
            stderr.println("JAIPilot dashboard listening at " + running.status().url());
            new CountDownLatch(1).await();
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException exception) {
            stderr.println("JAIPilot dashboard failed: " + rootMessage(exception));
            return 1;
        }
    }

    static RunningDashboard start(ObjectMapper mapper, Path stateRoot, int preferredPort) throws IOException {
        Path directory = dashboardDirectory(stateRoot);
        createPrivateDirectory(directory);
        Path lockPath = directory.resolve(LOCK_FILE);
        FileChannel lockChannel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        FileLock lock = tryLock(lockChannel);
        if (lock == null) {
            lockChannel.close();
            return null;
        }

        HttpServer server = null;
        ExecutorService executor = null;
        try {
            BoundServer bound = bind(preferredPort);
            server = bound.server();
            executor = Executors.newFixedThreadPool(4, daemonThreads());
            server.setExecutor(executor);
            String instanceId = UUID.randomUUID().toString();
            ServerMetadata metadata = new ServerMetadata(
                    METADATA_SCHEMA_VERSION,
                    instanceId,
                    ProcessHandle.current().pid(),
                    bound.port(),
                    preferredPort,
                    bound.fallback(),
                    Instant.now().toString(),
                    implementationVersion()
            );
            RepositorySnapshotStore snapshots = new RepositorySnapshotStore(mapper, stateRoot);
            configureRoutes(server, mapper, snapshots, metadata);
            server.start();
            writeMetadata(mapper, directory.resolve(METADATA_FILE), metadata);
            writeReadyMarker(directory.resolve(READY_FILE), metadata);
            return new RunningDashboard(
                    server,
                    executor,
                    lock,
                    lockChannel,
                    directory.resolve(METADATA_FILE),
                    directory.resolve(READY_FILE),
                    mapper,
                    metadata,
                    status(metadata)
            );
        } catch (IOException | RuntimeException exception) {
            if (server != null) {
                server.stop(0);
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            lock.close();
            lockChannel.close();
            throw exception;
        }
    }

    private static void configureRoutes(
            HttpServer server,
            ObjectMapper mapper,
            RepositorySnapshotStore snapshots,
            ServerMetadata metadata
    ) throws IOException {
        byte[] index = resource("index.html");
        byte[] stylesheet = resource("dashboard.css");
        byte[] script = resource("dashboard.js");
        byte[] logo = resource("logo.svg");
        server.createContext("/", exchange -> staticResponse(exchange, "/", "text/html; charset=utf-8", index));
        server.createContext(
                "/assets/dashboard.css",
                exchange -> staticResponse(exchange, "/assets/dashboard.css", "text/css; charset=utf-8", stylesheet)
        );
        server.createContext(
                "/assets/dashboard.js",
                exchange -> staticResponse(
                        exchange,
                        "/assets/dashboard.js",
                        "text/javascript; charset=utf-8",
                        script
                )
        );
        server.createContext(
                "/logo.svg",
                exchange -> staticResponse(exchange, "/logo.svg", "image/svg+xml", logo)
        );
        server.createContext("/api/health", exchange -> jsonResponse(exchange, mapper, new Health(
                SERVICE_NAME,
                "ok",
                metadata.instanceId(),
                metadata.version()
        )));
        server.createContext("/api/metrics", exchange -> {
            try {
                jsonResponse(exchange, mapper, snapshots.view(repositoryId(exchange.getRequestURI())));
            } catch (IllegalArgumentException exception) {
                jsonError(exchange, mapper, 400, "unknown_repository");
            } catch (RuntimeException exception) {
                jsonError(exchange, mapper, 500, "metrics_unavailable");
            }
        });
    }

    private static String repositoryId(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && "repository".equals(pair.substring(0, equals))) {
                return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void staticResponse(
            HttpExchange exchange,
            String expectedPath,
            String contentType,
            byte[] body
    ) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            emptyResponse(exchange, 405);
            return;
        }
        if (!expectedPath.equals(exchange.getRequestURI().getPath())) {
            emptyResponse(exchange, 404);
            return;
        }
        secureHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void jsonResponse(HttpExchange exchange, ObjectMapper mapper, Object value) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            emptyResponse(exchange, 405);
            return;
        }
        byte[] body = mapper.writeValueAsBytes(value);
        secureHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void jsonError(HttpExchange exchange, ObjectMapper mapper, int status, String code)
            throws IOException {
        byte[] body = mapper.writeValueAsBytes(new ErrorResponse(false, code));
        secureHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void emptyResponse(HttpExchange exchange, int status) throws IOException {
        secureHeaders(exchange);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void secureHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                        + "connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"
        );
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
    }

    private static BoundServer bind(int preferredPort) throws IOException {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(loopback, preferredPort), 0);
            return new BoundServer(server, server.getAddress().getPort(), false);
        } catch (BindException exception) {
            HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
            return new BoundServer(server, server.getAddress().getPort(), true);
        }
    }

    private static boolean healthy(ObjectMapper mapper, ServerMetadata metadata) {
        HttpURLConnection connection = null;
        try {
            URL endpoint = URI.create(url(metadata.port()) + "api/health").toURL();
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != 200) {
                return false;
            }
            try (InputStream input = connection.getInputStream()) {
                JsonNode health = mapper.readTree(input);
                return SERVICE_NAME.equals(health.path("service").asText())
                        && metadata.instanceId().equals(health.path("instanceId").asText());
            }
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static DashboardStatus quickStatus(Path stateRoot) {
        ServerReady ready = readReadyMarker(dashboardDirectory(stateRoot).resolve(READY_FILE));
        if (ready == null) {
            return null;
        }
        if (!ProcessHandle.of(ready.pid()).map(ProcessHandle::isAlive).orElse(false)) {
            return DashboardStatus.stopped(ready.preferredPort());
        }
        return new DashboardStatus(
                true,
                null,
                0,
                ready.preferredPort(),
                false,
                ready.pid(),
                null,
                ready.version()
        );
    }

    private static void startDetached(Path stateRoot, Path logPath) throws IOException {
        Files.createDirectories(logPath.getParent());
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(JaiPilotToolkit.class.getName());
        command.add("dashboard-serve");
        ProcessBuilder process = new ProcessBuilder(command);
        process.environment().put("JAIPILOT_STATE_HOME", stateRoot.toAbsolutePath().normalize().toString());
        process.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        process.redirectError(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        process.start();
    }

    private static Path javaExecutable() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path windows = bin.resolve("java.exe");
        return Files.isRegularFile(windows) ? windows : bin.resolve("java");
    }

    private static int preferredPort(java.io.PrintStream stderr) {
        String configured = System.getenv("JAIPILOT_DASHBOARD_PORT");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PORT;
        }
        Integer parsed = validPort(configured);
        if (parsed != null) {
            return parsed;
        }
        if (stderr != null) {
            stderr.println("jaipilot: JAIPILOT_DASHBOARD_PORT must be between 1 and 65535; using " + DEFAULT_PORT);
        }
        return DEFAULT_PORT;
    }

    private static Integer validPort(String configured) {
        try {
            int port = Integer.parseInt(configured);
            return port >= 1 && port <= 65_535 ? port : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean disabled() {
        String value = System.getenv("JAIPILOT_DASHBOARD_DISABLED");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            return null;
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = DashboardServer.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (input == null) {
                throw new IOException("Missing dashboard resource: " + name);
            }
            return input.readAllBytes();
        }
    }

    private static void writeMetadata(ObjectMapper mapper, Path destination, ServerMetadata metadata)
            throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".jaipilot-dashboard-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), metadata);
            OwnerPermissions.set(temporary, OWNER_FILE);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeReadyMarker(Path destination, ServerMetadata metadata) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".jaipilot-dashboard-ready-", ".tmp");
        try {
            String contents = METADATA_SCHEMA_VERSION + "\n"
                    + metadata.version() + "\n"
                    + metadata.preferredPort() + "\n"
                    + metadata.pid() + "\n"
                    + metadata.instanceId() + "\n";
            Files.writeString(
                    temporary,
                    contents,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            OwnerPermissions.set(temporary, OWNER_FILE);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ServerReady readReadyMarker(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.size() != 5 || Integer.parseInt(lines.get(0)) != METADATA_SCHEMA_VERSION) {
                return null;
            }
            String version = lines.get(1);
            int preferredPort = Integer.parseInt(lines.get(2));
            long pid = Long.parseLong(lines.get(3));
            String instanceId = UUID.fromString(lines.get(4)).toString();
            if (version.isBlank() || preferredPort < 0 || preferredPort > 65_535 || pid <= 0) {
                return null;
            }
            return new ServerReady(version, preferredPort, pid, instanceId);
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static void createPrivateDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            OwnerPermissions.set(directory, OWNER_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create JAIPilot dashboard state directory.", exception);
        }
    }

    private static DashboardStatus status(ServerMetadata metadata) {
        return new DashboardStatus(
                true,
                url(metadata.port()),
                metadata.port(),
                metadata.preferredPort(),
                metadata.fallbackPort(),
                metadata.pid(),
                metadata.startedAt(),
                metadata.version()
        );
    }

    private static String url(int port) {
        return "http://127.0.0.1:" + port + "/";
    }

    private static Path dashboardDirectory(Path stateRoot) {
        return stateRoot.toAbsolutePath().normalize().resolve(DASHBOARD_DIRECTORY);
    }

    private static String implementationVersion() {
        String version = JaiPilotToolkit.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }

    private static ThreadFactory daemonThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "jaipilot-dashboard-http");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    record DashboardStatus(
            boolean running,
            String url,
            int port,
            int preferredPort,
            boolean fallbackPort,
            long pid,
            String startedAt,
            String version
    ) {
        private static DashboardStatus stopped(int preferredPort) {
            return new DashboardStatus(false, null, 0, preferredPort, false, 0L, null, implementationVersion());
        }
    }

    static final class RunningDashboard implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final FileLock lock;
        private final FileChannel lockChannel;
        private final Path metadataPath;
        private final Path readyPath;
        private final ObjectMapper mapper;
        private final ServerMetadata metadata;
        private final DashboardStatus status;

        private RunningDashboard(
                HttpServer server,
                ExecutorService executor,
                FileLock lock,
                FileChannel lockChannel,
                Path metadataPath,
                Path readyPath,
                ObjectMapper mapper,
                ServerMetadata metadata,
                DashboardStatus status
        ) {
            this.server = server;
            this.executor = executor;
            this.lock = lock;
            this.lockChannel = lockChannel;
            this.metadataPath = metadataPath;
            this.readyPath = readyPath;
            this.mapper = mapper;
            this.metadata = metadata;
            this.status = status;
        }

        DashboardStatus status() {
            return status;
        }

        @Override
        public void close() throws IOException {
            server.stop(0);
            executor.shutdownNow();
            deleteOwnMetadata();
            lock.close();
            lockChannel.close();
        }

        private void deleteOwnMetadata() throws IOException {
            try {
                if (!Files.isRegularFile(metadataPath)) {
                    return;
                }
                ServerMetadata current = mapper.readValue(metadataPath.toFile(), ServerMetadata.class);
                if (metadata.instanceId().equals(current.instanceId())) {
                    Files.deleteIfExists(metadataPath);
                    ServerReady ready = readReadyMarker(readyPath);
                    if (ready != null && metadata.instanceId().equals(ready.instanceId())) {
                        Files.deleteIfExists(readyPath);
                    }
                }
            } catch (RuntimeException invalidMetadata) {
                Files.deleteIfExists(metadataPath);
                Files.deleteIfExists(readyPath);
            }
        }
    }

    private record BoundServer(HttpServer server, int port, boolean fallback) {
    }

    private record ServerReady(String version, int preferredPort, long pid, String instanceId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ServerMetadata(
            int schemaVersion,
            String instanceId,
            long pid,
            int port,
            int preferredPort,
            boolean fallbackPort,
            String startedAt,
            String version
    ) {
    }

    private record Health(String service, String status, String instanceId, String version) {
    }

    private record ErrorResponse(boolean ok, String error) {
    }
}
