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
	"os/signal"
	"path/filepath"
	"reflect"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"
)

func write(t *testing.T, root, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(filepath.Join(root, path)), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, path), []byte(content), 0644); err != nil {
		t.Fatal(err)
	}
}
func mustGit(t *testing.T, root string, args ...string) string {
	t.Helper()
	out, err := git(context.Background(), root, args...)
	if err != nil {
		t.Fatal(err)
	}
	return out
}
func fixture(t *testing.T) (string, string, string) {
	t.Helper()
	root := t.TempDir()
	root, _ = real(root)
	source, test := "src/main/java/com/acme/Order.java", "src/test/java/com/acme/OrderTest.java"
	write(t, root, source, "package com.acme; public class Order { public static int cost() { return 5; } }\n")
	write(t, root, ".gitignore", "target/\n")
	mustGit(t, root, "init", "-q")
	return root, source, test
}
func resultFixture() object {
	return object{"status": "complete", "summary": "Tests generated", "verification": object{"tests": "model claimed pass"}}
}
func fixtureJobs(t *testing.T, root, test string) (*Jobs, LockInput) {
	t.Helper()
	jobs := newJobs(context.Background(), root)
	t.Cleanup(jobs.stop)
	jobs.token = func(context.Context) (string, error) { return "test-token", nil }
	jobs.run = func(_ context.Context, _ string, _ string, _ Scope, options workflowOptions) (object, error) {
		if !strings.Contains(options.UserRequest, "Only create or edit tests") {
			t.Error("Missing preservation constraint")
		}
		write(t, root, test, "package com.acme; public class OrderTest { public static void main(String[] args) { if (Order.cost() != 5) throw new AssertionError(\"baseline assertion failed\"); System.out.println(\"baseline tests passed\"); } }\n")
		return resultFixture(), nil
	}
	return jobs, LockInput{Scope: ScopeInput{Classes: []string{"com.acme.Order"}}, TestPaths: []string{"src/test/java"}, TestCommand: "javac -d target/test-classes src/main/java/com/acme/Order.java src/test/java/com/acme/OrderTest.java && java -cp target/test-classes com.acme.OrderTest", TimeoutSeconds: 30, Intent: "Extract helper"}
}
func finished(t *testing.T, jobs *Jobs, view JobView, err error) JobView {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
	v, err := jobs.status(context.Background(), view.JobID, 30)
	if err != nil {
		t.Fatal(err)
	}
	if v.State == "running" || v.State == "cancelling" {
		t.Fatalf("Job did not finish: %+v", v)
	}
	return v
}

func TestExactScopeAndProject(t *testing.T) {
	root, source, _ := fixture(t)
	ctx := context.Background()
	for _, input := range []ScopeInput{{Classes: []string{"com.acme.Order"}}, {Paths: []string{source}}, {Selections: []string{source + ":1-2"}}} {
		scope, err := resolveScope(ctx, root, input)
		if err != nil || !reflect.DeepEqual(scope.SelectedPaths, []string{source}) {
			t.Fatalf("Scope: %+v %v", scope, err)
		}
	}
	for _, input := range []ScopeInput{{}, {All: true, Paths: []string{source}}, {Selections: []string{source + ":0-1"}}, {Selections: []string{source + ":1-99"}}, {Classes: []string{"com.acme.Missing"}}, {Paths: []string{".git"}}, {Paths: []string{".gitignore"}}, {Paths: []string{"../outside.java"}}} {
		if _, err := resolveScope(ctx, root, input); err == nil {
			t.Errorf("Accepted invalid scope: %+v", input)
		}
	}
	write(t, root, "other/src/main/java/com/other/Order.java", "package com.other; class Order {}\n")
	if _, err := resolveScope(ctx, root, ScopeInput{Classes: []string{"Order"}}); err == nil {
		t.Fatal("Accepted ambiguous class")
	}
	project, err := projectContext(ctx, root, Scope{})
	if err != nil {
		t.Fatal(err)
	}
	if len(project["modules"].([]object)) != 2 {
		t.Fatalf("Modules: %v", project)
	}
	outside := t.TempDir()
	write(t, outside, "Outside.java", "class Outside {}")
	if err := os.Symlink(filepath.Join(outside, "Outside.java"), filepath.Join(root, "Link.java")); err == nil {
		if _, err := resolveScope(ctx, root, ScopeInput{Paths: []string{"Link.java"}}); err == nil {
			t.Fatal("Accepted escaping source symlink")
		}
		if _, err := testPaths(root, []string{"Link.java"}); err == nil {
			t.Fatal("Accepted test symlink")
		}
	}
	write(t, root, " leading.java", "class Leading {}")
	files, err := snapshot(ctx, root)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := files[" leading.java"]; !ok {
		t.Fatal("Git filename lost leading whitespace")
	}
}

