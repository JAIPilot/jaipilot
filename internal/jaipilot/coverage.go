package jaipilot

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"math"
	"math/big"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// CoveragePolicy is produced from the evaluated build, never from model prose.
type CoveragePolicy struct {
	Version        int              `json:"version"`
	Provider       string           `json:"provider"`
	Targets        []CoverageTarget `json:"targets"`
	Reports        []string         `json:"reports"`
	ExecutionData  []string         `json:"executionData,omitempty"`
	ScopePaths     []string         `json:"scopePaths,omitempty"`
	TestRoots      []string         `json:"testRoots"`
	TestCommand    string           `json:"testCommand"`
	ReportCommand  string           `json:"reportCommand"`
	CheckCommand   string           `json:"checkCommand,omitempty"`
	MaxIterations  int              `json:"maxIterations,omitempty"`
	TimeoutSeconds int              `json:"timeoutSeconds,omitempty"`
}

type CoverageTarget struct {
	Counter        string   `json:"counter"`
	MinimumPercent float64  `json:"minimumPercent"`
	Element        string   `json:"element"`
	Report         string   `json:"report"`
	Includes       []string `json:"includes,omitempty"`
	Excludes       []string `json:"excludes,omitempty"`
}

type coverageCounter struct {
	Type    string `xml:"type,attr"`
	Missed  int64  `xml:"missed,attr"`
	Covered int64  `xml:"covered,attr"`
}
type coverageNode struct {
	XMLName  xml.Name
	Name     string            `xml:"name,attr"`
	Counters []coverageCounter `xml:"counter"`
	Packages []coverageNode    `xml:"package"`
	Classes  []coverageNode    `xml:"class"`
}
type CoverageMeasurement struct {
	Counter        string  `json:"counter"`
	Element        string  `json:"element"`
	Name           string  `json:"name"`
	Report         string  `json:"report"`
	MinimumPercent float64 `json:"minimumPercent"`
	Covered        int64   `json:"covered"`
	Missed         int64   `json:"missed"`
	ActualPercent  float64 `json:"actualPercent"`
	Passed         bool    `json:"passed"`
}

func percentage(raw string) (float64, error) {
	n, err := strconv.ParseFloat(raw, 64)
	if err != nil || math.IsNaN(n) || math.IsInf(n, 0) || n < 0 || n > 100 {
		return 0, errors.New("Coverage target must be a percentage between 0 and 100")
	}
	return n, nil
}

func loadCoveragePolicy(root, path string) (*CoveragePolicy, error) {
	f, err := os.Open(absolute(root, path))
	if err != nil {
		return nil, err
	}
	defer f.Close()
	d := json.NewDecoder(io.LimitReader(f, 1<<20))
	d.DisallowUnknownFields()
	var p CoveragePolicy
	if err := d.Decode(&p); err != nil {
		return nil, fmt.Errorf("Invalid coverage policy: %w", err)
	}
	var extra any
	if d.Decode(&extra) != io.EOF {
		return nil, errors.New("Invalid trailing coverage policy data")
	}
	if err := p.validate(root); err != nil {
		return nil, err
	}
	return &p, nil
}

