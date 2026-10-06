package com.jaipilot.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small shared adapter; measurement and generation remain in the native CLI. */
public final class CoverageSupport {
  private CoverageSupport() {}

  public static double percentage(String value) {
    double n = Double.parseDouble(value);
    if (!Double.isFinite(n) || n < 0 || n > 100)
      throw new IllegalArgumentException("Coverage percentage must be between 0 and 100");
    return n;
  }

  public static String quote(String value) {
    return windows()
        ? "\"" + value.replace("\"", "\"\"") + "\""
        : "'" + value.replace("'", "'\"'\"'") + "'";
  }

  public static boolean windows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  public static String relative(Path root, Path path) {
    Path actual = path.toAbsolutePath().normalize();
    if (!actual.startsWith(root))
      throw new IllegalArgumentException("Coverage path must remain inside repository: " + path);
    return root.relativize(actual).toString().replace('\\', '/');
  }

  public static Path repository(Path directory) throws IOException, InterruptedException {
    Process p =
        new ProcessBuilder("git", "rev-parse", "--show-toplevel")
            .directory(directory.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    String result = new String(p.getInputStream().readAllBytes()).trim();
    if (p.waitFor() != 0 || result.isEmpty())
      throw new IOException("JAIPilot requires a Git repository");
    return Path.of(result).toAbsolutePath().normalize();
  }

  public static Map<String, Object> target(
      String counter,
      String element,
      double minimum,
      String report,
      List<String> includes,
      List<String> excludes) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("counter", counter);
    t.put("element", element);
    t.put("minimumPercent", minimum);
    t.put("report", report);
    if (!includes.isEmpty()) t.put("includes", includes);
    if (!excludes.isEmpty()) t.put("excludes", excludes);
    return t;
  }

  /** Resolve exact production classes without expanding to unrelated source files. */
  public static List<String> classSources(Path repository, List<String> roots, List<String> classes)
      throws IOException {
    if (classes.isEmpty()) return roots;
    List<String> result = new ArrayList<>();
    for (String name : classes) {
      if (!name.matches("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*"))
        throw new IllegalArgumentException("Use exact qualified Java class names: " + name);
      String path = name.replace('.', '/') + ".java";
      boolean found = false;
      for (String root : roots) {
        Path candidate = repository.resolve(root).resolve(path);
        if (Files.isRegularFile(candidate)) {
          result.add(relative(repository, candidate));
          found = true;
        }
      }
      if (!found) throw new IllegalArgumentException("Production class was not found: " + name);
    }
    return result;
  }

  public static String json(Object value) {
    if (value instanceof String s) {
      StringBuilder out = new StringBuilder("\"");
      for (char c : s.toCharArray())
        switch (c) {
          case '\\' -> out.append("\\\\");
          case '"' -> out.append("\\\"");
          case '\n' -> out.append("\\n");
          case '\r' -> out.append("\\r");
          case '\t' -> out.append("\\t");
          default -> {
            if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
          }
        }
      return out.append('"').toString();
    }
    if (value instanceof Map<?, ?> m) {
      List<String> entries = new ArrayList<>();
      m.forEach((k, v) -> entries.add(json(k.toString()) + ":" + json(v)));
      return "{" + String.join(",", entries) + "}";
    }
    if (value instanceof List<?> l)
      return "[" + String.join(",", l.stream().map(CoverageSupport::json).toList()) + "]";
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    throw new IllegalArgumentException("Unsupported coverage policy value");
  }

  public static void execute(
      Path repository,
      Path policyPath,
      Map<String, Object> policy,
      String executable,
      boolean checkOnly)
      throws Exception {
    Files.createDirectories(policyPath.getParent());
    Files.writeString(policyPath, json(policy) + "\n");
    List<String> args =
        List.of(
            NativeExecutable.resolve(executable),
            "coverage",
            checkOnly ? "check" : "run",
            "--policy",
            policyPath.toString(),
            "--repo",
            repository.toString());
    ProcessBuilder builder =
        new ProcessBuilder(args).directory(repository.toFile()).redirectErrorStream(true);
    builder.environment().put("JAIPILOT_NO_UPDATE", "1");
    builder.environment().put("JAIPILOT_BUILD_INTEGRATION", "1");
    Process process = builder.start();
    Thread hook =
        new Thread(
            () -> {
              process.descendants().forEach(ProcessHandle::destroy);
              process.destroy();
            });
    Runtime.getRuntime().addShutdownHook(hook);
    int code;
    try {
      process.getInputStream().transferTo(System.out);
      code = process.waitFor();
    } finally {
      if (process.isAlive()) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
      }
      Runtime.getRuntime().removeShutdownHook(hook);
      Files.createDirectories(policyPath.getParent());
      Files.writeString(policyPath, json(policy) + "\n");
    }
    if (code != 0)
      throw new IOException(
          "JAIPilot coverage failed (exit "
              + code
              + "). Review the measured requirements and blockers above.");
  }
}
