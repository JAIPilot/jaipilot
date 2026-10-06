package jaipilot

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

type Files map[string]string
type Baseline struct {
	FormatVersion  int           `json:"formatVersion"`
	ID             string        `json:"id"`
	Repository     string        `json:"repository"`
	CreatedAt      string        `json:"createdAt"`
	Scope          Scope         `json:"scope"`
	Intent         string        `json:"intent"`
	TestPaths      []string      `json:"testPaths"`
	TestCommand    string        `json:"testCommand"`
	TimeoutSeconds int           `json:"timeoutSeconds"`
	ProtectedFiles Files         `json:"protectedFiles"`
	TestFiles      Files         `json:"testFiles"`
	Verification   CommandResult `json:"verification"`
}

func stateDirectory(ctx context.Context, root string) (string, error) {
	path, err := git(ctx, root, "rev-parse", "--git-path", "jaipilot-mcp")
	if err != nil {
		return "", err
	}
	return absolute(root, path), nil
}
func under(path string, roots []string) bool {
	for _, root := range roots {
		if path == root || strings.HasPrefix(path, root+"/") {
			return true
		}
	}
	return false
}
func testPaths(root string, paths []string) ([]string, error) {
	root, err := real(root)
	if err != nil {
		return nil, err
	}
	out := []string{}
	for _, raw := range paths {
		if raw == "" || strings.ContainsRune(raw, 0) || filepath.IsAbs(raw) {
			return nil, errors.New("test_paths must be repository-relative test files or directories")
		}
		absolute := filepath.Join(root, raw)
		rel, err := filepath.Rel(root, absolute)
		if err != nil || rel == "." || !within(root, absolute) {
			return nil, fmt.Errorf("Invalid test path: %s", raw)
		}
		path := filepath.ToSlash(rel)
		for _, part := range strings.Split(path, "/") {
			if part == ".git" {
				return nil, fmt.Errorf("Invalid test path: %s", raw)
			}
		}
		for current := absolute; current != root; current = filepath.Dir(current) {
			info, err := os.Lstat(current)
			if errors.Is(err, os.ErrNotExist) {
				continue
			}
			if err != nil {
				return nil, err
			}
			if info.Mode()&os.ModeSymlink != 0 {
				return nil, fmt.Errorf("Test path contains a symbolic link: %s", raw)
			}
		}
		out = append(out, path)
	}
	return unique(out), nil
}
func snapshot(ctx context.Context, root string) (Files, error) {
	out, err := git(ctx, root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
	if err != nil {
		return nil, err
	}
	files := Files{}
	paths := unique(strings.Split(out, "\x00"))
	sort.Strings(paths)
	for _, path := range paths {
		if path == "" {
			continue
		}
		absolute := filepath.Join(root, path)
		info, err := os.Lstat(absolute)
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return nil, err
		}
		hash := sha256.New()
		if info.Mode()&os.ModeSymlink != 0 {
			link, err := os.Readlink(absolute)
			if err != nil {
				return nil, err
			}
			fmt.Fprint(hash, "symlink:"+link)
		} else if info.Mode().IsRegular() {
			file, err := os.Open(absolute)
			if err != nil {
				return nil, err
			}
			_, err = io.Copy(hash, file)
			file.Close()
			if err != nil {
				return nil, err
			}
			fmt.Fprintf(hash, ":%d", info.Mode().Perm()&0111)
		} else {
			return nil, fmt.Errorf("Unsupported repository entry: %s", path)
		}
		files[filepath.ToSlash(path)] = hex.EncodeToString(hash.Sum(nil))
	}
	return files, nil
}
func selectFiles(files Files, paths []string, include bool) Files {
	out := Files{}
	for path, hash := range files {
		if under(path, paths) == include {
			out[path] = hash
		}
	}
	return out
}
func changed(before, after Files) []string {
	paths := []string{}
	for path, hash := range before {
		if after[path] != hash {
			paths = append(paths, path)
		}
	}
	for path, hash := range after {
		if before[path] != hash {
			paths = append(paths, path)
		}
	}
	paths = unique(paths)
	sort.Strings(paths)
	return paths
}
func saveBaseline(ctx context.Context, root string, b Baseline) error {
	directory, err := stateDirectory(ctx, root)
	if err != nil {
		return err
	}
	return atomicJSON(filepath.Join(directory, "baselines", b.ID+".json"), b, 0600)
}

var uuidPattern = regexp.MustCompile(`^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$`)

func loadBaseline(ctx context.Context, root, id string) (Baseline, error) {
	var b Baseline
	if !uuidPattern.MatchString(id) {
		return b, errors.New("Invalid baseline_id")
	}
	dir, err := stateDirectory(ctx, root)
	if err != nil {
		return b, err
	}
	bytes, err := os.ReadFile(filepath.Join(dir, "baselines", id+".json"))
	if errors.Is(err, os.ErrNotExist) {
		return b, fmt.Errorf("Baseline not found: %s", id)
	}
	if err != nil {
		return b, err
	}
	if err = json.Unmarshal(bytes, &b); err != nil {
		return b, err
	}
	if b.FormatVersion != 1 || b.ID != id || b.Repository != root || strings.TrimSpace(b.TestCommand) == "" || b.TimeoutSeconds < 1 || b.TimeoutSeconds > 7200 || len(b.TestPaths) == 0 || b.TestFiles == nil || b.ProtectedFiles == nil {
		return b, errors.New("Invalid baseline manifest")
	}
	if _, err := testPaths(root, b.TestPaths); err != nil {
		return b, err
	}
	return b, nil
}