func (p *CoveragePolicy) validate(root string) error {
	if p.Version != 1 || p.Provider != "jacoco" || len(p.Targets) == 0 || len(p.Targets) > 1000 || len(p.Reports) == 0 || len(p.TestRoots) == 0 {
		return errors.New("Coverage requires a version 1 JaCoCo policy, report paths, test roots, and explicit or configured targets")
	}
	if p.MaxIterations == 0 {
		p.MaxIterations = 3
	}
	if p.TimeoutSeconds == 0 {
		p.TimeoutSeconds = 1200
	}
	if p.MaxIterations < 1 || p.MaxIterations > 10 || p.TimeoutSeconds < 1 || p.TimeoutSeconds > 7200 {
		return errors.New("Invalid coverage execution budget")
	}
	if strings.TrimSpace(p.TestCommand) == "" || strings.TrimSpace(p.ReportCommand) == "" {
		return errors.New("Coverage requires test and report commands")
	}
	roots, err := testPaths(root, p.TestRoots)
	if err != nil {
		return err
	}
	p.TestRoots = roots
	for _, path := range append(append([]string{}, p.Reports...), p.ExecutionData...) {
		if _, err := testPaths(root, []string{path}); err != nil {
			return err
		}
	}
	for _, path := range p.Reports {
		if !strings.HasSuffix(path, ".xml") {
			return errors.New("Coverage evidence must be JaCoCo XML")
		}
	}
	for _, t := range p.Targets {
		if t.Counter != "LINE" && t.Counter != "BRANCH" && t.Counter != "INSTRUCTION" {
			return errors.New("Unsupported coverage counter; use LINE, BRANCH, or INSTRUCTION")
		}
		if t.Element != "BUNDLE" && t.Element != "PACKAGE" && t.Element != "CLASS" {
			return errors.New("Unsupported coverage scope; use BUNDLE, PACKAGE, or CLASS")
		}
		if _, err := percentage(strconv.FormatFloat(t.MinimumPercent, 'f', -1, 64)); err != nil {
			return err
		}
		found := false
		for _, r := range p.Reports {
			found = found || r == t.Report
		}
		if !found {
			return errors.New("Coverage target references an undeclared report")
		}
	}
	return nil
}

func coverageMatches(name string, includes, excludes []string) bool {
	match := func(pattern string) bool {
		r := regexp.QuoteMeta(strings.ReplaceAll(pattern, ".", "/"))
		r = strings.ReplaceAll(strings.ReplaceAll(r, `\*`, ".*"), `\?`, ".")
		return regexp.MustCompile("^" + r + "$").MatchString(strings.ReplaceAll(name, ".", "/"))
	}
	included := len(includes) == 0
	for _, x := range includes {
		included = included || match(x)
	}
	for _, x := range excludes {
		if match(x) {
			return false
		}
	}
	return included
}

func measureCoverage(root string, p *CoveragePolicy) ([]CoverageMeasurement, bool, error) {
	reports := map[string]coverageNode{}
	for _, path := range p.Reports {
		f, err := os.Open(absolute(root, path))
		if err != nil {
			return nil, false, fmt.Errorf("Fresh coverage report missing: %s", path)
		}
		var report coverageNode
		d := xml.NewDecoder(io.LimitReader(f, 64<<20))
		err = d.Decode(&report)
		f.Close()
		if err == nil && report.XMLName.Local != "report" {
			err = errors.New("expected JaCoCo report root")
		}
		if err != nil {
			return nil, false, fmt.Errorf("Invalid JaCoCo report %s: %w", path, err)
		}
		reports[path] = report
	}
	measurements := []CoverageMeasurement{}
	passed := true
	for _, t := range p.Targets {
		r := reports[t.Report]
		nodes := []coverageNode{r}
		if t.Element == "PACKAGE" {
			nodes = r.Packages
		}
		if t.Element == "CLASS" {
			nodes = nil
			for _, pkg := range r.Packages {
				nodes = append(nodes, pkg.Classes...)
			}
		}
		matched := 0
		for _, n := range nodes {
			if !coverageMatches(n.Name, t.Includes, t.Excludes) {
				continue
			}
			matched++
			var counter *coverageCounter
			for i := range n.Counters {
				if n.Counters[i].Type == t.Counter {
					counter = &n.Counters[i]
					break
				}
			}
			if counter == nil || counter.Missed < 0 || counter.Covered < 0 || counter.Covered > math.MaxInt64-counter.Missed || counter.Covered+counter.Missed == 0 {
				return measurements, false, fmt.Errorf("No measurable %s coverage for %s (%s)", t.Counter, n.Name, t.Report)
			}
			total := counter.Covered + counter.Missed
			actual := new(big.Rat).SetFrac(big.NewInt(counter.Covered), big.NewInt(total))
			actual.Mul(actual, big.NewRat(100, 1))
			minimum, _ := new(big.Rat).SetString(strconv.FormatFloat(t.MinimumPercent, 'f', -1, 64))
			ok := actual.Cmp(minimum) >= 0
			value, _ := actual.Float64()
			passed = passed && ok
			measurements = append(measurements, CoverageMeasurement{t.Counter, t.Element, n.Name, t.Report, t.MinimumPercent, counter.Covered, counter.Missed, value, ok})
		}
		if matched == 0 {
			return measurements, false, fmt.Errorf("Coverage rule matched no %s elements in %s", t.Element, t.Report)
		}
	}
	return measurements, passed, nil
}

