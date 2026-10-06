package jaipilot

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"
)

const endpoint = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/invoke-testing-agent"
const maxOutput = 80000

type CommandResult struct {
	Command    string `json:"command"`
	ExitCode   int    `json:"exitCode"`
	Output     string `json:"output"`
	Truncated  bool   `json:"truncated"`
	TimedOut   bool   `json:"timedOut"`
	DurationMS int64  `json:"durationMs"`
}
type tailWriter struct {
	mu        sync.Mutex
	data      []byte
	truncated bool
}

func (w *tailWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	w.data = append(w.data, p...)
	if len(w.data) > maxOutput {
		w.data = append([]byte{}, w.data[len(w.data)-maxOutput:]...)
		w.truncated = true
	}
	return len(p), nil
}

type Execute func(context.Context, string, object) (CommandResult, error)

func validateCommand(in object) (string, int, error) {
	command := str(in["command"])
	seconds := num(in["timeoutSeconds"])
	n, ok := in["timeoutSeconds"].(float64)
	if !ok {
		if i, yes := in["timeoutSeconds"].(int); yes {
			n = float64(i)
			ok = true
		}
	}
	if strings.TrimSpace(command) == "" || strings.ContainsRune(command, 0) || !ok || n != float64(seconds) || seconds < 1 || seconds > 7200 {
		return "", 0, errors.New("Invalid local command request")
	}
	return command, seconds, nil
}
func runCommand(ctx context.Context, root string, in object) (CommandResult, error) {
	command, seconds, err := validateCommand(in)
	if err != nil {
		return CommandResult{}, err
	}
	if err := ctx.Err(); err != nil {
		return CommandResult{}, err
	}
	purpose := str(in["purpose"])
	if purpose == "" {
		purpose = "task"
	}
	fmt.Fprintf(os.Stderr, "JAIPilot: %s…\n", purpose)
	started := time.Now()
	cmd := shellCommand(root, command)
	out, stderr := &tailWriter{}, &tailWriter{}
	cmd.Stdout = out
	cmd.Stderr = stderr
	cmd.WaitDelay = 3 * time.Second
	if err := cmd.Start(); err != nil {
		return CommandResult{}, err
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	timer := time.NewTimer(time.Duration(seconds) * time.Second)
	defer timer.Stop()
	timedOut := false
	cancelled := false
	select {
	case err = <-done:
	case <-ctx.Done():
		cancelled = true
	case <-timer.C:
		timedOut = true
	}
	if cancelled || timedOut {
		terminate(cmd, false)
		escalation := time.NewTimer(2 * time.Second)
		select {
		case err = <-done:
			if !escalation.Stop() {
				<-escalation.C
			}
		case <-escalation.C:
			terminate(cmd, true)
			err = <-done
		}
		terminate(cmd, true)
	}
	if cancelled {
		return CommandResult{}, ctx.Err()
	}
	code := 0
	if err != nil {
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			code = exit.ExitCode()
			if code < 0 {
				code = 1
			}
		} else {
			return CommandResult{}, err
		}
	}
	if timedOut {
		code = 124
	}
	output := string(out.data) + "\n" + string(stderr.data)
	output = strings.TrimSpace(output)
	truncated := out.truncated || stderr.truncated || len(output) > maxOutput
	if len(output) > maxOutput {
		output = "\n… earlier command output omitted …\n" + output[len(output)-maxOutput:]
	}
	return CommandResult{command, code, output, truncated, timedOut, time.Since(started).Milliseconds()}, nil
}

type workflowOptions struct {
	Protocol    int
	UserRequest string
	Report      func(string)
	Execute     Execute
}

