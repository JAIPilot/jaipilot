package jaipilot

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"time"
)

const releaseRepository = "JAIPilot/jaipilot"
const maxBinarySize = 16 << 20

var stableVersion = regexp.MustCompile(`^\d+\.\d+\.\d+$`)
var digestPattern = regexp.MustCompile(`^sha256:[0-9a-f]{64}$`)

type releaseAsset struct {
	Name   string `json:"name"`
	URL    string `json:"browser_download_url"`
	Digest string `json:"digest"`
	Size   int64  `json:"size"`
}
type release struct {
	Tag        string         `json:"tag_name"`
	Draft      bool           `json:"draft"`
	Prerelease bool           `json:"prerelease"`
	Assets     []releaseAsset `json:"assets"`
}
type updateOptions struct {
	automatic, checkOnly        bool
	version, executable, target string
	client                      *http.Client
	execute                     func(context.Context, string, []string, bool) (int, string, error)
}

func newerVersion(candidate, current string) (bool, error) {
	if !stableVersion.MatchString(candidate) || !stableVersion.MatchString(current) {
		return false, errors.New("Invalid release version")
	}
	next, previous := strings.Split(candidate, "."), strings.Split(current, ".")
	for i := range next {
		a, err := strconv.ParseUint(next[i], 10, 64)
		if err != nil {
			return false, err
		}
		b, err := strconv.ParseUint(previous[i], 10, 64)
		if err != nil {
			return false, err
		}
		if a != b {
			return a > b, nil
		}
	}
	return false, nil
}
func nativeTarget() string {
	architecture := map[string]string{"amd64": "x86_64", "arm64": "aarch64"}[runtime.GOARCH]
	platform := map[string]string{"darwin": "apple-darwin", "linux": "unknown-linux-gnu", "windows": "pc-windows-msvc"}[runtime.GOOS]
	if architecture == "" || platform == "" || (runtime.GOOS == "windows" && runtime.GOARCH != "amd64") {
		return ""
	}
	return architecture + "-" + platform
}
func shouldAutoUpdate(args []string) bool {
	return os.Getenv("JAIPILOT_NO_UPDATE") != "1" && len(args) > 0 && (args[0] == "run" || args[0] == "workflows")
}

func updateExecute(ctx context.Context, path string, args []string, capture bool) (int, string, error) {
	if capture {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
	}
	cmd := exec.CommandContext(ctx, path, args...)
	cmd.Env = append(os.Environ(), "JAIPILOT_NO_UPDATE=1")
	var out []byte
	var err error
	if capture {
		out, err = cmd.Output()
	} else {
		cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
		err = cmd.Run()
	}
	if err != nil {
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			return exit.ExitCode(), strings.TrimSpace(string(out)), nil
		}
		return 1, "", err
	}
	return 0, strings.TrimSpace(string(out)), nil
}

