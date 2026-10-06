package jaipilot

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
)

type Selection struct {
	Path      string `json:"path"`
	StartLine int    `json:"startLine"`
	EndLine   int    `json:"endLine"`
}
type Scope struct {
	SelectedPaths []string    `json:"selectedPaths"`
	Selections    []Selection `json:"selections"`
	ProjectWide   bool        `json:"projectWide"`
}
type ScopeInput struct {
	All        bool     `json:"all,omitempty"`
	Paths      []string `json:"paths,omitempty"`
	Classes    []string `json:"classes,omitempty"`
	Selections []string `json:"selections,omitempty"`
}

func (in *ScopeInput) UnmarshalJSON(data []byte) error {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}
	if len(fields) != 1 {
		return fmt.Errorf("Choose exactly one scope")
	}
	for key, value := range fields {
		switch key {
		case "all":
			if string(value) != "true" {
				return fmt.Errorf("all must be true")
			}
			in.All = true
		case "paths", "classes", "selections":
			var items []string
			if err := json.Unmarshal(value, &items); err != nil {
				return err
			}
			if len(items) == 0 {
				return fmt.Errorf("%s cannot be empty", key)
			}
			for _, item := range items {
				if strings.TrimSpace(item) == "" {
					return fmt.Errorf("%s cannot contain empty values", key)
				}
			}
			switch key {
			case "paths":
				in.Paths = items
			case "classes":
				in.Classes = items
			case "selections":
				in.Selections = items
			}
		default:
			return fmt.Errorf("Unknown scope field: %s", key)
		}
	}
	return nil
}

func git(ctx context.Context, root string, args ...string) (string, error) {
	name := "git"
	if runtime.GOOS == "windows" {
		name = "git.exe"
	}
	cmd := exec.CommandContext(ctx, name, append([]string{"-C", root}, args...)...)
	out, err := cmd.Output()
	if err != nil {
		if e, ok := err.(*exec.ExitError); ok && len(e.Stderr) > 0 {
			return "", fmt.Errorf("%s", strings.TrimSpace(string(e.Stderr)))
		}
		return "", fmt.Errorf("Git command failed: %w", err)
	}
	for _, arg := range args {
		if arg == "-z" {
			return string(out), nil
		}
	}
	return strings.TrimSpace(string(out)), nil
}
func repositoryRoot(ctx context.Context, path string) (string, error) {
	path, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	root, err := git(ctx, path, "rev-parse", "--show-toplevel")
	if err != nil {
		return "", err
	}
	return real(root)
}
func javaFiles(ctx context.Context, root string) ([]string, error) {
	out, err := git(ctx, root, "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.java")
	if err != nil {
		return nil, err
	}
	files := []string{}
	for _, p := range strings.Split(out, "\x00") {
		if p != "" {
			files = append(files, filepath.ToSlash(p))
		}
	}
	return unique(files), nil
}
func checkedPath(root, raw string, file bool) (string, error) {
	if raw == "" || strings.ContainsRune(raw, 0) {
		return "", fmt.Errorf("Invalid scope path: %s", raw)
	}
	root, err := real(root)
	if err != nil {
		return "", err
	}
	path, err := real(absolute(root, raw))
	if err != nil {
		return "", err
	}
	if !within(root, path) {
		return "", fmt.Errorf("Scope path leaves the repository: %s", raw)
	}
	info, err := os.Stat(path)
	if err != nil {
		return "", err
	}
	if (file && !info.Mode().IsRegular()) || (!info.Mode().IsRegular() && !info.IsDir()) {
		return "", fmt.Errorf("Invalid scope path: %s", raw)
	}
	rel, err := filepath.Rel(root, path)
	if err != nil {
		return "", err
	}
	rel = filepath.ToSlash(rel)
	for _, part := range strings.Split(rel, "/") {
		if part == ".git" || part == ".jaipilot" {
			return "", fmt.Errorf("Scope must be Java source or a source directory: %s", raw)
		}
	}
	if info.Mode().IsRegular() && !strings.HasSuffix(rel, ".java") {
		return "", fmt.Errorf("Scope must be Java source or a source directory: %s", raw)
	}
	return rel, nil
}

var classPattern = regexp.MustCompile(`^[\p{L}_$][\p{L}\p{N}_$]*(\.[\p{L}_$][\p{L}\p{N}_$]*)*$`)
var packagePattern = regexp.MustCompile(`(?m)^\s*package\s+([\w.]+)\s*;`)
var selectionPattern = regexp.MustCompile(`^(.+):(\d+)(?:-(\d+))?$`)

