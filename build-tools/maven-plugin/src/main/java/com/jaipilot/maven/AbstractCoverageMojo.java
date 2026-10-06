package com.jaipilot.maven;

import com.jaipilot.build.CoverageSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

abstract class AbstractCoverageMojo extends AbstractMojo {
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  protected MavenSession session;

  @Parameter(defaultValue = "${mojoExecution}", readonly = true)
  protected MojoExecution execution;

  @Parameter(property = "jaipilot.coverage.line")
  protected String line;

  @Parameter(property = "jaipilot.coverage.branch")
  protected String branch;

  @Parameter(property = "jaipilot.coverage.classes")
  protected String coverageClasses;

  @Parameter(property = "jaipilot.executable", defaultValue = "jaipilot")
  protected String executable;

  @Parameter(property = "jaipilot.skip", defaultValue = "false")
  protected boolean skip;

  @Parameter(property = "jaipilot.maxIterations", defaultValue = "3")
  protected int maxIterations;

  @Parameter(property = "jaipilot.timeoutSeconds", defaultValue = "1200")
  protected int timeoutSeconds;

  private MavenSession evaluatedSession;

  protected abstract boolean checkOnly();

  private String text(Xpp3Dom parent, String child, String fallback) throws Exception {
    Xpp3Dom node = parent == null ? null : parent.getChild(child);
    if (node == null || node.getValue() == null) return fallback;
    Object value =
        new PluginParameterExpressionEvaluator(evaluatedSession, execution)
            .evaluate(node.getValue());
    return String.valueOf(value).trim();
  }

