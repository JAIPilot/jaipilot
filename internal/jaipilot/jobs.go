package jaipilot

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type LockInput struct {
	Scope          ScopeInput `json:"scope"`
	TestPaths      []string   `json:"test_paths"`
	TestCommand    string     `json:"test_command"`
	TimeoutSeconds int        `json:"timeout_seconds"`
	Intent         string     `json:"intent,omitempty"`
}
type JobView struct {
	JobID       string   `json:"job_id"`
	Kind        string   `json:"kind"`
	State       string   `json:"state"`
	ReadyToEdit bool     `json:"ready_to_edit"`
	Verified    bool     `json:"verified"`
	CreatedAt   string   `json:"created_at"`
	DurationMS  int64    `json:"duration_ms"`
	Progress    []string `json:"progress"`
	Result      object   `json:"result,omitempty"`
	Error       string   `json:"error,omitempty"`
}
type job struct {
	view    JobView
	cancel  context.CancelFunc
	done    chan struct{}
	started time.Time
}
type jobOutcome struct {
	state  string
	result object
}
type Workflow func(context.Context, string, string, Scope, workflowOptions) (object, error)
type Jobs struct {
	root    string
	ctx     context.Context
	mu      sync.Mutex
	jobs    map[string]*job
	busy    bool
	stopped bool
	run     Workflow
	execute Execute
	token   func(context.Context) (string, error)
}