func TestScopeWireValidation(t *testing.T) {
	for _, data := range []string{`{}`, `{"all":false}`, `{"paths":[]}`, `{"paths":[""]}`, `{"paths":["Order.java"],"all":false}`, `{"other":true}`, `null`} {
		var in ScopeInput
		if json.Unmarshal([]byte(data), &in) == nil {
			t.Errorf("Accepted %s", data)
		}
	}
	var in ScopeInput
	if err := json.Unmarshal([]byte(`{"all":true}`), &in); err != nil || !in.All {
		t.Fatal(err)
	}
}

func TestDurableBaselineWithRealJava(t *testing.T) {
	if _, err := exec.LookPath("javac"); err != nil {
		t.Fatal("Java compiler is required for the baseline gate")
	}
	root, source, test := fixture(t)
	jobs, input := fixtureJobs(t, root, test)
	mustGit(t, root, "add", ".")
	mustGit(t, root, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-qm", "initial")
	before, _ := os.ReadFile(filepath.Join(root, source))
	write(t, root, source, string(before)+"// existing dirty work\n")
	saved, err := snapshot(context.Background(), root)
	if err != nil {
		t.Fatal(err)
	}
	v, err := jobs.lock(input)
	ready := finished(t, jobs, v, err)
	if !ready.ReadyToEdit || ready.Verified || ready.State != "completed" {
		t.Fatalf("%+v", ready)
	}
	id := str(ready.Result["baseline_id"])
	baseline, err := loadBaseline(context.Background(), root, id)
	if err != nil {
		t.Fatal(err)
	}
	if baseline.ProtectedFiles[source] != saved[source] || !strings.Contains(baseline.Verification.Output, "baseline tests passed") {
		t.Fatal("Baseline lacks independent evidence or dirty-file snapshot")
	}
	jobs.stop()
	restarted := newJobs(context.Background(), root)
	t.Cleanup(restarted.stop)
	restarted.run = func(context.Context, string, string, Scope, workflowOptions) (object, error) {
		t.Error("Verification must not call the model")
		return nil, errors.New("Unexpected model call")
	}
	write(t, root, source, string(before)+"// refactored\n")
	v, err = restarted.verify(id)
	passed := finished(t, restarted, v, err)
	if !passed.Verified || !reflect.DeepEqual(passed.Result["changed_production_paths"], []string{source}) {
		t.Fatalf("%+v", passed)
	}
	write(t, root, source, strings.Replace(string(before), "return 5", "return 7", 1))
	v, err = restarted.verify(id)
	failed := finished(t, restarted, v, err)
	if failed.Verified || failed.State != "blocked" || failed.Result["verification"].(CommandResult).ExitCode == 0 {
		t.Fatalf("Missed real Java regression: %+v", failed)
	}
	write(t, root, test, "// weakened test\n")
	v, err = restarted.verify(id)
	weakened := finished(t, restarted, v, err)
	if weakened.State != "blocked" || !reflect.DeepEqual(weakened.Result["changed_test_paths"], []string{test}) {
		t.Fatalf("%+v", weakened)
	}
}

func TestBaselineRejectsFalseSuccess(t *testing.T) {
	for _, failure := range []string{"source", "independent", "workflow", "verification-write"} {
		t.Run(failure, func(t *testing.T) {
			root, source, test := fixture(t)
			jobs, input := fixtureJobs(t, root, test)
			run := jobs.run
			jobs.run = func(ctx context.Context, r, w string, s Scope, o workflowOptions) (object, error) {
				result, err := run(ctx, r, w, s, o)
				if failure == "source" {
					write(t, root, source, "BROKEN")
				}
				if failure == "workflow" {
					result["status"] = "blocked"
				}
				return result, err
			}
			if failure == "independent" {
				input.TestCommand = "exit 1"
			}
			if failure == "verification-write" {
				jobs.execute = func(context.Context, string, object) (CommandResult, error) {
					write(t, root, "unrelated.txt", "unexpected change")
					return CommandResult{}, nil
				}
			}
			v, err := jobs.lock(input)
			blocked := finished(t, jobs, v, err)
			if blocked.State != "blocked" || blocked.ReadyToEdit || blocked.Verified {
				t.Fatalf("%+v", blocked)
			}
		})
	}
}

func TestRepositoryLockCancellationAndShutdown(t *testing.T) {
	root, _, test := fixture(t)
	a, input := fixtureJobs(t, root, test)
	b, _ := fixtureJobs(t, root, test)
	entered := make(chan struct{})
	a.run = func(ctx context.Context, _ string, _ string, _ Scope, _ workflowOptions) (object, error) {
		close(entered)
		<-ctx.Done()
		return nil, ctx.Err()
	}
	v, err := a.lock(input)
	if err != nil {
		t.Fatal(err)
	}
	<-entered
	if _, err := b.lock(input); err == nil {
		t.Fatal("Second process lock allowed concurrent writes")
	}
	if _, err := a.lock(input); err == nil {
		t.Fatal("Same process allowed concurrent writes")
	}
	if _, err := a.cancel(v.JobID); err != nil {
		t.Fatal(err)
	}
	done := finished(t, a, v, nil)
	if done.State != "cancelled" || done.ReadyToEdit {
		t.Fatalf("%+v", done)
	}
	v, err = b.lock(input)
	ready := finished(t, b, v, err)
	if !ready.ReadyToEdit {
		t.Fatalf("Lock not released: %+v", ready)
	}
	b.stop()
	if _, err := b.lock(input); err == nil {
		t.Fatal("Stopped server accepted work")
	}
}

func TestCommandBoundsExitAndTimeout(t *testing.T) {
	root := t.TempDir()
	quote := "'"
	if runtime.GOOS == "windows" {
		quote = "\""
	}
	command := "echo " + quote + "hello world" + quote + " && exit 7"
	r, err := runCommand(context.Background(), root, object{"command": command, "timeoutSeconds": 10})
	if err != nil || r.ExitCode != 7 || !strings.Contains(r.Output, "hello world") {
		t.Fatalf("%+v %v", r, err)
	}
	for _, in := range []object{{"command": "", "timeoutSeconds": 1}, {"command": "echo x", "timeoutSeconds": 0}, {"command": "echo x", "timeoutSeconds": 1.5}, {"command": "echo x", "timeoutSeconds": 7201}, {"command": "echo\x00x", "timeoutSeconds": 1}} {
		if _, _, err := validateCommand(in); err == nil {
			t.Errorf("Accepted invalid command: %v", in)
		}
	}
	w := &tailWriter{}
	_, _ = w.Write([]byte(strings.Repeat("x", maxOutput+200)))
	if len(w.data) != maxOutput || !w.truncated {
		t.Fatal("Unbounded output")
	}
	command = "sleep 30"
	if runtime.GOOS == "windows" {
		command = "ping -n 30 127.0.0.1 >nul"
	}
	r, err = runCommand(context.Background(), root, object{"command": command, "timeoutSeconds": 1})
	if err != nil || !r.TimedOut || r.ExitCode != 124 || r.DurationMS > 7000 {
		t.Fatalf("Timeout: %+v %v", r, err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	time.AfterFunc(100*time.Millisecond, cancel)
	_, err = runCommand(ctx, root, object{"command": command, "timeoutSeconds": 60})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("Cancellation: %v", err)
	}
}

func TestProcessHelper(t *testing.T) {
	args := os.Args
	for i, arg := range args {
		if arg != "--" || i+1 >= len(args) {
			continue
		}
		signal.Ignore(terminationSignals()...)
		if args[i+1] == "child" {
			time.Sleep(30 * time.Second)
			os.Exit(0)
		}
		if args[i+1] != "parent" {
			return
		}
		path, _ := os.Executable()
		child := exec.Command(path, "-test.run=^TestProcessHelper$", "--", "child")
		if err := child.Start(); err != nil {
			os.Exit(1)
		}
		if err := os.WriteFile("pids.json", []byte(jsonText([]int{os.Getpid(), child.Process.Pid})), 0600); err != nil {
			os.Exit(1)
		}
		_ = child.Wait()
		os.Exit(0)
	}
}

func TestCancellationKillsDescendantsIgnoringTermination(t *testing.T) {
	root := t.TempDir()
	path, _ := os.Executable()
	command := "\"" + path + "\" -test.run=^TestProcessHelper$ -- parent"
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() { _, err := runCommand(ctx, root, object{"command": command, "timeoutSeconds": 60}); done <- err }()
	var pids []int
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		data, err := os.ReadFile(filepath.Join(root, "pids.json"))
		if err == nil && json.Unmarshal(data, &pids) == nil {
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	if len(pids) != 2 {
		cancel()
		<-done
		t.Fatal("Command did not start its child")
	}
	cancel()
	select {
	case err := <-done:
		if !errors.Is(err, context.Canceled) {
			t.Fatal(err)
		}
	case <-time.After(8 * time.Second):
		t.Fatal("Cancellation did not finish")
	}
	for _, pid := range pids {
		deadline := time.Now().Add(3 * time.Second)
		for processAlive(pid) && time.Now().Before(deadline) {
			time.Sleep(20 * time.Millisecond)
		}
		if processAlive(pid) {
			t.Errorf("Cancelled descendant still running: %d", pid)
		}
	}
}

type roundTripper func(*http.Request) (*http.Response, error)

func (f roundTripper) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func response(code int, body string) *http.Response {
	return &http.Response{StatusCode: code, Header: http.Header{"Retry-After": []string{"0"}}, Body: io.NopCloser(strings.NewReader(body))}
}
func testSession(t *testing.T) {
	t.Helper()
	t.Setenv("JAIPILOT_CONFIG_DIR", t.TempDir())
	if err := atomicJSON(sessionPath(), Session{"test-token", "test-refresh", time.Now().Unix() + 3600, "test@example.com"}, 0600); err != nil {
		t.Fatal(err)
	}
}
func setClient(t *testing.T, f roundTripper) {
	old := http.DefaultClient
	http.DefaultClient = &http.Client{Transport: f}
	t.Cleanup(func() { http.DefaultClient = old })
}

func TestServiceProtocolRetryAndActualCommands(t *testing.T) {
	testSession(t)
	root, _, _ := fixture(t)
	var mu sync.Mutex
	attempts := 0
	requestID := ""
	executed := false
	setClient(t, func(r *http.Request) (*http.Response, error) {
		mu.Lock()
		defer mu.Unlock()
		if r.Header.Get("Authorization") != "Bearer test-token" {
			t.Error("Missing auth")
		}
		if r.Method == "GET" {
			return response(200, `{"protocolVersion":6,"workflows":[{"id":"generate_tests"}]}`), nil
		}
		var body object
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatal(err)
		}
		if num(body["protocolVersion"]) != 6 {
			t.Error("Protocol changed")
		}
		attempts++
		if attempts == 1 {
			requestID = str(body["requestId"])
			return response(202, `{"pending":true}`), nil
		}
		if attempts == 2 {
			if str(body["requestId"]) != requestID {
				t.Error("Retry lost idempotency")
			}
			return response(200, `{"content":[{"type":"tool_use","id":"cmd","name":"run_command","input":{"command":"echo actual-command","timeoutSeconds":10}}]}`), nil
		}
		if len(list(body["history"])) != 2 {
			t.Error("Command evidence omitted")
		}
		return response(200, `{"content":[{"type":"tool_use","id":"finish","name":"finish","input":{"status":"complete","summary":"verified"}}]}`), nil
	})
	scope, _ := resolveScope(context.Background(), root, ScopeInput{All: true})
	r, err := runWorkflow(context.Background(), root, "generate_tests", scope, workflowOptions{Execute: func(ctx context.Context, r string, in object) (CommandResult, error) {
		executed = true
		return runCommand(ctx, r, in)
	}})
	if err != nil || !executed || str(r["status"]) != "complete" || attempts != 3 {
		t.Fatalf("%v %v attempts=%d", r, err, attempts)
	}
}

func TestServiceErrorsCatalogAndCancellation(t *testing.T) {
	testSession(t)
	setClient(t, func(*http.Request) (*http.Response, error) {
		return response(500, `{"retryable":false,"error":"terminal error"}`), nil
	})
	if _, err := serviceRequest(context.Background(), "POST", object{"requestId": "exact-id"}); err == nil || !strings.Contains(err.Error(), "exact-id") {
		t.Fatal(err)
	}
	http.DefaultClient = &http.Client{Transport: roundTripper(func(*http.Request) (*http.Response, error) {
		return response(200, `{"protocolVersion":1,"workflows":[]}`), nil
	})}
	if _, err := workflows(context.Background()); err == nil {
		t.Fatal("Accepted incompatible service")
	}
	http.DefaultClient = &http.Client{Transport: roundTripper(func(*http.Request) (*http.Response, error) { return response(503, `{}`), nil })}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := serviceRequest(ctx, "GET", nil); !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
}

func TestMCPWireContractValidationAndAuth(t *testing.T) {
	root, source, _ := fixture(t)
	t.Setenv("JAIPILOT_CONFIG_DIR", t.TempDir())
	jobs := newJobs(context.Background(), root)
	t.Cleanup(jobs.stop)
	m := &mcpHandler{jobs: jobs}
	init, err := m.handle(context.Background(), "initialize", json.RawMessage(`{"protocolVersion":"2025-11-25"}`))
	if err != nil || str(obj(init)["instructions"]) == "" {
		t.Fatal(err)
	}
	catalog, err := m.handle(context.Background(), "tools/list", nil)
	if err != nil || len(obj(catalog)["tools"].([]object)) != 4 {
		t.Fatal(err)
	}
	for _, scope := range []object{{"all": true, "paths": []string{source}}, {"all": false}, {"paths": []string{}}} {
		result, err := m.handle(context.Background(), "tools/call", json.RawMessage(jsonText(object{"name": "lock_behavior", "arguments": object{"scope": scope, "test_paths": []string{"tests"}, "test_command": "never executed"}})))
		if err != nil || obj(result)["isError"] != true {
			t.Fatalf("Accepted invalid wire schema: %v %v", result, err)
		}
	}
	started, err := m.handle(context.Background(), "tools/call", json.RawMessage(jsonText(object{"name": "lock_behavior", "arguments": object{"scope": object{"paths": []string{source}}, "test_paths": []string{"tests"}, "test_command": "never executed"}})))
	if err != nil {
		t.Fatal(err)
	}
	v := obj(started)["structuredContent"].(JobView)
	final := finished(t, jobs, v, nil)
	if final.State != "failed" || !strings.Contains(final.Error, "Sign in first") || final.ReadyToEdit {
		t.Fatalf("%+v", final)
	}
}

func TestVerifiedUpdaterPreservesInstallation(t *testing.T) {
	for _, scenario := range []string{"success", "checksum", "size", "oversize", "url", "version"} {
		t.Run(scenario, func(t *testing.T) {
			root := t.TempDir()
			path := filepath.Join(root, "jaipilot")
			if runtime.GOOS == "windows" {
				path += ".exe"
			}
			write(t, root, filepath.Base(path), "old binary")
			data := []byte("verified new executable")
			sum := sha256.Sum256(data)
			asset := releaseAsset{Name: "jaipilot-" + nativeTarget(), Size: int64(len(data)), Digest: "sha256:" + hex.EncodeToString(sum[:])}
			if runtime.GOOS == "windows" {
				asset.Name += ".exe"
			}
			asset.URL = "https://github.com/JAIPilot/jaipilot/releases/download/v1.2.1/" + asset.Name
			switch scenario {
			case "checksum":
				asset.Digest = "sha256:" + strings.Repeat("0", 64)
			case "size":
				asset.Size--
			case "oversize":
				asset.Size = maxBinarySize + 1
			case "url":
				asset.URL = "https://example.com/binary"
			}
			options := updateOptions{executable: path, client: &http.Client{Transport: roundTripper(func(*http.Request) (*http.Response, error) { return response(200, string(data)), nil })}, execute: func(context.Context, string, []string, bool) (int, string, error) {
				if scenario == "version" {
					return 0, "JAIPilot CLI 0.0.0", nil
				}
				return 0, "JAIPilot CLI 1.2.1", nil
			}}
			_, err := installBinary(context.Background(), release{Tag: "v1.2.1", Assets: []releaseAsset{asset}}, "1.2.1", options)
			installed, _ := os.ReadFile(path)
			if scenario == "success" {
				if err != nil || string(installed) != string(data) {
					t.Fatalf("%s %v", installed, err)
				}
			} else if err == nil || string(installed) != "old binary" {
				t.Fatalf("Existing installation damaged: %s %v", installed, err)
			}
			entries, _ := os.ReadDir(root)
			for _, entry := range entries {
				if strings.HasPrefix(entry.Name(), ".jaipilot-update-") {
					t.Fatal("Temporary binary leaked")
				}
			}
		})
	}
	for _, pair := range [][3]string{{"1.2.0", "1.1.1", "true"}, {"1.2.0", "1.2.0", "false"}, {"1.1.1", "1.2.0", "false"}, {"2.0.0", "1.9.9", "true"}} {
		newer, err := newerVersion(pair[0], pair[1])
		if err != nil || fmt.Sprint(newer) != pair[2] {
			t.Fatal(pair, err)
		}
	}
	if _, err := newerVersion("1.2.0-beta", "1.1.1"); err == nil {
		t.Fatal("Accepted prerelease")
	}
}
