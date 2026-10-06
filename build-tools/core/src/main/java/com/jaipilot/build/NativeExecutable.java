package com.jaipilot.build;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Resolves the exact native release; downloading never runs during build configuration. */
public final class NativeExecutable {
  public static final String VERSION = "1.2.1";

  private NativeExecutable() {}

  private static boolean correctVersion(String path) {
    try {
      Process p = new ProcessBuilder(path, "--version").redirectErrorStream(true).start();
      if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return false;
      }
      return p.exitValue() == 0
          && new String(p.getInputStream().readAllBytes()).trim().equals("JAIPilot CLI " + VERSION);
    } catch (Exception e) {
      return false;
    }
  }

  public static synchronized String resolve(String requested) throws Exception {
    if (requested != null && !requested.isBlank() && !requested.equals("jaipilot")) {
      if (!correctVersion(requested))
        throw new IOException("Explicit JAIPilot executable must be version " + VERSION);
      return requested;
    }
    if (correctVersion("jaipilot")) return "jaipilot";
    String os = System.getProperty("os.name").toLowerCase(),
        arch = System.getProperty("os.arch").toLowerCase();
    boolean arm = arch.equals("aarch64") || arch.equals("arm64");
    if (!arm && !arch.equals("amd64") && !arch.equals("x86_64"))
      throw new IOException("Unsupported native architecture: " + arch);
    String target =
        os.contains("mac")
            ? (arm ? "aarch64" : "x86_64") + "-apple-darwin"
            : os.contains("linux")
                ? (arm ? "aarch64" : "x86_64") + "-unknown-linux-gnu"
                : os.contains("win") && !arm ? "x86_64-pc-windows-msvc" : null;
    if (target == null) throw new IOException("Unsupported native platform: " + os + " " + arch);
    String name = "jaipilot" + (CoverageSupport.windows() ? ".exe" : "");
    Path cache =
        Path.of(System.getProperty("user.home"), ".cache", "jaipilot", "native", VERSION, target);
    Files.createDirectories(cache);
    Path executable = cache.resolve(name);
    try (FileChannel channel =
            FileChannel.open(
                cache.resolve("install.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = channel.lock()) {
      if (Files.exists(executable) && correctVersion(executable.toString()))
        return executable.toString();
      String asset = "jaipilot-" + target + (CoverageSupport.windows() ? ".exe" : "") + ".gz";
      String base = "https://github.com/JAIPilot/jaipilot/releases/download/v" + VERSION + "/";
      HttpClient client =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(20))
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
      HttpResponse<String> checksum =
          client.send(
              HttpRequest.newBuilder(URI.create(base + asset + ".sha256"))
                  .timeout(Duration.ofSeconds(30))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (checksum.statusCode() != 200)
        throw new IOException(
            "Native release "
                + VERSION
                + " is unavailable (checksum HTTP "
                + checksum.statusCode()
                + ")");
      String expected = checksum.body().trim().split("\\s+")[0];
      if (!expected.matches("[0-9a-fA-F]{64}")) throw new IOException("Invalid native checksum");
      Path compressed = Files.createTempFile(cache, "download-", ".gz"),
          candidate =
              Files.createTempFile(cache, "native-", CoverageSupport.windows() ? ".exe" : ".tmp");
      try {
        HttpResponse<java.io.InputStream> response =
            client.send(
                HttpRequest.newBuilder(URI.create(base + asset))
                    .timeout(Duration.ofSeconds(120))
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        var timer =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                r -> {
                  Thread thread = new Thread(r, "jaipilot-native-download-timeout");
                  thread.setDaemon(true);
                  return thread;
                });
        var timeout =
            timer.schedule(
                () -> {
                  try {
                    response.body().close();
                  } catch (IOException ignored) {
                  }
                },
                120,
                java.util.concurrent.TimeUnit.SECONDS);
        try (var in = response.body();
            var out = Files.newOutputStream(compressed)) {
          if (response.statusCode() != 200)
            throw new IOException("Native download HTTP " + response.statusCode());
          byte[] block = new byte[8192];
          long size = 0;
          for (int n; (n = in.read(block)) != -1; ) {
            size += n;
            if (size > 16 * 1024 * 1024)
              throw new IOException("Native download exceeds size limit");
            out.write(block, 0, n);
          }
        } finally {
          timeout.cancel(false);
          timer.shutdownNow();
        }
        String actual =
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(compressed)));
        if (!actual.equalsIgnoreCase(expected)) throw new IOException("Native checksum mismatch");
        try (var in = new GZIPInputStream(Files.newInputStream(compressed));
            var out = Files.newOutputStream(candidate)) {
          byte[] block = new byte[8192];
          long size = 0;
          for (int n; (n = in.read(block)) != -1; ) {
            size += n;
            if (size > 16 * 1024 * 1024)
              throw new IOException("Native executable exceeds size limit");
            out.write(block, 0, n);
          }
        }
        if (!CoverageSupport.windows())
          Files.setPosixFilePermissions(candidate, PosixFilePermissions.fromString("rwx------"));
        if (!correctVersion(candidate.toString()))
          throw new IOException("Downloaded executable has incorrect version");
        Files.move(
            candidate,
            executable,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(compressed);
        Files.deleteIfExists(candidate);
      }
      return executable.toString();
    }
  }
}