func newJobs(ctx context.Context, root string) *Jobs {
	return &Jobs{root: root, ctx: ctx, jobs: map[string]*job{}, run: runWorkflow, execute: runCommand, token: bearerToken}
}
func (j *Jobs) acquire() (func() error, error) {
	dir, err := stateDirectory(j.ctx, j.root)
	if err != nil {
		return nil, err
	}
	if err = os.MkdirAll(dir, 0700); err != nil {
		return nil, err
	}
	file, err := os.OpenFile(filepath.Join(dir, "job.lock"), os.O_CREATE|os.O_RDWR, 0600)
	if err != nil {
		return nil, err
	}
	if err = lockFile(file); err != nil {
		file.Close()
		return nil, errors.New("A JAIPilot MCP job is already running in this repository")
	}
	return func() error {
		err := unlockFile(file)
		closeErr := file.Close()
		if err != nil {
			return err
		}
		return closeErr
	}, nil
}
func (j *Jobs) start(kind string, action func(context.Context, func(string)) (jobOutcome, error)) (JobView, error) {
	j.mu.Lock()
	if j.stopped {
		j.mu.Unlock()
		return JobView{}, errors.New("JAIPilot MCP is stopping")
	}
	if j.busy {
		j.mu.Unlock()
		return JobView{}, errors.New("A JAIPilot MCP job is already running in this repository")
	}
	j.busy = true
	j.mu.Unlock()
	release, err := j.acquire()
	if err != nil {
		j.mu.Lock()
		j.busy = false
		j.mu.Unlock()
		return JobView{}, err
	}
	ctx, cancel := context.WithCancel(j.ctx)
	started := time.Now()
	b := &job{view: JobView{JobID: uuid(), Kind: kind, State: "running", CreatedAt: started.UTC().Format(time.RFC3339Nano), Progress: []string{}}, cancel: cancel, done: make(chan struct{}), started: started}
	j.mu.Lock()
	for id, old := range j.jobs {
		if len(j.jobs) < 100 {
			break
		}
		if old.view.State != "running" && old.view.State != "cancelling" {
			delete(j.jobs, id)
		}
	}
	j.jobs[b.view.JobID] = b
	j.mu.Unlock()
	go func() {
		defer cancel()
		report := func(s string) {
			if len(s) > 2000 {
				s = s[len(s)-2000:]
			}
			j.mu.Lock()
			b.view.Progress = append(b.view.Progress, s)
			if len(b.view.Progress) > 20 {
				b.view.Progress = b.view.Progress[len(b.view.Progress)-20:]
			}
			j.mu.Unlock()
			fmt.Fprintln(os.Stderr, s)
		}
		outcome, err := action(ctx, report)
		if ctx.Err() != nil {
			err = ctx.Err()
		}
		releaseErr := release()
		j.mu.Lock()
		defer j.mu.Unlock()
		if err != nil {
			b.view.State = "failed"
			if ctx.Err() != nil {
				b.view.State = "cancelled"
			}
			b.view.Error = err.Error()
		} else {
			b.view.State = outcome.state
			b.view.Result = outcome.result
			b.view.ReadyToEdit = kind == "lock_behavior" && outcome.state == "completed"
			b.view.Verified = kind == "verify_behavior" && outcome.state == "completed"
		}
		if releaseErr != nil {
			b.view.State = "failed"
			b.view.ReadyToEdit = false
			b.view.Verified = false
			b.view.Error = "Could not release repository lock: " + releaseErr.Error()
		}
		b.view.DurationMS = time.Since(started).Milliseconds()
		j.busy = false
		close(b.done)
	}()
	return j.status(context.Background(), b.view.JobID, 0)
}
func (j *Jobs) lock(in LockInput) (JobView, error) {
	scope, err := resolveScope(j.ctx, j.root, in.Scope)
	if err != nil {
		return JobView{}, err
	}
	paths, err := testPaths(j.root, in.TestPaths)
	if err != nil {
		return JobView{}, err
	}
	return j.start("lock_behavior", func(ctx context.Context, report func(string)) (jobOutcome, error) {
		if _, err := j.token(ctx); err != nil {
			return jobOutcome{}, err
		}
		before, err := snapshot(ctx, j.root)
		if err != nil {
			return jobOutcome{}, err
		}
		for path := range before {
			if strings.HasSuffix(path, ".java") && (scope.ProjectWide || under(path, scope.SelectedPaths)) && !scope.ProjectWide && under(path, paths) {
				return jobOutcome{}, errors.New("test_paths must not include the selected production code")
			}
		}
		protected := selectFiles(before, paths, false)
		intent := in.Intent
		if intent == "" {
			intent = "Preserve existing behavior during a refactor."
		}
		workflow, err := j.run(ctx, j.root, "lock_behavior", scope, workflowOptions{UserRequest: strings.Join([]string{"Prepare a behavior baseline before the calling coding agent edits production code.", "Planned change: " + intent, "Only create or edit tests within these repository paths: " + jsonText(paths) + ".", "Preserve all other files, including existing production code and build configuration.", "Validate with this command: " + in.TestCommand, "Do not change existing behavior to match the planned change; characterize it as it is."}, "\n"), Report: report, Execute: func(ctx context.Context, root string, input object) (CommandResult, error) {
			if _, seconds, err := validateCommand(input); err == nil {
				input["timeoutSeconds"] = max(seconds, in.TimeoutSeconds)
			}
			return j.execute(ctx, root, input)
		}})
		if err != nil {
			return jobOutcome{}, err
		}
		after, err := snapshot(ctx, j.root)
		if err != nil {
			return jobOutcome{}, err
		}
		modified := changed(protected, selectFiles(after, paths, false))
		if len(modified) > 0 || str(workflow["status"]) != "complete" {
			return jobOutcome{"blocked", object{"workflow": workflow, "changed_protected_paths": modified, "baseline_id": nil}}, nil
		}
		tests := selectFiles(after, paths, true)
		if len(tests) == 0 {
			return jobOutcome{"blocked", object{"reason": "No baseline test files found", "workflow": workflow}}, nil
		}
		testFiles := []string{}
		for path := range tests {
			testFiles = append(testFiles, path)
		}
		if _, err := testPaths(j.root, testFiles); err != nil {
			return jobOutcome{}, err
		}
		report("JAIPilot MCP: independently verifying the baseline…")
		verification, err := j.execute(ctx, j.root, object{"command": in.TestCommand, "purpose": "baseline tests", "timeoutSeconds": in.TimeoutSeconds})
		if err != nil {
			return jobOutcome{}, err
		}
		final, err := snapshot(ctx, j.root)
		if err != nil {
			return jobOutcome{}, err
		}
		during := changed(after, final)
		if verification.ExitCode != 0 || len(during) > 0 {
			return jobOutcome{"blocked", object{"workflow": workflow, "verification": verification, "changed_during_verification": during, "baseline_id": nil}}, nil
		}
		if ctx.Err() != nil {
			return jobOutcome{}, ctx.Err()
		}
		baseline := Baseline{1, uuid(), j.root, time.Now().UTC().Format(time.RFC3339Nano), scope, intent, paths, in.TestCommand, in.TimeoutSeconds, protected, tests, verification}
		if err := saveBaseline(ctx, j.root, baseline); err != nil {
			return jobOutcome{}, err
		}
		return jobOutcome{"completed", object{"baseline_id": baseline.ID, "scope": scope, "test_paths": testFiles, "verification": verification, "workflow": workflow}}, nil
	})
}
func (j *Jobs) verify(id string) (JobView, error) {
	baseline, err := loadBaseline(j.ctx, j.root, id)
	if err != nil {
		return JobView{}, err
	}
	return j.start("verify_behavior", func(ctx context.Context, report func(string)) (jobOutcome, error) {
		before, err := snapshot(ctx, j.root)
		if err != nil {
			return jobOutcome{}, err
		}
		modified := changed(baseline.TestFiles, selectFiles(before, baseline.TestPaths, true))
		if len(modified) > 0 {
			return jobOutcome{"blocked", object{"baseline_id": id, "reason": "Baseline tests changed; restore them before verification", "changed_test_paths": modified}}, nil
		}
		report("JAIPilot MCP: verifying preserved behavior after edits…")
		verification, err := j.execute(ctx, j.root, object{"command": baseline.TestCommand, "purpose": "preserved baseline tests", "timeoutSeconds": baseline.TimeoutSeconds})
		if err != nil {
			return jobOutcome{}, err
		}
		after, err := snapshot(ctx, j.root)
		if err != nil {
			return jobOutcome{}, err
		}
		during := changed(before, after)
		state := "completed"
		if verification.ExitCode != 0 || len(during) > 0 {
			state = "blocked"
		}
		return jobOutcome{state, object{"baseline_id": id, "verification": verification, "changed_production_paths": changed(baseline.ProtectedFiles, selectFiles(before, baseline.TestPaths, false)), "changed_during_verification": during}}, nil
	})
}
func (j *Jobs) status(ctx context.Context, id string, wait int) (JobView, error) {
	j.mu.Lock()
	b := j.jobs[id]
	j.mu.Unlock()
	if b == nil {
		return JobView{}, fmt.Errorf("Job not found: %s. Jobs belong to this MCP server instance.", id)
	}
	if wait > 0 {
		timer := time.NewTimer(time.Duration(wait) * time.Second)
		defer timer.Stop()
		select {
		case <-ctx.Done():
			return JobView{}, ctx.Err()
		case <-b.done:
		case <-timer.C:
		}
	}
	j.mu.Lock()
	defer j.mu.Unlock()
	v := b.view
	v.Progress = append([]string{}, v.Progress...)
	if v.State == "running" || v.State == "cancelling" {
		v.DurationMS = time.Since(b.started).Milliseconds()
	}
	return v, nil
}
func (j *Jobs) cancel(id string) (JobView, error) {
	j.mu.Lock()
	b := j.jobs[id]
	if b != nil && b.view.State == "running" {
		b.view.State = "cancelling"
		b.cancel()
	}
	j.mu.Unlock()
	if b == nil {
		return JobView{}, fmt.Errorf("Job not found: %s", id)
	}
	return j.status(context.Background(), id, 0)
}
func (j *Jobs) stop() {
	j.mu.Lock()
	j.stopped = true
	active := []*job{}
	for _, b := range j.jobs {
		b.cancel()
		active = append(active, b)
	}
	j.mu.Unlock()
	for _, b := range active {
		<-b.done
	}
}