func resolveScope(ctx context.Context, root string, in ScopeInput) (Scope, error) {
	scope := Scope{SelectedPaths: []string{}, Selections: []Selection{}}
	count := 0
	for _, yes := range []bool{in.All, len(in.Paths) > 0, len(in.Classes) > 0, len(in.Selections) > 0} {
		if yes {
			count++
		}
	}
	if count != 1 {
		return scope, fmt.Errorf("Choose exactly one scope: --all, --path, --class, or --selection")
	}
	if in.All {
		scope.SelectedPaths = []string{"."}
		scope.ProjectWide = true
		return scope, nil
	}
	for _, raw := range in.Paths {
		p, err := checkedPath(root, raw, false)
		if err != nil {
			return scope, err
		}
		scope.SelectedPaths = append(scope.SelectedPaths, p)
	}
	if len(in.Classes) > 0 {
		files, err := javaFiles(ctx, root)
		if err != nil {
			return scope, err
		}
		for _, name := range in.Classes {
			if !classPattern.MatchString(name) {
				return scope, fmt.Errorf("Invalid Java class: %s", name)
			}
			parts := strings.Split(name, ".")
			simple := parts[len(parts)-1]
			matches := []string{}
			for _, file := range files {
				if filepath.Base(file) != simple+".java" {
					continue
				}
				p, err := checkedPath(root, file, true)
				if err != nil {
					return scope, err
				}
				source, err := os.ReadFile(filepath.Join(root, p))
				if err != nil {
					return scope, err
				}
				pkg := ""
				if m := packagePattern.FindStringSubmatch(string(source)); m != nil {
					pkg = m[1]
				}
				if name == simple || name == pkg+"."+simple {
					matches = append(matches, p)
				}
			}
			if len(matches) != 1 {
				if len(matches) > 1 {
					return scope, fmt.Errorf("Class %s is ambiguous; use --path", name)
				}
				return scope, fmt.Errorf("Class not found: %s", name)
			}
			scope.SelectedPaths = append(scope.SelectedPaths, matches[0])
		}
	}
	for _, raw := range in.Selections {
		m := selectionPattern.FindStringSubmatch(raw)
		if m == nil {
			return scope, fmt.Errorf("Invalid selection: %s (use path:start-end)", raw)
		}
		p, err := checkedPath(root, m[1], true)
		if err != nil {
			return scope, err
		}
		start, _ := strconv.Atoi(m[2])
		end := start
		if m[3] != "" {
			end, _ = strconv.Atoi(m[3])
		}
		source, err := os.ReadFile(filepath.Join(root, p))
		if err != nil {
			return scope, err
		}
		lines := strings.Count(string(source), "\n") + 1
		if start < 1 || end < start || end > lines {
			return scope, fmt.Errorf("Selection is outside %s (%d lines)", p, lines)
		}
		scope.SelectedPaths = append(scope.SelectedPaths, p)
		scope.Selections = append(scope.Selections, Selection{p, start, end})
	}
	scope.SelectedPaths = unique(scope.SelectedPaths)
	return scope, nil
}
func projectContext(ctx context.Context, root string, scope Scope) (object, error) {
	files, err := javaFiles(ctx, root)
	if err != nil {
		return nil, err
	}
	roots := []string{}
	for _, file := range files {
		if index := strings.Index("/"+file, "/src/main/java/"); index >= 0 {
			roots = append(roots, file[:index])
		}
	}
	modules := []object{}
	testRoots := []string{}
	for _, module := range unique(roots) {
		name := module
		if name == "" {
			name = filepath.Base(root)
		}
		testRoot := filepath.Join(root, module, "src/test/java")
		testRoots = append(testRoots, testRoot)
		modules = append(modules, object{"name": name, "sourceRoots": []string{filepath.Join(root, module, "src/main/java")}, "testRoots": []string{testRoot}, "sdkHome": os.Getenv("JAVA_HOME")})
	}
	shell := "/bin/sh"
	if runtime.GOOS == "windows" {
		shell = "cmd.exe"
	}
	return object{"selectedPaths": scope.SelectedPaths, "projectDirectory": root, "os": runtime.GOOS, "shell": shell, "jdkHome": os.Getenv("JAVA_HOME"), "testRoots": testRoots, "modules": modules}, nil
}
