package jaipilot

import (
	"context"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"testing"
)

func TestCoverageExactCountersScopesAndIndependentRules(t *testing.T) {
	root := t.TempDir()
	report := "coverage.xml"
	write(t, root, report, `<report name="module"><package name="com/acme"><class name="com/acme/Order"><counter type="LINE" missed="1" covered="4"/><counter type="BRANCH" missed="2" covered="2"/></class><counter type="LINE" missed="1" covered="4"/></package><counter type="LINE" missed="2" covered="8"/><counter type="BRANCH" missed="3" covered="7"/></report>`)
	p := &CoveragePolicy{Reports: []string{report}, Targets: []CoverageTarget{{Counter: "LINE", MinimumPercent: 80, Element: "BUNDLE", Report: report}, {Counter: "BRANCH", MinimumPercent: 70, Element: "BUNDLE", Report: report}, {Counter: "LINE", MinimumPercent: 80, Element: "CLASS", Report: report, Includes: []string{"com.acme.*"}}}}
	measurements, passed, err := measureCoverage(root, p)
	if err != nil || !passed || len(measurements) != 3 {
		t.Fatalf("%v %v %v", measurements, passed, err)
	}
	p.Targets[0].MinimumPercent = 80.0001
	if _, passed, err := measureCoverage(root, p); err != nil || passed {
		t.Fatal("Rounded threshold falsely passed", err)
	}
	p.Targets[0].MinimumPercent = 70
	p.Targets[1].MinimumPercent = 75
	if _, passed, err := measureCoverage(root, p); err != nil || passed {
		t.Fatal("Line coverage masked failed branch rule", err)
	}
	p.Targets[2].Includes = []string{"missing.*"}
	if _, _, err := measureCoverage(root, p); err == nil {
		t.Fatal("Empty scope falsely passed")
	}
	p.Targets = p.Targets[:1]
	write(t, root, report, `<report><counter type="LINE" missed="0" covered="0"/></report>`)
	if _, _, err := measureCoverage(root, p); err == nil {
		t.Fatal("No execution falsely passed")
	}
}

func TestCoverageInputsAndLegacyParsing(t *testing.T) {
	for _, raw := range []string{"-1", "101", "NaN", "Inf", "", "0.8%"} {
		if _, err := percentage(raw); err == nil {
			t.Fatal("Invalid percentage accepted", raw)
		}
	}
	for _, raw := range []string{"0", "80", "80.5", "100"} {
		if _, err := percentage(raw); err != nil {
			t.Fatal(err)
		}
	}
	old, err := parseRun([]string{"--class", "Example", "--json"})
	if err != nil || old.coverageTarget != nil || old.coveragePolicy != "" || !old.json {
		t.Fatal("Legacy options changed", err)
	}
	new, err := parseRun([]string{"--all", "--coverage-target", "80", "--branch-coverage-target", "70"})
	if err != nil || *new.coverageTarget != 80 || *new.branchTarget != 70 {
		t.Fatal(err)
	}
}

func TestCoveragePolicyIsOptInOnEveryServiceTurn(t *testing.T) {
	testSession(t)
	root, _, _ := fixture(t)
	scope, _ := resolveScope(context.Background(), root, ScopeInput{All: true})
	policy := object{"version": 1, "provider": "jacoco", "targets": []any{object{"counter": "LINE", "minimumPercent": 80}}}
	posts := 0
	setClient(t, func(r *http.Request) (*http.Response, error) {
		if r.Method == "GET" {
			return response(200, `{"protocolVersion":6,"coveragePolicyVersions":[1],"workflows":[{"id":"improve_coverage"}]}`), nil
		}
		posts++
		var body object
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatal(err)
		}
		if jsonText(obj(body["context"])["coveragePolicy"]) != jsonText(policy) {
			t.Fatal("Coverage policy lost", body)
		}
		return response(200, `{"content":[{"type":"tool_use","id":"done","name":"finish","input":{"status":"complete"}}]}`), nil
	})
	if _, err := runWorkflow(context.Background(), root, "improve_coverage", scope, workflowOptions{CoveragePolicy: policy}); err != nil || posts != 1 {
		t.Fatal(err, posts)
	}
}

func TestStaleCoverageReportCannotPassSkippedReportCommand(t *testing.T) {
	root, _, _ := fixture(t)
	write(t, root, "target/coverage.xml", `<report><counter type="LINE" missed="0" covered="10"/></report>`)
	p := &CoveragePolicy{Reports: []string{"target/coverage.xml"}, TestCommand: "echo tests", ReportCommand: "echo skipped", TimeoutSeconds: 10, Targets: []CoverageTarget{{Counter: "LINE", Element: "BUNDLE", Report: "target/coverage.xml", MinimumPercent: 80}}}
	if _, _, _, err := freshCoverage(context.Background(), root, p); err == nil {
		t.Fatal("Stale report passed")
	}
	if _, err := os.Stat(filepath.Join(root, "target/coverage.xml")); !os.IsNotExist(err) {
		t.Fatal("Stale report retained")
	}
}

