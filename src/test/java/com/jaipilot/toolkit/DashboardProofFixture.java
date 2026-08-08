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

    static Map<String, Object> postCommitQuality(boolean clean) {
        return Map.of(
                "analysisStatus", "analyzed",
                "revision", "0123456789abcdef0123456789abcdef01234567",
                "fingerprint", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "currentQuality", Map.of(
                        "projectRoot", "/private/project",
                        "targets", List.of(
                                "com.example.PaymentService",
                                "com.example.InvoiceService"
                        ),
                        "quality", quality(clean)
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
                "metrics", Map.ofEntries(
                        Map.entry("fileCount", 4),
                        Map.entry("linesOfCode", 240),
                        Map.entry("sourceBytes", 8_192L),
                        Map.entry("methodCount", 18),
                        Map.entry("findingCount", findings.size()),
                        Map.entry("bugRiskCount", clean ? 0 : 1),
                        Map.entry("codeSmellCount", clean ? 0 : 1),
                        Map.entry("modernizationOpportunityCount", clean ? 0 : 2),
                        Map.entry("findingsBySeverity", clean ? Map.of() : Map.of("HIGH", 1)),
                        Map.entry("maximumCyclomaticComplexity", clean ? 5 : 14),
                        Map.entry("averageCyclomaticComplexity", clean ? 2.1d : 4.6d),
                        Map.entry("maximumCognitiveComplexity", clean ? 6 : 19),
                        Map.entry("duplicatedLineCount", clean ? 0 : 24),
                        Map.entry("duplicationPercent", clean ? 0.0d : 10.0d),
                        Map.entry("remediationDebtMinutes", clean ? 0 : 55),
                        Map.entry("remediationDebtRatioPercent", clean ? 0.0d : 3.8d),
                        Map.entry("reliabilityScore", clean ? 100.0d : 75.0d),
                        Map.entry("maintainabilityScore", clean ? 98.0d : 84.0d),
                        Map.entry("complexityScore", clean ? 94.0d : 70.0d),
                        Map.entry("duplicationScore", clean ? 100.0d : 80.0d),
                        Map.entry("qualityScore", clean ? 96.0d : 88.0d),
                        Map.entry("analysisElapsedNanos", 125_000_000L)
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
