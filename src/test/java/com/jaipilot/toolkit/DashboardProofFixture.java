package com.jaipilot.toolkit;

import java.util.List;
import java.util.Map;

final class DashboardProofFixture {

    private DashboardProofFixture() {
    }

    static Map<String, Object> proof(boolean passed) {
        return Map.of(
                "ok", passed,
                "result", Map.of(
                        "passed", passed,
                        "targets", List.of("com.example.PaymentService"),
                        "changedQuality", Map.of("score", passed ? 96.0d : 88.0d),
                        "quality", quality(passed),
                        "testQuality", Map.of(
                                "score", passed ? 93.0d : 72.0d,
                                "lineCoverage", passed ? 94.0d : 80.0d,
                                "branchCoverage", passed ? 86.0d : 60.0d
                        ),
                        "mutation", Map.of("mutationScore", passed ? 90.0d : 65.0d),
                        "architecture", architecture(passed),
                        "failures", passed ? List.of() : List.of("ArchUnit found one changed-code package cycle."),
                        "warnings", passed ? List.of() : List.of("Mutation score remains below the preferred target.")
                )
        );
    }

    static Map<String, Object> quality(boolean clean) {
        List<Map<String, Object>> findings = clean
                ? List.of()
                : List.of(Map.of(
                        "id", "JAI-QUAL-001",
                        "category", "COMPLEXITY",
                        "severity", "HIGH",
                        "relativePath", "src/main/java/com/example/PaymentService.java",
                        "line", 42,
                        "symbol", "settle",
                        "message", "Method complexity exceeds the deterministic limit.",
                        "remediation", "Extract the decision branches into named methods."
                ));
        return Map.of(
                "findings", findings,
                "parseFailures", List.of(),
                "metrics", Map.of(
                        "findingCount", findings.size(),
                        "findingsBySeverity", clean ? Map.of() : Map.of("HIGH", 1),
                        "qualityScore", clean ? 96.0d : 88.0d
                )
        );
    }

    static Map<String, Object> architecture(boolean clean) {
        List<Map<String, Object>> violations = clean
                ? List.of()
                : List.of(Map.of(
                        "id", "JAI-ARCH-001",
                        "severity", "HIGH",
                        "originClass", "com.example.payment.PaymentService",
                        "targetClass", "com.example.order.OrderService",
                        "relativePath", "src/main/java/com/example/payment/PaymentService.java",
                        "line", 17,
                        "cyclePackages", List.of("com.example.order", "com.example.payment", "com.example.order"),
                        "message", "Package cycle crosses the changed PaymentService.",
                        "remediation", "Invert the cross-package dependency."
                ));
        return Map.of(
                "engine", "ArchUnit",
                "engineVersion", "1.4.2",
                "rulesetVersion", 1,
                "rules", List.of("JAI-ARCH-001"),
                "complete", true,
                "compiledClassCount", 17,
                "missingTargetClasses", List.of(),
                "violations", violations
        );
    }
}