func installBinary(ctx context.Context, r release, version string, options updateOptions) (string, error) {
	target := options.target
	if target == "" {
		target = nativeTarget()
	}
	name := "jaipilot-" + target
	if strings.HasSuffix(target, "windows-msvc") {
		name += ".exe"
	}
	var asset releaseAsset
	for _, item := range r.Assets {
		if item.Name == name {
			asset = item
			break
		}
	}
	expectedURL := "https://github.com/" + releaseRepository + "/releases/download/" + r.Tag + "/" + name
	if asset.URL != expectedURL || !digestPattern.MatchString(asset.Digest) || asset.Size <= 0 || asset.Size > maxBinarySize {
		return "", fmt.Errorf("A verified native release binary is not available for %s", target)
	}
	executable := options.executable
	if executable == "" {
		var err error
		executable, err = os.Executable()
		if err != nil {
			return "", err
		}
	}
	executable, err := real(executable)
	if err != nil {
		return "", err
	}
	lock, err := os.OpenFile(executable+".native-update.lock", os.O_CREATE|os.O_RDWR, 0600)
	if err != nil {
		return "", err
	}
	defer lock.Close()
	if err := lockFile(lock); err != nil {
		return "", errors.New("Another CLI update is running or the installation is not writable")
	}
	defer unlockFile(lock)
	suffix := ""
	if runtime.GOOS == "windows" {
		suffix = ".exe"
	}
	file, err := os.CreateTemp(filepath.Dir(executable), ".jaipilot-update-*"+suffix)
	if err != nil {
		return "", err
	}
	temporary := file.Name()
	defer os.Remove(temporary)
	defer file.Close()
	client := options.client
	if client == nil {
		client = http.DefaultClient
	}
	downloadCtx, cancel := context.WithTimeout(ctx, 120*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(downloadCtx, "GET", asset.URL, nil)
	if err != nil {
		return "", err
	}
	response, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return "", fmt.Errorf("Download failed (HTTP %d)", response.StatusCode)
	}
	hash := sha256.New()
	size, err := io.Copy(io.MultiWriter(file, hash), io.LimitReader(response.Body, asset.Size+1))
	if err != nil {
		return "", err
	}
	if size != asset.Size || "sha256:"+hex.EncodeToString(hash.Sum(nil)) != asset.Digest {
		return "", errors.New("Release binary checksum or size did not match; installation unchanged")
	}
	if err := file.Sync(); err != nil {
		return "", err
	}
	if err := file.Close(); err != nil {
		return "", err
	}
	if err := os.Chmod(temporary, 0755); err != nil {
		return "", err
	}
	execute := options.execute
	if execute == nil {
		execute = updateExecute
	}
	code, output, err := execute(ctx, temporary, []string{"--version"}, true)
	if err != nil || code != 0 || output != "JAIPilot CLI "+version {
		return "", errors.New("Downloaded CLI failed its version check; installation unchanged")
	}
	if runtime.GOOS == "windows" {
		backup := executable + ".previous"
		if err := os.Remove(backup); err != nil && !errors.Is(err, os.ErrNotExist) {
			return "", err
		}
		if err := os.Rename(executable, backup); err != nil {
			return "", err
		}
		if err := os.Rename(temporary, executable); err != nil {
			rollback := os.Rename(backup, executable)
			if rollback != nil {
				return "", fmt.Errorf("Update failed: %v; restore %s manually: %v", err, backup, rollback)
			}
			return "", err
		}
		_ = os.Remove(backup)
	} else if err := os.Rename(temporary, executable); err != nil {
		return "", err
	}
	return executable, nil
}

func updateCLI(ctx context.Context, args []string, options updateOptions) (*int, error) {
	current := options.version
	if current == "" {
		current = Version
	}
	client := options.client
	if client == nil {
		client = http.DefaultClient
	}
	checkTimeout := 15 * time.Second
	if options.automatic {
		checkTimeout = 3 * time.Second
	}
	checkCtx, cancel := context.WithTimeout(ctx, checkTimeout)
	defer cancel()
	req, err := http.NewRequestWithContext(checkCtx, "GET", "https://api.github.com/repos/"+releaseRepository+"/releases/latest", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "JAIPilot/"+current)
	response, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return nil, fmt.Errorf("Release check failed (HTTP %d)", response.StatusCode)
	}
	var r release
	if err := json.NewDecoder(io.LimitReader(response.Body, 1<<20)).Decode(&r); err != nil {
		return nil, err
	}
	if !strings.HasPrefix(r.Tag, "v") || !stableVersion.MatchString(strings.TrimPrefix(r.Tag, "v")) || r.Draft || r.Prerelease || r.Assets == nil {
		return nil, errors.New("GitHub returned an invalid stable release")
	}
	version := strings.TrimPrefix(r.Tag, "v")
	newer, err := newerVersion(version, current)
	if err != nil {
		return nil, err
	}
	if !newer {
		if !options.automatic {
			fmt.Fprintf(os.Stderr, "JAIPilot CLI %s is up to date.\n", current)
		}
		return nil, nil
	}
	if options.checkOnly {
		fmt.Fprintf(os.Stderr, "JAIPilot CLI %s is available; run `jaipilot update`.\n", version)
		return nil, nil
	}
	path, err := installBinary(ctx, r, version, options)
	if err != nil {
		return nil, err
	}
	fmt.Fprintf(os.Stderr, "Installed JAIPilot CLI %s.\n", version)
	if !options.automatic {
		return nil, nil
	}
	execute := options.execute
	if execute == nil {
		execute = updateExecute
	}
	code, _, err := execute(ctx, path, args, false)
	return &code, err
}
