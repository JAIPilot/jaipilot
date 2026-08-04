package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaQualityServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsBugsComplexityDuplicationAndTransparentScores() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/example/QualityFixture.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                import java.math.BigDecimal;

                class QualityFixture {
                    public static Object shared;

                    void broken(boolean a, boolean b, boolean c, boolean d, boolean e, boolean f,
                            boolean g, boolean h) {
                        try {
                            System.out.println(new BigDecimal(0.1));
                        } catch (InterruptedException ignored) {
                        }
                        if (a) { System.out.println("a"); }
                        if (b) { System.out.println("b"); }
                        if (c) { System.out.println("c"); }
                        if (d) { System.out.println("d"); }
                        if (e) { System.out.println("e"); }
                        if (f) { System.out.println("f"); }
                        if (g) { System.out.println("g"); }
                        if (h) { System.out.println("h"); }
                        if (a && b && c) { System.out.println("many"); }
                    }

                    void duplicateOne() {
                        System.out.println("a long duplicated statement one");
                        System.out.println("a long duplicated statement two");
                        System.out.println("a long duplicated statement three");
                        System.out.println("a long duplicated statement four");
                    }

                    void duplicateTwo() {
                        System.out.println("a long duplicated statement one");
                        System.out.println("a long duplicated statement two");
                        System.out.println("a long duplicated statement three");
                        System.out.println("a long duplicated statement four");
                    }
                }
                """);

        JavaQualityService.QualityReport report = new JavaQualityService().analyze(tempDir, List.of(source));
        Set<String> ids = report.findings().stream().map(JavaQualityService.Finding::id).collect(Collectors.toSet());

        assertTrue(ids.contains("JAI-BUG-001"));
        assertTrue(ids.contains("JAI-BUG-003"));
        assertTrue(ids.contains("JAI-BUG-004"));
        assertTrue(ids.contains("JAI-CPLX-001"));
        assertTrue(ids.contains("JAI-SMELL-002"));
        assertTrue(ids.contains("JAI-DUP-001"));
        assertEquals(1, report.duplications().size());
        assertEquals(1, report.metrics().fileCount());
        assertTrue(report.metrics().bugRiskCount() >= 4);
        assertTrue(report.metrics().codeSmellCount() >= 1);
        assertTrue(report.metrics().duplicationPercent() > 0.0d);
        assertTrue(report.metrics().remediationDebtMinutes() > 0);
        assertTrue(report.metrics().qualityScore() >= 0.0d && report.metrics().qualityScore() <= 100.0d);
        assertTrue(report.metrics().analysisElapsedNanos() > 0L);
    }

    @Test
    void reportsParseFailuresWithoutInventingPerfectEvidence() throws Exception {
        Path source = tempDir.resolve("src/main/java/Broken.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Broken { void nope( }\n");

        JavaQualityService.QualityReport report = new JavaQualityService().analyze(tempDir, List.of(source));

        assertEquals(1, report.parseFailures().size());
        assertEquals(0, report.metrics().methodCount());
    }
}
