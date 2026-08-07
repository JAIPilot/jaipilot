package com.jaipilot.toolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureServiceTest {

    @TempDir
    Path tempDir;

    private ArchitectureService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        ProjectFileService files = new ProjectFileService();
        service = new ArchitectureService(new JavaProjectService(files, new CoverageReportService()));
    }

    @Test
    void reportsPackageCyclesThatInvolveTheSelectedClass() throws Exception {
        writeSource(
                "com/example/orders/OrderService.java",
                """
                        package com.example.orders;
                        import com.example.inventory.Inventory;
                        public class OrderService { static class Handler { private Inventory inventory; } }
                        """
        );
        writeSource(
                "com/example/inventory/Inventory.java",
                """
                        package com.example.inventory;
                        import com.example.orders.OrderService;
                        public class Inventory { OrderService service() { return null; } }
                        """
        );
        compileSources();

        ArchitectureService.ArchitectureReport report = service.analyze(
                tempDir,
                List.of("com.example.orders.OrderService")
        );

        assertTrue(report.complete());
        assertFalse(report.goalMet());
        assertEquals(ArchitectureService.RULESET_VERSION, report.rulesetVersion());
        assertEquals(3, report.compiledClassCount());
        assertEquals(List.of("target/classes"), report.classOutputRoots());
        assertEquals(1, report.violations().size());
        ArchitectureService.ArchitectureViolation violation = report.violations().get(0);
        assertEquals(ArchitectureService.PACKAGE_CYCLE_RULE, violation.id());
        assertEquals("HIGH", violation.severity());
        assertEquals(List.of(
                "com.example.inventory",
                "com.example.orders",
                "com.example.inventory"
        ), violation.cyclePackages());
        assertEquals(List.of("com.example.orders.OrderService"), violation.affectedTargets());
        assertTrue(violation.relativePath().endsWith(".java"));
        assertTrue(violation.line() > 0);
        assertTrue(violation.message().contains("Package cycle"));
    }

    @Test
    void ignoresExistingCyclesThatDoNotInvolveTheSelectedClass() throws Exception {
        writeSource(
                "com/example/a/A.java",
                "package com.example.a; public class A { com.example.b.B b; }\n"
        );
        writeSource(
                "com/example/b/B.java",
                "package com.example.b; public class B { com.example.a.A a; }\n"
        );
        writeSource(
                "com/example/c/C.java",
                "package com.example.c; public class C {}\n"
        );
        compileSources();

        ArchitectureService.ArchitectureReport report = service.analyze(
                tempDir,
                List.of("com.example.c.C")
        );

        assertTrue(report.complete());
        assertTrue(report.goalMet());
        assertTrue(report.violations().isEmpty());
    }

    @Test
    void failsClosedWhenSelectedBytecodeIsMissing() throws Exception {
        writeSource(
                "com/example/orders/OrderService.java",
                "package com.example.orders; public class OrderService {}\n"
        );

        ArchitectureService.ArchitectureReport report = service.analyze(
                tempDir,
                List.of("com.example.orders.OrderService")
        );

        assertFalse(report.complete());
        assertFalse(report.goalMet());
        assertEquals(List.of("com.example.orders.OrderService"), report.missingTargetClasses());
        assertTrue(report.incompleteReason().contains("No compiled production class directories"));
    }

    @Test
    void producesStableViolationOrdering() throws Exception {
        writeSource(
                "com/example/a/A.java",
                "package com.example.a; public class A { com.example.b.B b; }\n"
        );
        writeSource(
                "com/example/b/B.java",
                "package com.example.b; public class B { com.example.a.A a; }\n"
        );
        compileSources();

        ArchitectureService.ArchitectureReport first = service.analyze(
                tempDir,
                List.of("com.example.b.B", "com.example.a.A")
        );
        ArchitectureService.ArchitectureReport second = service.analyze(
                tempDir,
                List.of("com.example.a.A", "com.example.b.B")
        );

        assertEquals(first.targetClasses(), second.targetClasses());
        assertEquals(first.violations(), second.violations());
    }

    private void writeSource(String relativePath, String source) throws IOException {
        Path path = tempDir.resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    private void compileSources() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path output = tempDir.resolve("target/classes");
        Files.createDirectories(output);
        List<Path> sources;
        try (var paths = Files.walk(tempDir.resolve("src/main/java"))) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            boolean compiled = compiler.getTask(
                    null,
                    files,
                    null,
                    List.of("--release", "17", "-d", output.toString()),
                    null,
                    files.getJavaFileObjectsFromPaths(sources)
            ).call();
            assertTrue(compiled);
        }
    }
}