  private List<String> patterns(Xpp3Dom parent, String child) throws Exception {
    Xpp3Dom node = parent == null ? null : parent.getChild(child);
    if (node == null) return List.of();
    List<String> result = new ArrayList<>();
    for (Xpp3Dom pattern : node.getChildren())
      result.add(
          String.valueOf(
              new PluginParameterExpressionEvaluator(evaluatedSession, execution)
                  .evaluate(pattern.getValue())));
    return result;
  }

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) return;
    try {
      Path directory =
          session.getTopLevelProject().getBasedir().toPath().toAbsolutePath().normalize();
      Path repository = CoverageSupport.repository(directory);
      List<String> reports = new ArrayList<>(),
          roots = new ArrayList<>(),
          executionData = new ArrayList<>(),
          scope = new ArrayList<>();
      List<Map<String, Object>> targets = new ArrayList<>();
      List<String> classes =
          coverageClasses == null
              ? List.of()
              : java.util.Arrays.stream(coverageClasses.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .toList();
      boolean gate = false;
      String reportGoal = null;
      for (MavenProject project : session.getProjects()) {
        if (project.getPackaging().equals("pom")) continue;
        evaluatedSession = session.clone();
        evaluatedSession.setCurrentProject(project);
        Plugin jacoco =
            project.getBuildPlugins().stream()
                .filter(
                    p ->
                        p.getGroupId().equals("org.jacoco")
                            && p.getArtifactId().equals("jacoco-maven-plugin"))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "JaCoCo is not configured in "
                                + project.getArtifactId()
                                + "; explicitly set up coverage first"));
        Xpp3Dom base = (Xpp3Dom) jacoco.getConfiguration();
        if (text(base, "skip", "false").equals("true")
            || "true".equals(session.getUserProperties().getProperty("jacoco.skip")))
          throw new IllegalArgumentException("JaCoCo is disabled in " + project.getArtifactId());
        boolean agent =
            jacoco.getExecutions().stream().anyMatch(e -> e.getGoals().contains("prepare-agent"));
        if (!agent)
          throw new IllegalArgumentException(
              "JaCoCo prepare-agent is not configured in " + project.getArtifactId());
        Path reportDirectory = Path.of(project.getReporting().getOutputDirectory(), "jacoco");
        Xpp3Dom reportConfig = base;
        String moduleReportGoal = "jacoco:report";
        int reportExecutions = 0;
        for (PluginExecution e : jacoco.getExecutions())
          if (e.getGoals().contains("report")) {
            reportExecutions++;
            if (reportExecutions > 1)
              throw new IllegalArgumentException(
                  "Multiple JaCoCo report executions are not yet supported; provide an explicit"
                      + " native policy");
            Xpp3Dom config = (Xpp3Dom) e.getConfiguration();
            reportConfig =
                config == null
                    ? base
                    : (base == null
                        ? config
                        : Xpp3Dom.mergeXpp3Dom(new Xpp3Dom(config), new Xpp3Dom(base)));
            moduleReportGoal = "jacoco:report@" + e.getId();
            String configured = text(reportConfig, "outputDirectory", reportDirectory.toString());
            reportDirectory = project.getBasedir().toPath().resolve(configured).normalize();
          }
        if (reportGoal != null && !reportGoal.equals(moduleReportGoal))
          throw new IllegalArgumentException(
              "Reactor JaCoCo report execution IDs must match; use separate module invocations or"
                  + " an explicit native policy");
        reportGoal = moduleReportGoal;
        String report = CoverageSupport.relative(repository, reportDirectory.resolve("jacoco.xml"));
        reports.add(report);
        roots.add(
            CoverageSupport.relative(
                repository, Path.of(project.getBuild().getTestSourceDirectory())));
        for (var resource : project.getTestResources()) {
          String path = CoverageSupport.relative(repository, Path.of(resource.getDirectory()));
          if (!roots.contains(path)) roots.add(path);
        }
        scope.add(
            CoverageSupport.relative(repository, Path.of(project.getBuild().getSourceDirectory())));
        String destination =
            text(
                base,
                "destFile",
                Path.of(project.getBuild().getDirectory(), "jacoco.exec").toString());
        for (PluginExecution e : jacoco.getExecutions())
          if (e.getGoals().contains("prepare-agent"))
            destination = text((Xpp3Dom) e.getConfiguration(), "destFile", destination);
        Path data = project.getBasedir().toPath().resolve(destination).normalize();
        Path reportData =
            project
                .getBasedir()
                .toPath()
                .resolve(
                    text(
                        reportConfig,
                        "dataFile",
                        Path.of(project.getBuild().getDirectory(), "jacoco.exec").toString()))
                .normalize();
        if (!data.equals(reportData))
          throw new IllegalArgumentException(
              "JaCoCo agent and report data paths differ in "
                  + project.getArtifactId()
                  + "; align them before generation");
        executionData.add(CoverageSupport.relative(repository, data));
        for (PluginExecution e : jacoco.getExecutions())
          if (e.getGoals().contains("check")) {
            gate = true;
            Xpp3Dom config = (Xpp3Dom) e.getConfiguration();
            if (config == null) config = base;
            else if (base != null)
              config = Xpp3Dom.mergeXpp3Dom(new Xpp3Dom(config), new Xpp3Dom(base));
            Xpp3Dom rules = config == null ? null : config.getChild("rules");
            if (rules == null) continue;
            for (Xpp3Dom rule : rules.getChildren()) {
              String element = text(rule, "element", "BUNDLE");
              Xpp3Dom limits = rule.getChild("limits");
              if (limits == null) continue;
              for (Xpp3Dom limit : limits.getChildren()) {
                String counter = text(limit, "counter", "INSTRUCTION"),
                    value = text(limit, "value", "COVEREDRATIO"),
                    minimum = text(limit, "minimum", ""),
                    maximum = text(limit, "maximum", "");
                if (!value.equals("COVEREDRATIO")
                    || minimum.isEmpty()
                    || !maximum.isEmpty()
                    || !List.of("LINE", "BRANCH", "INSTRUCTION").contains(counter)
                    || !List.of("BUNDLE", "PACKAGE", "CLASS").contains(element))
                  throw new IllegalArgumentException(
                      "Unsupported automatic JaCoCo rule in "
                          + project.getArtifactId()
                          + "; no rule was weakened");
                double percent = CoverageSupport.percentage(minimum.replace("%", ""));
                if (!minimum.endsWith("%")) percent *= 100;
                if (percent > 100) throw new IllegalArgumentException("Invalid JaCoCo ratio");
                targets.add(
                    CoverageSupport.target(
                        counter,
                        element,
                        percent,
                        report,
                        patterns(rule, "includes"),
                        patterns(rule, "excludes")));
              }
            }
          }
        if (line != null)
          targets.add(
              CoverageSupport.target(
                  "LINE",
                  classes.isEmpty() ? "BUNDLE" : "CLASS",
                  CoverageSupport.percentage(line),
                  report,
                  classes,
                  List.of()));
        if (branch != null)
          targets.add(
              CoverageSupport.target(
                  "BRANCH",
                  classes.isEmpty() ? "BUNDLE" : "CLASS",
                  CoverageSupport.percentage(branch),
                  report,
                  classes,
                  List.of()));
      }
      if (targets.isEmpty())
        throw new IllegalArgumentException(
            "No JaCoCo minimum is configured. Set -Djaipilot.coverage.line=80 or configure a JaCoCo"
                + " check rule.");
      String launcher =
          Files.exists(directory.resolve(CoverageSupport.windows() ? "mvnw.cmd" : "mvnw"))
              ? (CoverageSupport.windows() ? "mvnw.cmd" : "./mvnw")
              : (CoverageSupport.windows() ? "mvn.cmd" : "mvn");
      String prefix =
          directory.equals(repository)
              ? ""
              : (CoverageSupport.windows() ? "cd /d " : "cd ")
                  + CoverageSupport.quote(directory.toString())
                  + " && ";
      String common = prefix + launcher + " -Djaipilot.skip=true -Dstyle.color=never";
      if (!session.getRequest().getActiveProfiles().isEmpty())
        common +=
            " -P"
                + CoverageSupport.quote(String.join(",", session.getRequest().getActiveProfiles()));
      if (session.getProjects().size() > 1) {
        String selected =
            String.join(
                ",",
                session.getProjects().stream()
                    .filter(p -> !p.getPackaging().equals("pom"))
                    .map(p -> ":" + p.getArtifactId())
                    .toList());
        if (!selected.isEmpty()) common += " -pl " + CoverageSupport.quote(selected) + " -am";
      }
      Map<String, Object> policy = new LinkedHashMap<>();
      policy.put("version", 1);
      policy.put("provider", "jacoco");
      policy.put("targets", targets);
      policy.put("reports", reports);
      policy.put("executionData", executionData);
      policy.put("scopePaths", CoverageSupport.classSources(repository, scope, classes));
      policy.put("testRoots", roots);
      policy.put("testCommand", common + " clean test");
      policy.put("reportCommand", common + " " + reportGoal);
      if (gate) policy.put("checkCommand", common + " verify");
      policy.put("maxIterations", maxIterations);
      policy.put("timeoutSeconds", timeoutSeconds);
      Path output = directory.resolve("target/jaipilot/coverage-policy.json");
      getLog()
          .info(
              "Using JaCoCo requirements from the evaluated Maven build; explicit targets add"
                  + " requirements.");
      CoverageSupport.execute(repository, output, policy, executable, checkOnly());
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }
}