func freshCoverage(ctx context.Context, root string, p *CoveragePolicy) ([]CoverageMeasurement, bool, []CommandResult, error) {
	commands := []CommandResult{}
	// Removing old reports prevents a skipped report task from passing on stale XML.
	for _, path := range append(append([]string{}, p.Reports...), p.ExecutionData...) {
		if _, err := testPaths(root, []string{path}); err != nil {
			return nil, false, commands, err
		}
		tracked, err := git(ctx, root, "ls-files", "--", path)
		if err != nil {
			return nil, false, commands, err
		}
		if tracked != "" {
			return nil, false, commands, fmt.Errorf("Refusing to remove tracked coverage evidence: %s", path)
		}
	}
	for _, path := range append(append([]string{}, p.Reports...), p.ExecutionData...) {
		if err := os.Remove(absolute(root, path)); err != nil && !errors.Is(err, os.ErrNotExist) {
			return nil, false, commands, err
		}
	}
	for _, command := range []string{p.TestCommand, p.ReportCommand} {
		r, err := runCommand(ctx, root, object{"command": command, "timeoutSeconds": p.TimeoutSeconds, "purpose": "coverage verification"})
		commands = append(commands, r)
		if err != nil {
			return nil, false, commands, err
		}
		if r.ExitCode != 0 || r.TimedOut {
			return nil, false, commands, fmt.Errorf("Coverage verification command failed (exit %d): %s", r.ExitCode, command)
		}
	}
	measurements, passed, err := measureCoverage(root, p)
	return measurements, passed, commands, err
}

func runCoverage(ctx context.Context, root, workflow string, scope Scope, p *CoveragePolicy, checkOnly bool) (object, error) {
	ctx, cancel := context.WithTimeout(ctx, time.Duration(p.TimeoutSeconds)*time.Second)
	defer cancel()
	jobs := newJobs(ctx, root)
	release, err := jobs.acquire()
	if err != nil {
		return nil, err
	}
	defer release()
	beforeFiles, err := snapshot(ctx, root)
	if err != nil {
		return nil, err
	}
	baseline, passed, commands, err := freshCoverage(ctx, root, p)
	result := object{"workflow": workflow, "coverageBefore": baseline, "coverageAfter": baseline, "commands": commands, "status": "blocked"}
	if err != nil {
		result["blocker"] = err.Error()
		return result, nil
	}
	baselineFiles, err := snapshot(ctx, root)
	if err != nil {
		return result, err
	}
	if paths := changed(beforeFiles, baselineFiles); len(paths) > 0 {
		return result, fmt.Errorf("Coverage baseline changed repository files: %s", strings.Join(paths, ", "))
	}
	if !passed && !checkOnly {
		status, err := git(ctx, root, "status", "--porcelain")
		if err != nil {
			return nil, err
		}
		if status != "" {
			return nil, errors.New("Coverage generation requires a clean Git working tree; commit or isolate local changes first")
		}
	}
	for i := 0; !passed && !checkOnly && i < p.MaxIterations; i++ {
		policy := object{"version": p.Version, "provider": p.Provider, "targets": p.Targets}
		iterationCtx, stop := context.WithCancel(ctx)
		var protectedErr error
		execute := func(ctx context.Context, root string, input object) (CommandResult, error) {
			r, err := runCommand(ctx, root, input)
			if err != nil {
				return r, err
			}
			files, err := snapshot(ctx, root)
			if err != nil {
				protectedErr = err
				stop()
				return CommandResult{}, err
			}
			for _, path := range changed(beforeFiles, files) {
				if !under(path, p.TestRoots) {
					protectedErr = fmt.Errorf("Coverage workflow changed protected file: %s", path)
					stop()
					return CommandResult{}, protectedErr
				}
			}
			return r, nil
		}
		out, runErr := runWorkflow(iterationCtx, root, workflow, scope, workflowOptions{CoveragePolicy: policy, Execute: execute, UserRequest: "Meet the supplied coverage policy without changing production code or build configuration. Fresh locally measured gaps: " + jsonText(result["coverageAfter"])})
		stop()
		if protectedErr != nil {
			return result, protectedErr
		}
		if runErr != nil {
			return result, runErr
		}
		result["agentResult"] = out
		afterFiles, err := snapshot(ctx, root)
		if err != nil {
			return result, err
		}
		for _, path := range changed(beforeFiles, afterFiles) {
			if !under(path, p.TestRoots) {
				return result, fmt.Errorf("Coverage workflow changed protected file: %s", path)
			}
		}
		var measured []CoverageMeasurement
		var runs []CommandResult
		measured, passed, runs, err = freshCoverage(ctx, root, p)
		commands = append(commands, runs...)
		result["coverageAfter"] = measured
		result["commands"] = commands
		if err != nil {
			result["blocker"] = err.Error()
			return result, nil
		}
	}
	if passed && p.CheckCommand != "" {
		r, err := runCommand(ctx, root, object{"command": p.CheckCommand, "timeoutSeconds": p.TimeoutSeconds, "purpose": "project coverage gates"})
		commands = append(commands, r)
		result["commands"] = commands
		if err != nil {
			return result, err
		}
		if r.ExitCode != 0 || r.TimedOut {
			passed = false
			result["blocker"] = "Project coverage verification failed"
		}
	}
	afterFiles, err := snapshot(ctx, root)
	if err != nil {
		return result, err
	}
	for _, path := range changed(beforeFiles, afterFiles) {
		if checkOnly || !under(path, p.TestRoots) {
			return result, fmt.Errorf("Coverage verification changed protected file: %s", path)
		}
	}
	if passed {
		result["status"] = "complete"
	} else if result["blocker"] == nil {
		result["blocker"] = "Coverage requirements remain unmet"
	}
	result["coveragePolicy"] = p
	return result, nil
}