func serviceRequest(ctx context.Context, method string, body any) (object, error) {
	token, err := bearerToken(ctx)
	if err != nil {
		return nil, err
	}
	requestID := str(obj(body)["requestId"])
	started := time.Now()
	failures := 0
	waiting := false
	for {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		if time.Since(started) > 15*time.Minute {
			return nil, fmt.Errorf("JAIPilot request timed out (%s)", requestID)
		}
		attempt, cancel := context.WithTimeout(ctx, 145*time.Second)
		var reader io.Reader
		if body != nil {
			reader = strings.NewReader(jsonText(body))
		}
		req, err := http.NewRequestWithContext(attempt, method, endpoint, reader)
		if err != nil {
			cancel()
			return nil, err
		}
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		response, err := http.DefaultClient.Do(req)
		if err != nil {
			cancel()
			if ctx.Err() != nil {
				return nil, ctx.Err()
			}
			failures++
			if err := pause(ctx, retryDelay(failures)); err != nil {
				return nil, err
			}
			continue
		}
		var value object
		err = json.NewDecoder(io.LimitReader(response.Body, 32<<20)).Decode(&value)
		response.Body.Close()
		cancel()
		if err != nil {
			value = object{"error": fmt.Sprintf("JAIPilot returned HTTP %d", response.StatusCode)}
		}
		pending := response.StatusCode == 202
		transient := value["retryable"] != false && (response.StatusCode == 408 || response.StatusCode == 425 || response.StatusCode == 429 || response.StatusCode >= 500)
		if pending || transient {
			if !waiting {
				fmt.Fprintf(os.Stderr, "JAIPilot: waiting for the service (HTTP %d)…\n", response.StatusCode)
				waiting = true
			}
			delay := 3 * time.Second
			if transient {
				failures++
				delay = retryDelay(failures)
			}
			if retry, err := strconv.ParseFloat(response.Header.Get("Retry-After"), 64); err == nil && retry >= 0 {
				delay = time.Duration(retry * float64(time.Second))
				if delay > 30*time.Second {
					delay = 30 * time.Second
				}
			}
			if err := pause(ctx, delay); err != nil {
				return nil, err
			}
			continue
		}
		if response.StatusCode < 200 || response.StatusCode >= 300 {
			return nil, fmt.Errorf("%s (request %s)", str(value["error"]), requestID)
		}
		if err != nil {
			return nil, errors.New("JAIPilot returned invalid JSON")
		}
		return value, nil
	}
}
func retryDelay(n int) time.Duration {
	if n > 5 {
		n = 5
	}
	delay := 3 * time.Second * time.Duration(1<<max(n-1, 0))
	if delay > 30*time.Second {
		return 30 * time.Second
	}
	return delay
}
func pause(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
func workflows(ctx context.Context) ([]object, error) {
	v, err := serviceRequest(ctx, "GET", nil)
	if err != nil {
		return nil, err
	}
	if num(v["protocolVersion"]) != 6 || v["workflows"] == nil {
		return nil, errors.New("JAIPilot returned an incompatible workflow catalog")
	}
	out := []object{}
	for _, item := range list(v["workflows"]) {
		out = append(out, obj(item))
	}
	return out, nil
}
func runWorkflow(ctx context.Context, root, workflow string, scope Scope, options workflowOptions) (object, error) {
	started := time.Now()
	catalog, err := workflows(ctx)
	if err != nil {
		return nil, err
	}
	found := false
	for _, item := range catalog {
		if str(item["id"]) == workflow {
			found = true
		}
	}
	if !found {
		return nil, fmt.Errorf("Unknown workflow: %s. Run `jaipilot workflows` to list them", workflow)
	}
	project, err := projectContext(ctx, root, scope)
	if err != nil {
		return nil, err
	}
	report := options.Report
	if report == nil {
		report = func(s string) { fmt.Fprintln(os.Stderr, s) }
	}
	execute := options.Execute
	if execute == nil {
		execute = runCommand
	}
	protocol := options.Protocol
	if protocol == 0 {
		protocol = 6
	}
	state := object{"workflow": workflow, "revision": 0, "project": project, "selections": scope.Selections, "trigger": object{"projectWide": scope.ProjectWide}, "jobId": uuid()}
	if options.UserRequest != "" {
		state["userRequest"] = options.UserRequest
	}
	history := []object{}
	for turn := 0; turn < 300; turn++ {
		reply, err := serviceRequest(ctx, "POST", object{"protocolVersion": protocol, "requestId": uuid(), "workflow": workflow, "context": state, "history": history})
		if err != nil {
			return nil, err
		}
		if reply["continue"] == true {
			progress := obj(reply["parallel"])
			if num(progress["totalClasses"]) > 0 {
				report(fmt.Sprintf("JAIPilot: %d/%d classes complete", num(progress["completedClasses"]), num(progress["totalClasses"])))
			}
			continue
		}
		content := list(reply["content"])
		if content == nil {
			return nil, errors.New("JAIPilot returned an invalid agent turn")
		}
		results := []object{}
		for _, item := range content {
			block := obj(item)
			if str(block["type"]) == "text" && strings.TrimSpace(str(block["text"])) != "" {
				report(strings.TrimSpace(str(block["text"])))
			}
		}
		for _, item := range content {
			block := obj(item)
			if str(block["type"]) != "tool_use" {
				continue
			}
			input := obj(block["input"])
			if str(block["name"]) == "finish" {
				status := str(input["status"])
				if status != "complete" && status != "blocked" {
					return nil, errors.New("JAIPilot returned an invalid result")
				}
				gitStatus, err := git(ctx, root, "status", "--short")
				if err != nil {
					return nil, err
				}
				input["workflow"] = workflow
				input["scope"] = scope
				input["gitStatus"] = gitStatus
				input["durationMs"] = time.Since(started).Milliseconds()
				return input, nil
			}
			if str(block["id"]) == "" || str(block["name"]) == "" || block["input"] == nil {
				return nil, errors.New("JAIPilot returned an invalid tool request")
			}
			var value any
			var failure error
			if str(block["name"]) != "run_command" {
				failure = fmt.Errorf("Unsupported local tool: %s", str(block["name"]))
			} else {
				value, failure = execute(ctx, root, input)
			}
			result := object{"type": "tool_result", "tool_use_id": str(block["id"])}
			if ctx.Err() != nil {
				return nil, ctx.Err()
			}
			if failure != nil {
				value = object{"error": failure.Error()}
				result["is_error"] = true
			}
			result["content"] = jsonText(value)
			results = append(results, result)
		}
		if len(results) == 0 {
			return nil, errors.New("JAIPilot did not request a local tool")
		}
		history = append(history, object{"role": "assistant", "content": content}, object{"role": "user", "content": results})
	}
	return nil, errors.New("JAIPilot stopped after 300 agent turns")
}