func TestCoverageEvidenceNeverDeletesTrackedFiles(t *testing.T) {
	root, _, _ := fixture(t)
	write(t, root, "tracked.xml", `<report><counter type="LINE" missed="0" covered="10"/></report>`)
	if _, err := git(context.Background(), root, "add", "tracked.xml"); err != nil {
		t.Fatal(err)
	}
	p := &CoveragePolicy{Reports: []string{"tracked.xml"}, TestCommand: "echo tests", ReportCommand: "echo report", TimeoutSeconds: 10}
	if _, _, _, err := freshCoverage(context.Background(), root, p); err == nil {
		t.Fatal("Tracked evidence was deleted")
	}
	if _, err := os.Stat(filepath.Join(root, "tracked.xml")); err != nil {
		t.Fatal(err)
	}
}
func TestCoverageRejectsNonJacocoXML(t *testing.T) {
	root := t.TempDir()
	write(t, root, "coverage.xml", `<fake><counter type="LINE" missed="0" covered="10"/></fake>`)
	p := &CoveragePolicy{Reports: []string{"coverage.xml"}, Targets: []CoverageTarget{{Counter: "LINE", Element: "BUNDLE", Report: "coverage.xml", MinimumPercent: 80}}}
	if _, _, err := measureCoverage(root, p); err == nil {
		t.Fatal("Non-JaCoCo XML was accepted")
	}
}
func TestCoverageSelectedClassUsesExactPackageIncludingDefaultPackage(t *testing.T) {
	root := t.TempDir()
	write(t, root, "src/main/java/a/Order.java", "package a;\nclass Order {}")
	write(t, root, "src/main/java/Plain.java", "class Plain {}")
	p := &CoveragePolicy{Reports: []string{"coverage.xml"}}
	if err := addCoverageTarget(root, p, "LINE", 80, Scope{SelectedPaths: []string{"src/main/java/a/Order.java", "src/main/java/Plain.java"}}); err != nil {
		t.Fatal(err)
	}
	if got := jsonText(p.Targets[0].Includes); got != `["a/Order","Plain"]` {
		t.Fatal(got)
	}
}
func TestCoverageUnsupportedServiceRejectsBeforeModelRequest(t *testing.T) {
	testSession(t)
	root, _, _ := fixture(t)
	scope, _ := resolveScope(context.Background(), root, ScopeInput{All: true})
	setClient(t, func(r *http.Request) (*http.Response, error) {
		if r.Method != "GET" {
			t.Fatal("Target sent to an unsupported service")
		}
		return response(200, `{"protocolVersion":6,"workflows":[{"id":"improve_coverage"}]}`), nil
	})
	if _, err := runWorkflow(context.Background(), root, "improve_coverage", scope, workflowOptions{CoveragePolicy: object{"version": 1}}); err == nil {
		t.Fatal("Unsupported policy ignored")
	}
}

func TestCoverageReportHelper(t *testing.T) {
	if os.Getenv("JAIPILOT_TEST_COVERAGE_WRITER") != "1" {
		return
	}
	root, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	write(t, root, "target/coverage.xml", `<report><counter type="LINE" missed="9" covered="1"/></report>`)
}
func TestCoverageModelCompletionCannotOverrideLocalCounters(t *testing.T) {
	testSession(t)
	t.Setenv("JAIPILOT_TEST_COVERAGE_WRITER", "1")
	root, _, _ := fixture(t)
	mustGit(t, root, "add", ".")
	mustGit(t, root, "-c", "user.name=JAIPilot integration", "-c", "user.email=integration@example.invalid", "commit", "-m", "fixture")
	scope, _ := resolveScope(context.Background(), root, ScopeInput{All: true})
	binary, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	p := &CoveragePolicy{Version: 1, Provider: "jacoco", Reports: []string{"target/coverage.xml"}, TestRoots: []string{"src/test"}, TestCommand: "echo tests", ReportCommand: shellQuote(binary) + " -test.run=^TestCoverageReportHelper$", MaxIterations: 1, TimeoutSeconds: 30, Targets: []CoverageTarget{{Counter: "LINE", Element: "BUNDLE", Report: "target/coverage.xml", MinimumPercent: 80}}}
	setClient(t, func(r *http.Request) (*http.Response, error) {
		if r.Method == "GET" {
			return response(200, `{"protocolVersion":6,"coveragePolicyVersions":[1],"workflows":[{"id":"improve_coverage"}]}`), nil
		}
		return response(200, `{"content":[{"type":"tool_use","id":"done","name":"finish","input":{"status":"complete","summary":"100% coverage"}}]}`), nil
	})
	result, err := runCoverage(context.Background(), root, "improve_coverage", scope, p, false)
	if err != nil || result["status"] != "blocked" {
		t.Fatal("Model prose overrode local evidence", result, err)
	}
	m := result["coverageAfter"].([]CoverageMeasurement)
	if len(m) != 1 || m[0].ActualPercent != 10 || m[0].Passed {
		t.Fatal(m)
	}
}
