package com.jaipilot.toolkit.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/** Fast, deterministic source-quality analysis for selected Java production files. */
@SuppressWarnings({"rawtypes", "unchecked"}) // JavaParser exposes raw Class tokens for generic AST declarations.
public final class JavaQualityService {

    public static final int CYCLOMATIC_COMPLEXITY_LIMIT = 10;
    public static final int COGNITIVE_COMPLEXITY_LIMIT = 15;
    public static final int NESTING_DEPTH_LIMIT = 4;
    public static final int METHOD_LINE_LIMIT = 80;
    public static final int PARAMETER_LIMIT = 7;

    private static final int DUPLICATE_STATEMENT_WINDOW = 4;
    private static final int MINIMUM_DUPLICATE_CHARACTERS = 80;
    private static final int MAX_ANALYSIS_THREADS = 4;
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern CHARACTER_PATTERN = Pattern.compile("'(?:\\\\.|[^'\\\\])'");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![\\w$])(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d[\\d_]*)?)[fFdDlL]?");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final ParserConfiguration parserConfiguration;

    public JavaQualityService() {
        this(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setCharacterEncoding(StandardCharsets.UTF_8));
    }

    JavaQualityService(ParserConfiguration parserConfiguration) {
        this.parserConfiguration = Objects.requireNonNull(parserConfiguration, "parserConfiguration");
    }

    public QualityReport analyze(Path projectRoot, List<Path> sourceFiles) {
        Path root = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        List<Path> files = normalizeFiles(root, sourceFiles);
        long started = System.nanoTime();
        List<FileAnalysis> analyses = analyzeFiles(root, files);

        List<Finding> findings = new ArrayList<>();
        List<ParseFailure> parseFailures = new ArrayList<>();
        List<MethodMetric> methods = new ArrayList<>();
        List<DuplicateCandidate> duplicateCandidates = new ArrayList<>();
        int linesOfCode = 0;
        long sourceBytes = 0L;
        for (FileAnalysis analysis : analyses) {
            findings.addAll(analysis.findings());
            parseFailures.addAll(analysis.parseFailures());
            methods.addAll(analysis.methods());
            duplicateCandidates.addAll(analysis.duplicateCandidates());
            linesOfCode += analysis.linesOfCode();
            sourceBytes += analysis.sourceBytes();
        }

        List<DuplicateBlock> duplications = findDuplications(duplicateCandidates);
        for (DuplicateBlock duplicate : duplications) {
            findings.add(new Finding(
                    "JAI-DUP-001",
                    Category.DUPLICATION,
                    Severity.MEDIUM,
                    duplicate.secondPath(),
                    duplicate.secondLine(),
                    duplicate.secondSymbol(),
                    "Duplicated statement block also appears at " + duplicate.firstPath() + ":" + duplicate.firstLine() + ".",
                    "Extract the shared behavior or keep one authoritative implementation.",
                    false,
                    Math.max(10, duplicate.lineCount() * 2)
            ));
        }
        findings.sort(Finding.ORDER);

        int duplicateLines = duplications.stream().mapToInt(DuplicateBlock::lineCount).sum();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        QualityMetrics metrics = metrics(files.size(), linesOfCode, sourceBytes, methods, findings, duplicateLines, elapsed);
        return new QualityReport(
                List.copyOf(findings),
                List.copyOf(methods.stream().sorted(MethodMetric.ORDER).toList()),
                List.copyOf(duplications),
                List.copyOf(parseFailures),
                metrics
        );
    }

    private List<Path> normalizeFiles(Path root, List<Path> sourceFiles) {
        Objects.requireNonNull(sourceFiles, "sourceFiles");
        LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        sourceFiles.stream().filter(Objects::nonNull).map(path -> path.toAbsolutePath().normalize()).sorted().forEach(path -> {
            if (!path.startsWith(root)
                    || !Files.isRegularFile(path)
                    || Files.isSymbolicLink(path)
                    || !path.toString().endsWith(".java")) {
                throw new IllegalArgumentException("Quality target is not a regular Java file under the project: " + path);
            }
            normalized.add(path);
        });
        return List.copyOf(normalized);
    }

    private List<FileAnalysis> analyzeFiles(Path root, List<Path> files) {
        if (files.isEmpty()) {
            return List.of();
        }
        int threads = Math.min(MAX_ANALYSIS_THREADS, files.size());
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "jaipilot-quality");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Callable<FileAnalysis>> tasks = files.stream()
                    .<Callable<FileAnalysis>>map(file -> () -> analyzeFile(root, file))
                    .toList();
            List<Future<FileAnalysis>> futures = executor.invokeAll(tasks);
            List<FileAnalysis> results = new ArrayList<>(futures.size());
            for (Future<FileAnalysis> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException("Java quality analysis failed.", cause);
                }
            }
            return List.copyOf(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Java quality analysis was interrupted.", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private FileAnalysis analyzeFile(Path root, Path file) {
        Path relative = root.relativize(file).normalize();
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Java quality target " + file, exception);
        }
        JavaParser parser = new JavaParser(parserConfiguration);
        ParseResult<CompilationUnit> parsed = parser.parse(source);
        if (parsed.getResult().isEmpty()) {
            String problem = parsed.getProblems().stream()
                    .map(Object::toString)
                    .findFirst()
                    .orElse("Unknown Java parse failure");
            return new FileAnalysis(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new ParseFailure(portable(relative), problem)),
                    countCodeLines(source),
                    source.getBytes(StandardCharsets.UTF_8).length
            );
        }

        CompilationUnit unit = parsed.getResult().orElseThrow();
        List<Finding> findings = new ArrayList<>();
        List<MethodMetric> methods = analyzeMethods(relative, unit, findings);
        inspectBugRisks(relative, unit, findings);
        inspectModernization(relative, unit, findings);
        inspectIdeQuality(relative, unit, findings);
        List<DuplicateCandidate> duplicateCandidates = duplicateCandidates(relative, unit);
        List<ParseFailure> parseFailures = parsed.getProblems().stream()
                .map(problem -> new ParseFailure(portable(relative), problem.toString()))
                .toList();
        return new FileAnalysis(
                List.copyOf(findings),
                methods,
                duplicateCandidates,
                parseFailures,
                countCodeLines(source),
                source.getBytes(StandardCharsets.UTF_8).length
        );
    }

    private List<MethodMetric> analyzeMethods(Path path, CompilationUnit unit, List<Finding> findings) {
        List<MethodMetric> metrics = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            int cyclomatic = cyclomaticComplexity(callable);
            int cognitive = cognitiveComplexity(callable);
            int nesting = maximumNesting(callable);
            int lines = lineSpan(callable);
            int statements = (int) callable.findAll(Statement.class).stream()
                    .filter(statement -> belongsTo(statement, callable))
                    .count();
            String symbol = callable.getSignature().asString();
            int line = line(callable);
            metrics.add(new MethodMetric(portable(path), line, symbol, lines, statements, cyclomatic, cognitive, nesting));
            if (cyclomatic > CYCLOMATIC_COMPLEXITY_LIMIT) {
                add(findings, "JAI-CPLX-001", Category.COMPLEXITY,
                        cyclomatic > 20 ? Severity.HIGH : Severity.MEDIUM, path, line, symbol,
                        "Cyclomatic complexity is " + cyclomatic + " (limit " + CYCLOMATIC_COMPLEXITY_LIMIT + ").",
                        "Split independent decisions into cohesive methods and remove redundant branches.", false,
                        5 + (cyclomatic - CYCLOMATIC_COMPLEXITY_LIMIT) * 2);
            }
            if (cognitive > COGNITIVE_COMPLEXITY_LIMIT) {
                add(findings, "JAI-CPLX-002", Category.COMPLEXITY,
                        cognitive > 30 ? Severity.HIGH : Severity.MEDIUM, path, line, symbol,
                        "Cognitive complexity is " + cognitive + " (limit " + COGNITIVE_COMPLEXITY_LIMIT + ").",
                        "Flatten nesting, name intent, and extract focused decision units.", false,
                        5 + (cognitive - COGNITIVE_COMPLEXITY_LIMIT) * 2);
            }
            if (nesting > NESTING_DEPTH_LIMIT) {
                add(findings, "JAI-CPLX-003", Category.COMPLEXITY, Severity.MEDIUM, path, line, symbol,
                        "Control-flow nesting reaches " + nesting + " levels (limit " + NESTING_DEPTH_LIMIT + ").",
                        "Use guard clauses or extract nested behavior.", false, 10);
            }
            if (lines > METHOD_LINE_LIMIT || statements > 50) {
                add(findings, "JAI-SMELL-001", Category.CODE_SMELL, Severity.MEDIUM, path, line, symbol,
                        "Method spans " + lines + " lines and " + statements + " statements.",
                        "Separate responsibilities into named, testable units.", false, 20);
            }
            if (callable.getParameters().size() > PARAMETER_LIMIT) {
                add(findings, "JAI-SMELL-002", Category.CODE_SMELL, Severity.MEDIUM, path, line, symbol,
                        "Method has " + callable.getParameters().size() + " parameters (limit " + PARAMETER_LIMIT + ").",
                        "Introduce a cohesive parameter object or simplify the method contract.", false, 15);
            }
        }
        return List.copyOf(metrics);
    }

    private void inspectBugRisks(Path path, CompilationUnit unit, List<Finding> findings) {
        for (CatchClause clause : unit.findAll(CatchClause.class)) {
            String symbol = nearestSymbol(clause);
            String type = clause.getParameter().getType().asString();
            if (clause.getBody().getStatements().isEmpty()) {
                add(findings, "JAI-BUG-001", Category.BUG_RISK, Severity.HIGH, path, line(clause), symbol,
                        "Empty catch block silently discards " + type + ".",
                        "Handle the failure, restore state, or document and test why it is safe to ignore.", false, 30);
            }
            if (("Exception".equals(type) || "Throwable".equals(type) || type.endsWith(".Exception") || type.endsWith(".Throwable"))
                    && !type.contains("|")) {
                add(findings, "JAI-BUG-002", Category.BUG_RISK,
                        "Throwable".equals(type) || type.endsWith(".Throwable") ? Severity.HIGH : Severity.MEDIUM,
                        path, line(clause), symbol,
                        "Broad catch of " + type + " can hide unrelated failures.",
                        "Catch only recoverable exceptions and preserve unexpected failures.", false, 15);
            }
            if (type.contains("InterruptedException")) {
                String body = clause.getBody().toString();
                String parameter = clause.getParameter().getNameAsString();
                boolean restored = body.contains("Thread.currentThread().interrupt()")
                        || body.contains("throw " + parameter)
                        || body.contains("throw new InterruptedException");
                if (!restored) {
                    add(findings, "JAI-BUG-003", Category.BUG_RISK, Severity.CRITICAL, path, line(clause), symbol,
                            "InterruptedException is consumed without restoring or propagating interruption.",
                            "Call Thread.currentThread().interrupt() or propagate the interruption.", true, 20);
                }
            }
        }

        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            String type = creation.getType().getNameAsString();
            if ("BigDecimal".equals(type)
                    && creation.getArguments().size() == 1
                    && creation.getArgument(0) instanceof DoubleLiteralExpr) {
                add(findings, "JAI-BUG-004", Category.BUG_RISK, Severity.HIGH, path, line(creation), nearestSymbol(creation),
                        "BigDecimal constructed from a floating-point literal can encode an unintended value.",
                        "Use BigDecimal.valueOf or a decimal string.", true, 10);
            }
        }

        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String scope = call.getScope().map(Object::toString).orElse("");
            if ("printStackTrace".equals(call.getNameAsString())) {
                add(findings, "JAI-BUG-005", Category.BUG_RISK, Severity.MEDIUM, path, line(call), nearestSymbol(call),
                        "printStackTrace bypasses structured error handling.",
                        "Use the project's logging/error policy and preserve actionable context.", false, 10);
            }
            if (("System.out".equals(scope) || "System.err".equals(scope)) && call.getNameAsString().startsWith("print")) {
                add(findings, "JAI-SMELL-003", Category.CODE_SMELL, Severity.MEDIUM, path, line(call), nearestSymbol(call),
                        "Direct console output remains in production code.",
                        "Use the project's logging or user-output abstraction.", true, 5);
            }
            if (("System".equals(scope) || "Runtime.getRuntime()".equals(scope)) && "gc".equals(call.getNameAsString())) {
                add(findings, "JAI-PERF-001", Category.PERFORMANCE, Severity.MEDIUM, path, line(call), nearestSymbol(call),
                        "Manual garbage-collection request can introduce unpredictable pauses.",
                        "Let the JVM schedule collection unless measurement proves a specialized need.", true, 10);
            }
            if ("Thread".equals(scope) && "sleep".equals(call.getNameAsString())) {
                add(findings, "JAI-BUG-006", Category.BUG_RISK, Severity.MEDIUM, path, line(call), nearestSymbol(call),
                        "Thread.sleep couples behavior to wall-clock timing.",
                        "Use scheduling, coordination primitives, or an injectable clock.", false, 15);
            }
        }

        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            if (field.isStatic() && !field.isFinal() && (field.isPublic() || field.isProtected())) {
                add(findings, "JAI-BUG-007", Category.BUG_RISK, Severity.HIGH, path, line(field), nearestSymbol(field),
                        "Externally visible mutable static state is difficult to make thread-safe.",
                        "Encapsulate state, define ownership, and synchronize mutations explicitly.", false, 30);
            } else if (field.isPublic() && !field.isStatic()) {
                add(findings, "JAI-SMELL-004", Category.CODE_SMELL, Severity.MEDIUM, path, line(field), nearestSymbol(field),
                        "Public mutable field bypasses invariants and refactoring boundaries.",
                        "Encapsulate the field behind a focused API.", false, 15);
            }
        }
    }

    private void inspectModernization(Path path, CompilationUnit unit, List<Finding> findings) {
        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            if (creation.getAnonymousClassBody().isPresent()
                    && creation.getAnonymousClassBody().orElseThrow().stream()
                    .filter(MethodDeclaration.class::isInstance).count() == 1) {
                add(findings, "JAI-MOD-001", Category.MODERNIZATION, Severity.LOW, path, line(creation), nearestSymbol(creation),
                        "Single-method anonymous class may be expressible as a lambda or method reference.",
                        "Convert only when the target is a functional interface and behavior remains clear.", true, 5);
            }
            if ("StringBuffer".equals(creation.getType().getNameAsString())) {
                add(findings, "JAI-MOD-002", Category.MODERNIZATION, Severity.LOW, path, line(creation), nearestSymbol(creation),
                        "StringBuffer synchronization may be unnecessary for method-local construction.",
                        "Use StringBuilder when the instance is not shared across threads.", false, 5);
            }
        }

        for (IfStmt statement : unit.findAll(IfStmt.class)) {
            if (!(statement.getCondition() instanceof InstanceOfExpr instanceOf)
                    || !statement.getThenStmt().isBlockStmt()) {
                continue;
            }
            BlockStmt block = statement.getThenStmt().asBlockStmt();
            if (block.getStatements().isEmpty() || !block.getStatement(0).isExpressionStmt()) {
                continue;
            }
            var expression = block.getStatement(0).asExpressionStmt().getExpression();
            if (!(expression instanceof VariableDeclarationExpr declaration) || declaration.getVariables().size() != 1) {
                continue;
            }
            VariableDeclarator variable = declaration.getVariable(0);
            if (variable.getInitializer().orElse(null) instanceof CastExpr cast
                    && cast.getExpression().toString().equals(instanceOf.getExpression().toString())
                    && cast.getType().toString().equals(instanceOf.getType().toString())) {
                add(findings, "JAI-MOD-003", Category.MODERNIZATION, Severity.LOW, path, line(statement), nearestSymbol(statement),
                        "instanceof followed by the same cast can use Java pattern matching.",
                        "Bind the pattern variable in the instanceof expression.", false, 5);
            }
        }
    }

    private void inspectIdeQuality(Path path, CompilationUnit unit, List<Finding> findings) {
        for (IfStmt statement : unit.findAll(IfStmt.class)) {
            if (statement.getElseStmt().isPresent()
                    && terminal(statement.getThenStmt())
                    && !statement.getElseStmt().orElseThrow().isIfStmt()) {
                add(findings, "JAI-IDE-001", Category.CODE_SMELL, Severity.LOW, path, line(statement), nearestSymbol(statement),
                        "Else branch follows a terminal branch and adds avoidable nesting.",
                        "Move the else body after the guard clause.", true, 5);
            }
            if (statement.getElseStmt().isPresent()
                    && normalize(statement.getThenStmt().toString())
                    .equals(normalize(statement.getElseStmt().orElseThrow().toString()))) {
                add(findings, "JAI-IDE-002", Category.BUG_RISK, Severity.HIGH, path, line(statement), nearestSymbol(statement),
                        "Both branches of this condition are identical.",
                        "Remove the ineffective condition and retain one branch.", true, 10);
            }
            Statement thenStatement = statement.getThenStmt();
            if (!statement.getElseStmt().isPresent()
                    && thenStatement.isBlockStmt()
                    && thenStatement.asBlockStmt().getStatements().size() == 1
                    && thenStatement.asBlockStmt().getStatement(0).isIfStmt()
                    && !thenStatement.asBlockStmt().getStatement(0).asIfStmt().getElseStmt().isPresent()) {
                add(findings, "JAI-IDE-003", Category.CODE_SMELL, Severity.LOW, path, line(statement), nearestSymbol(statement),
                        "Nested if statements can be merged into one intention-revealing condition.",
                        "Combine the conditions when short-circuit semantics remain equivalent.", true, 5);
            }
        }

        for (ConditionalExpr conditional : unit.findAll(ConditionalExpr.class)) {
            if (conditional.findAncestor(ConditionalExpr.class).isPresent()
                    || conditional.getThenExpr().isConditionalExpr()
                    || conditional.getElseExpr().isConditionalExpr()) {
                add(findings, "JAI-IDE-004", Category.CODE_SMELL, Severity.MEDIUM, path, line(conditional), nearestSymbol(conditional),
                        "Nested conditional expression obscures control flow.",
                        "Use named decisions or explicit branches.", false, 10);
            }
        }
    }

    private List<DuplicateCandidate> duplicateCandidates(Path path, CompilationUnit unit) {
        List<DuplicateCandidate> candidates = new ArrayList<>();
        for (BlockStmt block : unit.findAll(BlockStmt.class)) {
            CallableDeclaration<?> callable = block.findAncestor(CallableDeclaration.class).orElse(null);
            if (callable == null || block.getStatements().size() < DUPLICATE_STATEMENT_WINDOW) {
                continue;
            }
            List<Statement> statements = block.getStatements();
            for (int start = 0; start <= statements.size() - DUPLICATE_STATEMENT_WINDOW; start++) {
                List<Statement> window = statements.subList(start, start + DUPLICATE_STATEMENT_WINDOW);
                String normalized = normalize(window.stream().map(Node::toString)
                        .reduce((left, right) -> left + "\n" + right).orElse(""));
                if (normalized.length() < MINIMUM_DUPLICATE_CHARACTERS) {
                    continue;
                }
                int firstLine = line(window.get(0));
                int lastLine = endLine(window.get(window.size() - 1));
                candidates.add(new DuplicateCandidate(
                        normalized,
                        path,
                        firstLine,
                        Math.max(1, lastLine - firstLine + 1),
                        callable.getSignature().asString()
                ));
            }
        }
        return List.copyOf(candidates);
    }

    private List<DuplicateBlock> findDuplications(List<DuplicateCandidate> candidates) {
        Map<String, List<DuplicateCandidate>> groups = new LinkedHashMap<>();
        candidates.stream()
                .sorted(DuplicateCandidate.ORDER)
                .forEach(candidate -> groups.computeIfAbsent(candidate.normalized(), ignored -> new ArrayList<>()).add(candidate));
        List<DuplicateBlock> blocks = new ArrayList<>();
        for (List<DuplicateCandidate> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            DuplicateCandidate first = group.get(0);
            for (int index = 1; index < group.size(); index++) {
                DuplicateCandidate second = group.get(index);
                if (first.path().equals(second.path()) && first.symbol().equals(second.symbol())) {
                    continue;
                }
                if (overlapsExisting(blocks, first, second)) {
                    continue;
                }
                blocks.add(new DuplicateBlock(
                        "JAI-DUP-" + String.format(Locale.ROOT, "%04d", blocks.size() + 1),
                        portable(first.path()), first.line(), first.symbol(),
                        portable(second.path()), second.line(), second.symbol(),
                        Math.min(first.lineCount(), second.lineCount()),
                        DUPLICATE_STATEMENT_WINDOW
                ));
                break;
            }
        }
        return blocks.stream().sorted(DuplicateBlock.ORDER).toList();
    }

    private boolean overlapsExisting(
            List<DuplicateBlock> blocks,
            DuplicateCandidate first,
            DuplicateCandidate second
    ) {
        String firstPath = portable(first.path());
        String secondPath = portable(second.path());
        return blocks.stream().anyMatch(existing -> {
            boolean sameDirection = existing.firstPath().equals(firstPath)
                    && existing.firstSymbol().equals(first.symbol())
                    && existing.secondPath().equals(secondPath)
                    && existing.secondSymbol().equals(second.symbol())
                    && overlaps(existing.firstLine(), existing.lineCount(), first.line(), first.lineCount())
                    && overlaps(existing.secondLine(), existing.lineCount(), second.line(), second.lineCount());
            boolean oppositeDirection = existing.firstPath().equals(secondPath)
                    && existing.firstSymbol().equals(second.symbol())
                    && existing.secondPath().equals(firstPath)
                    && existing.secondSymbol().equals(first.symbol())
                    && overlaps(existing.firstLine(), existing.lineCount(), second.line(), second.lineCount())
                    && overlaps(existing.secondLine(), existing.lineCount(), first.line(), first.lineCount());
            return sameDirection || oppositeDirection;
        });
    }

    private boolean overlaps(int firstLine, int firstCount, int secondLine, int secondCount) {
        int firstEnd = firstLine + Math.max(1, firstCount) - 1;
        int secondEnd = secondLine + Math.max(1, secondCount) - 1;
        return firstLine <= secondEnd && secondLine <= firstEnd;
    }

    private QualityMetrics metrics(
            int fileCount,
            int linesOfCode,
            long sourceBytes,
            List<MethodMetric> methods,
            List<Finding> findings,
            int duplicateLines,
            Duration elapsed
    ) {
        Map<Category, Long> byCategory = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            byCategory.put(category, findings.stream().filter(finding -> finding.category() == category).count());
        }
        Map<Severity, Long> bySeverity = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity, findings.stream().filter(finding -> finding.severity() == severity).count());
        }
        int maxCyclomatic = methods.stream().mapToInt(MethodMetric::cyclomaticComplexity).max().orElse(0);
        double averageCyclomatic = methods.stream().mapToInt(MethodMetric::cyclomaticComplexity).average().orElse(0.0d);
        int maxCognitive = methods.stream().mapToInt(MethodMetric::cognitiveComplexity).max().orElse(0);
        int debtMinutes = findings.stream().mapToInt(Finding::estimatedMinutes).sum();
        double debtRatio = linesOfCode == 0 ? 0.0d : 100.0d * debtMinutes / (linesOfCode * 30.0d);
        double maintainabilityScore = clamp(100.0d - debtRatio * 2.0d);
        double duplicationPercent = linesOfCode == 0 ? 0.0d : Math.min(100.0d, 100.0d * duplicateLines / linesOfCode);
        long criticalAndHighBugs = findings.stream()
                .filter(finding -> finding.category() == Category.BUG_RISK)
                .filter(finding -> finding.severity() == Severity.CRITICAL || finding.severity() == Severity.HIGH)
                .count();
        long mediumAndLowBugs = byCategory.get(Category.BUG_RISK) - criticalAndHighBugs;
        double kiloLines = Math.max(1.0d, linesOfCode / 1000.0d);
        double reliabilityScore = clamp(100.0d - (criticalAndHighBugs * 20.0d + mediumAndLowBugs * 5.0d) / kiloLines);
        double complexityScore = clamp(100.0d
                - Math.max(0, maxCyclomatic - CYCLOMATIC_COMPLEXITY_LIMIT) * 3.0d
                - Math.max(0.0d, averageCyclomatic - 5.0d) * 2.0d);
        double duplicationScore = clamp(100.0d - duplicationPercent * 2.0d);
        double qualityScore = clamp(
                maintainabilityScore * 0.40d
                        + reliabilityScore * 0.30d
                        + complexityScore * 0.15d
                        + duplicationScore * 0.15d
        );
        long codeSmells = findings.stream()
                .filter(finding -> finding.category() == Category.CODE_SMELL
                        || finding.category() == Category.COMPLEXITY
                        || finding.category() == Category.DUPLICATION
                        || finding.category() == Category.PERFORMANCE)
                .count();
        return new QualityMetrics(
                fileCount,
                linesOfCode,
                sourceBytes,
                methods.size(),
                findings.size(),
                byCategory.get(Category.BUG_RISK).intValue(),
                codeSmells > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) codeSmells,
                byCategory.get(Category.MODERNIZATION).intValue(),
                bySeverity,
                maxCyclomatic,
                round(averageCyclomatic),
                maxCognitive,
                duplicateLines,
                round(duplicationPercent),
                debtMinutes,
                round(debtRatio),
                round(reliabilityScore),
                round(maintainabilityScore),
                round(complexityScore),
                round(duplicationScore),
                round(qualityScore),
                elapsed.toNanos()
        );
    }

    private int cyclomaticComplexity(CallableDeclaration<?> callable) {
        int decisions = scoped(callable, IfStmt.class).size()
                + scoped(callable, ForStmt.class).size()
                + scoped(callable, ForEachStmt.class).size()
                + scoped(callable, WhileStmt.class).size()
                + scoped(callable, DoStmt.class).size()
                + scoped(callable, CatchClause.class).size()
                + scoped(callable, ConditionalExpr.class).size();
        decisions += scoped(callable, SwitchEntry.class).stream()
                .filter(entry -> !entry.getLabels().isEmpty())
                .mapToInt(entry -> 1)
                .sum();
        decisions += scoped(callable, BinaryExpr.class).stream()
                .filter(binary -> binary.getOperator() == BinaryExpr.Operator.AND
                        || binary.getOperator() == BinaryExpr.Operator.OR)
                .mapToInt(ignored -> 1)
                .sum();
        return 1 + decisions;
    }

    private int cognitiveComplexity(CallableDeclaration<?> callable) {
        List<Node> controls = new ArrayList<>();
        controls.addAll(scoped(callable, IfStmt.class));
        controls.addAll(scoped(callable, ForStmt.class));
        controls.addAll(scoped(callable, ForEachStmt.class));
        controls.addAll(scoped(callable, WhileStmt.class));
        controls.addAll(scoped(callable, DoStmt.class));
        controls.addAll(scoped(callable, CatchClause.class));
        controls.addAll(scoped(callable, SwitchEntry.class).stream().filter(entry -> !entry.getLabels().isEmpty()).toList());
        controls.addAll(scoped(callable, ConditionalExpr.class));
        int score = controls.stream().mapToInt(control -> 1 + controlDepth(control, callable)).sum();
        score += scoped(callable, BinaryExpr.class).stream()
                .filter(binary -> binary.getOperator() == BinaryExpr.Operator.AND
                        || binary.getOperator() == BinaryExpr.Operator.OR)
                .mapToInt(ignored -> 1)
                .sum();
        return score;
    }

    private int maximumNesting(CallableDeclaration<?> callable) {
        List<Node> controls = new ArrayList<>();
        controls.addAll(scoped(callable, IfStmt.class));
        controls.addAll(scoped(callable, ForStmt.class));
        controls.addAll(scoped(callable, ForEachStmt.class));
        controls.addAll(scoped(callable, WhileStmt.class));
        controls.addAll(scoped(callable, DoStmt.class));
        controls.addAll(scoped(callable, TryStmt.class));
        return controls.stream().mapToInt(control -> 1 + controlDepth(control, callable)).max().orElse(0);
    }

    private int controlDepth(Node node, CallableDeclaration<?> callable) {
        int depth = 0;
        Node current = node.getParentNode().orElse(null);
        while (current != null && current != callable) {
            if (current instanceof IfStmt
                    || current instanceof ForStmt
                    || current instanceof ForEachStmt
                    || current instanceof WhileStmt
                    || current instanceof DoStmt
                    || current instanceof TryStmt
                    || current instanceof CatchClause
                    || current instanceof SwitchEntry
                    || current instanceof ConditionalExpr) {
                depth++;
            }
            current = current.getParentNode().orElse(null);
        }
        return depth;
    }

    private <T extends Node> List<T> scoped(CallableDeclaration<?> callable, Class<T> type) {
        return callable.findAll(type).stream().filter(node -> belongsTo(node, callable)).toList();
    }

    private boolean belongsTo(Node node, CallableDeclaration<?> callable) {
        return node.findAncestor(CallableDeclaration.class).map(ancestor -> ancestor == callable).orElse(false);
    }

    private boolean terminal(Statement statement) {
        if (statement instanceof ReturnStmt || statement instanceof ThrowStmt
                || statement instanceof BreakStmt || statement instanceof ContinueStmt) {
            return true;
        }
        if (statement.isBlockStmt()) {
            BlockStmt block = statement.asBlockStmt();
            return !block.getStatements().isEmpty() && terminal(block.getStatement(block.getStatements().size() - 1));
        }
        if (statement.isIfStmt()) {
            IfStmt conditional = statement.asIfStmt();
            return conditional.getElseStmt().isPresent()
                    && terminal(conditional.getThenStmt())
                    && terminal(conditional.getElseStmt().orElseThrow());
        }
        return false;
    }

    private String nearestSymbol(Node node) {
        return node.findAncestor(CallableDeclaration.class)
                .map(callable -> callable.getSignature().asString())
                .orElseGet(() -> node.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                        .map(type -> type.getNameAsString()).orElse("<type>"));
    }

    private int lineSpan(Node node) {
        return Math.max(1, endLine(node) - line(node) + 1);
    }

    private int line(Node node) {
        return node.getBegin().map(position -> position.line).orElse(1);
    }

    private int endLine(Node node) {
        return node.getEnd().map(position -> position.line).orElse(line(node));
    }

    private String normalize(String source) {
        String withoutComments = COMMENT_PATTERN.matcher(source).replaceAll("");
        String withoutStrings = STRING_PATTERN.matcher(withoutComments).replaceAll("\"S\"");
        String withoutCharacters = CHARACTER_PATTERN.matcher(withoutStrings).replaceAll("'C'");
        String withoutNumbers = NUMBER_PATTERN.matcher(withoutCharacters).replaceAll("N");
        return WHITESPACE_PATTERN.matcher(withoutNumbers).replaceAll("");
    }

    private int countCodeLines(String source) {
        boolean blockComment = false;
        int count = 0;
        for (String rawLine : source.lines().toList()) {
            String line = rawLine.strip();
            if (blockComment) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                blockComment = false;
                line = line.substring(end + 2).strip();
            }
            while (line.startsWith("/*")) {
                int end = line.indexOf("*/", 2);
                if (end < 0) {
                    blockComment = true;
                    line = "";
                    break;
                }
                line = line.substring(end + 2).strip();
            }
            if (!line.isBlank() && !line.startsWith("//")) {
                count++;
            }
        }
        return count;
    }

    private void add(
            List<Finding> findings,
            String id,
            Category category,
            Severity severity,
            Path path,
            int line,
            String symbol,
            String message,
            String remediation,
            boolean quickFixAvailable,
            int estimatedMinutes
    ) {
        findings.add(new Finding(
                id, category, severity, portable(path), line, symbol, message, remediation, quickFixAvailable, estimatedMinutes
        ));
    }

    private String portable(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private double clamp(double value) {
        return Math.max(0.0d, Math.min(100.0d, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    public enum Category {
        BUG_RISK,
        CODE_SMELL,
        MODERNIZATION,
        COMPLEXITY,
        DUPLICATION,
        PERFORMANCE
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    public record Finding(
            String id,
            Category category,
            Severity severity,
            String relativePath,
            int line,
            String symbol,
            String message,
            String remediation,
            boolean quickFixAvailable,
            int estimatedMinutes
    ) {
        private static final Comparator<Finding> ORDER = Comparator
                .comparing(Finding::relativePath)
                .thenComparingInt(Finding::line)
                .thenComparing(Finding::id)
                .thenComparing(Finding::symbol);
    }

    public record MethodMetric(
            String relativePath,
            int line,
            String symbol,
            int lines,
            int statements,
            int cyclomaticComplexity,
            int cognitiveComplexity,
            int maximumNesting
    ) {
        private static final Comparator<MethodMetric> ORDER = Comparator
                .comparing(MethodMetric::relativePath)
                .thenComparingInt(MethodMetric::line)
                .thenComparing(MethodMetric::symbol);
    }

    public record DuplicateBlock(
            String id,
            String firstPath,
            int firstLine,
            String firstSymbol,
            String secondPath,
            int secondLine,
            String secondSymbol,
            int lineCount,
            int statementCount
    ) {
        private static final Comparator<DuplicateBlock> ORDER = Comparator
                .comparing(DuplicateBlock::firstPath)
                .thenComparingInt(DuplicateBlock::firstLine)
                .thenComparing(DuplicateBlock::secondPath)
                .thenComparingInt(DuplicateBlock::secondLine);
    }

    public record ParseFailure(String relativePath, String message) {
    }

    public record QualityMetrics(
            int fileCount,
            int linesOfCode,
            long sourceBytes,
            int methodCount,
            int findingCount,
            int bugRiskCount,
            int codeSmellCount,
            int modernizationOpportunityCount,
            Map<Severity, Long> findingsBySeverity,
            int maximumCyclomaticComplexity,
            double averageCyclomaticComplexity,
            int maximumCognitiveComplexity,
            int duplicatedLineCount,
            double duplicationPercent,
            int remediationDebtMinutes,
            double remediationDebtRatioPercent,
            double reliabilityScore,
            double maintainabilityScore,
            double complexityScore,
            double duplicationScore,
            double qualityScore,
            long analysisElapsedNanos
    ) {
    }

    public record QualityReport(
            List<Finding> findings,
            List<MethodMetric> methods,
            List<DuplicateBlock> duplications,
            List<ParseFailure> parseFailures,
            QualityMetrics metrics
    ) {
    }

    private record DuplicateCandidate(
            String normalized,
            Path path,
            int line,
            int lineCount,
            String symbol
    ) {
        private static final Comparator<DuplicateCandidate> ORDER = Comparator
                .comparing(DuplicateCandidate::path)
                .thenComparingInt(DuplicateCandidate::line)
                .thenComparing(DuplicateCandidate::symbol);
    }

    private record FileAnalysis(
            List<Finding> findings,
            List<MethodMetric> methods,
            List<DuplicateCandidate> duplicateCandidates,
            List<ParseFailure> parseFailures,
            int linesOfCode,
            long sourceBytes
    ) {
    }
}
