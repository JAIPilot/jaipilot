package com.jaipilot.gradle;

import com.jaipilot.build.CoverageSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;

public final class JAIPilotPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    JAIPilotExtension extension =
        project.getExtensions().create("jaipilot", JAIPilotExtension.class);
    extension.getForwardProperties().convention(List.of("enableTestCoverage"));
    if (project.hasProperty("jaipilot.forwardProperties"))
      extension
          .getForwardProperties()
          .set(
              java.util.Arrays.asList(
                  project.property("jaipilot.forwardProperties").toString().split(",")));
    extension.getExecutable().convention("jaipilot");
    extension.getMaxIterations().convention(3);
    extension.getTimeoutSeconds().convention(1200);
    if (project.hasProperty("jaipilot.coverage.line"))
      extension
          .getLineCoverage()
          .set(CoverageSupport.percentage(project.property("jaipilot.coverage.line").toString()));
    if (project.hasProperty("jaipilot.coverage.branch"))
      extension
          .getBranchCoverage()
          .set(CoverageSupport.percentage(project.property("jaipilot.coverage.branch").toString()));
    if (project.hasProperty("jaipilot.executable"))
      extension.getExecutable().set(project.property("jaipilot.executable").toString());
    project
        .getTasks()
        .withType(JacocoReport.class)
        .configureEach(
            report -> {
              if (project.hasProperty("jaipilot.coverage.xml"))
                report.getReports().getXml().getRequired().set(true);
            });
    for (String name : List.of("jaipilotCheck", "jaipilotRun"))
      project
          .getTasks()
          .register(
              name,
              task -> {
                task.setGroup("verification");
                task.setDescription(
                    name.equals("jaipilotCheck")
                        ? "Verify fresh JaCoCo coverage against configured and explicit"
                            + " requirements"
                        : "Generate and verify tests against JaCoCo requirements");
                task.doNotTrackState(
                    "JAIPilot runs fresh tests and may generate tests; results cannot be reused"
                        + " from the build cache");
                task.notCompatibleWithConfigurationCache(
                    "Coverage policy currently reads the evaluated project model at execution"
                        + " time");
                task.doLast(
                    t -> {
                      if (Boolean.parseBoolean(
                          String.valueOf(project.findProperty("jaipilot.skip")))) return;
                      execute(project, extension, name.equals("jaipilotCheck"));
                    });
              });
  }

  private void execute(Project project, JAIPilotExtension extension, boolean checkOnly) {
    try {
      if (!project.getPlugins().hasPlugin("jacoco"))
        throw new IllegalArgumentException(
            "JaCoCo is not active in "
                + project.getPath()
                + ". Enable the project's existing coverage configuration first.");
      if (!(project.getTasks().findByName("jacocoTestReport") instanceof JacocoReport report))
        throw new IllegalArgumentException(
            "No jacocoTestReport task in "
                + project.getPath()
                + "; this release supports the standard Java test report");
      if (!report.getEnabled())
        throw new IllegalArgumentException("JaCoCo report task is disabled");
      if (!(project.getTasks().findByName("test") instanceof Test test))
        throw new IllegalArgumentException("No Java test task");
      JacocoTaskExtension agent = test.getExtensions().findByType(JacocoTaskExtension.class);
      if (!test.getEnabled() || agent == null || !agent.isEnabled())
        throw new IllegalArgumentException("Tests or JaCoCo instrumentation are disabled");
      if (!report
          .getExecutionData()
          .getFiles()
          .equals(java.util.Set.of(agent.getDestinationFile()))) {
        throw new IllegalArgumentException(
            "JaCoCo report must use only the active Java test execution data; custom suites require"
                + " an explicit policy");
      }
      Path directory =
          project.getRootProject().getProjectDir().toPath().toAbsolutePath().normalize();
      Path repository = CoverageSupport.repository(directory);
      String reportPath =
          CoverageSupport.relative(
              repository,
              report.getReports().getXml().getOutputLocation().get().getAsFile().toPath());
      List<Map<String, Object>> targets = new ArrayList<>();
      List<String> checks = new ArrayList<>();
      List<String> classes =
          project.hasProperty("jaipilot.coverage.classes")
              ? java.util.Arrays.stream(
                      project.property("jaipilot.coverage.classes").toString().split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .toList()
              : List.of();
      for (JacocoCoverageVerification verification :
          project.getTasks().withType(JacocoCoverageVerification.class)) {
        if (!verification.getEnabled()) continue;
        if (!verification
            .getClassDirectories()
            .getFiles()
            .equals(report.getClassDirectories().getFiles()))
          throw new IllegalArgumentException(
              "JaCoCo verification and report class directories differ; configure matching coverage"
                  + " scope first");
        for (var rule : verification.getViolationRules().getRules()) {
          if (!rule.isEnabled()) continue;
          checks.add(verification.getPath());
          for (var limit : rule.getLimits()) {
            if (!limit.getValue().equals("COVEREDRATIO")
                || limit.getMinimum() == null
                || limit.getMaximum() != null
                || !List.of("LINE", "BRANCH", "INSTRUCTION").contains(limit.getCounter())
                || !List.of("BUNDLE", "PACKAGE", "CLASS").contains(rule.getElement()))
              throw new IllegalArgumentException(
                  "Unsupported automatic JaCoCo rule; no existing rule was weakened");
            targets.add(
                CoverageSupport.target(
                    limit.getCounter(),
                    rule.getElement(),
                    limit.getMinimum().multiply(java.math.BigDecimal.valueOf(100)).doubleValue(),
                    reportPath,
                    rule.getIncludes(),
                    rule.getExcludes()));
          }
        }
      }
      if (extension.getLineCoverage().isPresent())
        targets.add(
            CoverageSupport.target(
                "LINE",
                classes.isEmpty() ? "BUNDLE" : "CLASS",
                CoverageSupport.percentage(extension.getLineCoverage().get().toString()),
                reportPath,
                classes,
                List.of()));
      if (extension.getBranchCoverage().isPresent())
        targets.add(
            CoverageSupport.target(
                "BRANCH",
                classes.isEmpty() ? "BUNDLE" : "CLASS",
                CoverageSupport.percentage(extension.getBranchCoverage().get().toString()),
                reportPath,
                classes,
                List.of()));
      if (targets.isEmpty())
        throw new IllegalArgumentException(
            "No JaCoCo minimum is configured. Set -Pjaipilot.coverage.line=80 or configure"
                + " violationRules.");
      String launcher =
          Files.exists(directory.resolve(CoverageSupport.windows() ? "gradlew.bat" : "gradlew"))
              ? (CoverageSupport.windows() ? "gradlew.bat" : "./gradlew")
              : (CoverageSupport.windows() ? "gradle.bat" : "gradle");
      String prefix =
          directory.equals(repository)
              ? ""
              : (CoverageSupport.windows() ? "cd /d " : "cd ")
                  + CoverageSupport.quote(directory.toString())
                  + " && ";
      String common =
          " --no-daemon --no-build-cache -Pjaipilot.skip=true -Pjaipilot.coverage.xml=true";
      for (var init : project.getGradle().getStartParameter().getInitScripts())
        common += " --init-script " + CoverageSupport.quote(init.toString());
      // Forward only named build switches; never copy arbitrary credentials into the policy.
      for (var entry : project.getGradle().getStartParameter().getProjectProperties().entrySet())
        if (extension.getForwardProperties().get().contains(entry.getKey()))
          common += " -P" + CoverageSupport.quote(entry.getKey() + "=" + entry.getValue());
      String command = prefix + launcher;
      SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
      List<String> roots =
          sourceSets.getByName("test").getAllSource().getSrcDirs().stream()
              .map(f -> CoverageSupport.relative(repository, f.toPath()))
              .toList();
      List<String> scope =
          sourceSets.getByName("main").getAllJava().getSrcDirs().stream()
              .map(f -> CoverageSupport.relative(repository, f.toPath()))
              .toList();
      Map<String, Object> policy = new LinkedHashMap<>();
      policy.put("version", 1);
      policy.put("provider", "jacoco");
      policy.put("targets", targets);
      policy.put("reports", List.of(reportPath));
      policy.put(
          "executionData",
          List.of(CoverageSupport.relative(repository, agent.getDestinationFile().toPath())));
      policy.put("scopePaths", CoverageSupport.classSources(repository, scope, classes));
      policy.put("testRoots", roots);
      policy.put("testCommand", command + " " + test.getPath() + common + " --rerun-tasks");
      policy.put(
          "reportCommand", command + " " + report.getPath() + common + " -x " + test.getPath());
      if (!checks.isEmpty())
        policy.put(
            "checkCommand",
            command
                + " "
                + String.join(" ", checks.stream().distinct().toList())
                + common
                + " -x "
                + test.getPath());
      policy.put("maxIterations", extension.getMaxIterations().get());
      policy.put("timeoutSeconds", extension.getTimeoutSeconds().get());
      project
          .getLogger()
          .lifecycle(
              "JAIPilot uses fresh JaCoCo data and preserves every configured coverage"
                  + " requirement.");
      CoverageSupport.execute(
          repository,
          project
              .getLayout()
              .getBuildDirectory()
              .file("jaipilot/coverage-policy.json")
              .get()
              .getAsFile()
              .toPath(),
          policy,
          extension.getExecutable().get(),
          checkOnly);
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new GradleException(e.getMessage(), e);
    }
  }
}