func addCoverageTarget(root string, p *CoveragePolicy, counter string, minimum float64, scope Scope) error {
	for _, report := range p.Reports {
		t := CoverageTarget{Counter: counter, MinimumPercent: minimum, Element: "BUNDLE", Report: report}
		if !scope.ProjectWide {
			t.Element = "CLASS"
			for _, path := range scope.SelectedPaths {
				if strings.HasSuffix(path, ".java") {
					source, err := os.ReadFile(absolute(root, path))
					if err != nil {
						return err
					}
					class := strings.TrimSuffix(filepath.Base(path), ".java")
					pkg := regexp.MustCompile(`(?m)^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;`).FindSubmatch(source)
					if len(pkg) > 1 {
						class = strings.ReplaceAll(string(pkg[1]), ".", "/") + "/" + class
					}
					t.Includes = append(t.Includes, class)
				}
			}
			if len(t.Includes) == 0 {
				t.Element = "BUNDLE"
			}
		}
		p.Targets = append(p.Targets, t)
	}
	return nil
}

func coverageText(result object) string {
	var out strings.Builder
	if measurements, ok := result["coverageAfter"].([]CoverageMeasurement); ok {
		for _, m := range measurements {
			state := "FAIL"
			if m.Passed {
				state = "PASS"
			}
			fmt.Fprintf(&out, "%s %s %s %s: %.2f%% (minimum %g%%; %d covered, %d missed)\n", state, m.Counter, m.Element, m.Name, m.ActualPercent, m.MinimumPercent, m.Covered, m.Missed)
		}
	}
	if result["status"] == "complete" {
		out.WriteString("Coverage requirements passed.")
	} else {
		out.WriteString("Coverage blocked: " + str(result["blocker"]))
		if commands, ok := result["commands"].([]CommandResult); ok {
			for _, c := range commands {
				if c.ExitCode != 0 || c.TimedOut {
					output := c.Output
					if len(output) > 8000 {
						output = output[len(output)-8000:]
					}
					out.WriteString("\n" + c.Command + "\n" + output)
				}
			}
		}
	}
	return out.String()
}
