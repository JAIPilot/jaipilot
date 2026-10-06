package jaipilot

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"time"
	"unicode"
)

var outcomes = []object{
	{"id": "generate_tests", "name": "Generate tests", "description": "Create focused Java tests and verify them."},
	{"id": "improve_coverage", "name": "Improve coverage", "description": "Measure coverage and close meaningful gaps."},
	{"id": "fix_tests", "name": "Fix failing tests", "description": "Reproduce failures, repair tests, and rerun the suite."},
	{"id": "stabilize_flaky_tests", "name": "Stabilize flaky tests", "description": "Reproduce nondeterminism and verify the fix repeatedly."},
	{"id": "test_current_changes", "name": "Test current changes", "description": "Test behavior affected by the current Git diff."},
	{"id": "lock_behavior", "name": "Lock existing behavior", "description": "Capture observable behavior before refactoring."},
}
var authMethod = object{"id": "jaipilot-login", "name": "Sign in to JAIPilot", "description": "Open your browser and use your existing JAIPilot account, subscription, and credits.", "type": "agent"}

const scopeHint = "--class com.acme.OrderService | --path src/main/java | --selection File.java:42-88 | --all"

func acpHelp() string {
	lines := []string{"Choose a Java testing outcome, then supply exactly one scope.", ""}
	for _, o := range outcomes {
		lines = append(lines, "/"+str(o["id"])+" — "+str(o["description"]))
	}
	return strings.Join(lines, "\n") + "\n\nExample: /improve_coverage --class com.acme.OrderService\n\nScope: " + scopeHint + "\nYou can repeat --class, --path, or --selection. An attached file link can supply the scope. Save editor changes before running. Commands and their output appear in the IDE; review the complete diff afterward.\n\nJAIPilot uses its managed model service and your account credits. Approved command output and project metadata are sent to that service. No remote workspace or bulk source upload is used by this adapter. Custom MCP servers, images, audio, and persistent sessions are not supported."
}
func scopeArguments(text string) ([]string, error) {
	out := []string{}
	current := strings.Builder{}
	var quote rune
	started := false
	for _, char := range strings.TrimSpace(text) {
		if quote != 0 {
			if char == quote {
				quote = 0
			} else {
				current.WriteRune(char)
			}
		} else if (char == '\'' || char == '"') && !started {
			quote = char
			started = true
		} else if unicode.IsSpace(char) {
			if started {
				out = append(out, current.String())
			}
			current.Reset()
			started = false
		} else {
			current.WriteRune(char)
			started = true
		}
	}
	if quote != 0 {
		return nil, errors.New("Close the quoted scope argument.")
	}
	if started {
		out = append(out, current.String())
	}
	return out, nil
}
func promptScope(ctx context.Context, root, text string, prompt []any, cwd string) (Scope, error) {
	args, err := scopeArguments(text)
	if err != nil {
		return Scope{}, err
	}
	in := ScopeInput{}
	for i := 0; i < len(args); i++ {
		arg := args[i]
		if arg == "--all" {
			in.All = true
			continue
		}
		if arg == "--class" || arg == "--path" || arg == "--selection" {
			i++
			if i >= len(args) || args[i] == "" || strings.HasPrefix(args[i], "--") {
				return Scope{}, fmt.Errorf("Missing value for %s", arg)
			}
			value := args[i]
			switch arg {
			case "--class":
				in.Classes = append(in.Classes, value)
			case "--path":
				in.Paths = append(in.Paths, absolute(cwd, value))
			case "--selection":
				m := selectionPattern.FindStringSubmatch(value)
				if m == nil {
					return Scope{}, errors.New("Use --selection path:start-end")
				}
				path := absolute(cwd, m[1]) + ":" + m[2]
				if m[3] != "" {
					path += "-" + m[3]
				}
				in.Selections = append(in.Selections, path)
			}
		} else if strings.HasPrefix(arg, "--") {
			return Scope{}, fmt.Errorf("Unknown scope option: %s", arg)
		}
	}
	if !in.All && len(in.Paths) == 0 && len(in.Classes) == 0 && len(in.Selections) == 0 {
		for _, item := range prompt {
			block := obj(item)
			uri := ""
			if str(block["type"]) == "resource_link" {
				uri = str(block["uri"])
			} else if str(block["type"]) == "resource" {
				uri = str(obj(block["resource"])["uri"])
			}
			if strings.HasPrefix(uri, "file:") {
				u, err := url.Parse(uri)
				if err != nil {
					return Scope{}, err
				}
				if u.Host != "" && u.Host != "localhost" {
					return Scope{}, errors.New("File links must refer to this computer")
				}
				path, err := url.PathUnescape(u.EscapedPath())
				if err != nil {
					return Scope{}, err
				}
				if runtime.GOOS == "windows" && len(path) > 2 && path[0] == '/' && path[2] == ':' {
					path = path[1:]
				}
				in.Paths = append(in.Paths, filepath.FromSlash(path))
			}
		}
	}
	return resolveScope(ctx, root, in)
}
func configOptions(mode string) []object {
	choices := []object{}
	for _, o := range outcomes {
		choices = append(choices, object{"value": o["id"], "name": o["name"], "description": o["description"]})
	}
	return []object{{"id": "outcome", "name": "Java testing outcome", "category": "mode", "type": "select", "currentValue": mode, "options": choices}}
}
func outcome(id string) (object, error) {
	for _, o := range outcomes {
		if str(o["id"]) == id {
			return o, nil
		}
	}
	return nil, &rpcError{-32602, "Unknown testing outcome: " + id, nil}
}

type acpSession struct {
	root, cwd, mode string
	cancel          context.CancelFunc
}
type acpAgent struct {
	rpc                   *rpcConnection
	mu                    sync.Mutex
	wg                    sync.WaitGroup
	stopped               bool
	sessions              map[string]*acpSession
	running               map[string]bool
	initialized, terminal bool
	run                   Workflow
	token                 func(context.Context) (string, error)
	login                 func(context.Context) (string, error)
}

func newACP(r *rpcConnection) *acpAgent {
	return &acpAgent{rpc: r, sessions: map[string]*acpSession{}, running: map[string]bool{}, run: runWorkflow, token: bearerToken, login: login}
}
func (a *acpAgent) stop() {
	a.mu.Lock()
	a.stopped = true
	for _, session := range a.sessions {
		if session.cancel != nil {
			session.cancel()
		}
	}
	a.mu.Unlock()
	a.wg.Wait()
}
func (a *acpAgent) session(id string) (*acpSession, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	s := a.sessions[id]
	if s == nil {
		return nil, &rpcError{-32602, "Unknown session", nil}
	}
	return s, nil
}
func (a *acpAgent) requireAuth(ctx context.Context) error {
	if _, err := a.token(ctx); err != nil {
		return &rpcError{-32000, err.Error(), object{"authMethods": []object{authMethod}}}
	}
	return nil
}
func (a *acpAgent) update(id string, update object) error {
	return a.rpc.notify("session/update", object{"sessionId": id, "update": update})
}
func (a *acpAgent) setMode(id, mode string) (any, error) {
	if _, err := outcome(mode); err != nil {
		return nil, err
	}
	s, err := a.session(id)
	if err != nil {
		return nil, err
	}
	a.mu.Lock()
	s.mode = mode
	a.mu.Unlock()
	if err := a.update(id, object{"sessionUpdate": "current_mode_update", "currentModeId": mode}); err != nil {
		return nil, err
	}
	if err := a.update(id, object{"sessionUpdate": "config_option_update", "configOptions": configOptions(mode)}); err != nil {
		return nil, err
	}
	return object{}, nil
}
func (a *acpAgent) handle(ctx context.Context, method string, params json.RawMessage) (any, error) {
	var p object
	if len(params) > 0 {
		if err := json.Unmarshal(params, &p); err != nil {
			return nil, invalidParams(err)
		}
	}
	id := str(p["sessionId"])
	switch method {
	case "initialize":
		a.mu.Lock()
		a.initialized = true
		a.terminal = obj(p["clientCapabilities"])["terminal"] == true
		a.mu.Unlock()
		return object{"protocolVersion": 1, "agentInfo": object{"name": "jaipilot", "title": "JAIPilot", "version": Version}, "agentCapabilities": object{"loadSession": false, "promptCapabilities": object{"embeddedContext": true}, "auth": object{"logout": object{}}}, "authMethods": []object{authMethod}}, nil
	case "authenticate":
		a.mu.Lock()
		initialized := a.initialized
		a.mu.Unlock()
		if !initialized || str(p["methodId"]) != "jaipilot-login" {
			return nil, invalidParams(errors.New("Unknown authentication method"))
		}
		_, err := a.login(ctx)
		return object{}, err
	case "logout":
		a.stop()
		err := logout()
		a.mu.Lock()
		a.stopped = false
		a.mu.Unlock()
		return object{}, err
	case "session/new":
		a.mu.Lock()
		initialized, terminal := a.initialized, a.terminal
		a.mu.Unlock()
		if !initialized {
			return nil, invalidParams(errors.New("Initialize first"))
		}
		cwd := str(p["cwd"])
		if !filepath.IsAbs(cwd) {
			return nil, invalidParams(errors.New("cwd must be absolute"))
		}
		if len(list(p["mcpServers"])) > 0 {
			return nil, invalidParams(errors.New("JAIPilot's bounded testing adapter does not use custom MCP servers. Disable Pass custom MCP servers and Pass IntelliJ MCP server for this agent."))
		}
		if !terminal {
			return nil, invalidParams(errors.New("This agent requires ACP client terminal support for approved local commands"))
		}
		if err := a.requireAuth(ctx); err != nil {
			return nil, err
		}
		root, err := repositoryRoot(ctx, cwd)
		if err != nil {
			return nil, err
		}
		cwd, err = real(cwd)
		if err != nil {
			return nil, err
		}
		id := uuid()
		mode := "generate_tests"
		a.mu.Lock()
		a.sessions[id] = &acpSession{root: root, cwd: cwd, mode: mode}
		a.mu.Unlock()
		commands := []object{}
		for _, o := range outcomes {
			commands = append(commands, object{"name": o["id"], "description": o["description"], "input": object{"hint": scopeHint}})
		}
		commands = append(commands, object{"name": "help", "description": "Show testing outcomes, scope syntax, and privacy details."})
		if err := a.update(id, object{"sessionUpdate": "available_commands_update", "availableCommands": commands}); err != nil {
			return nil, err
		}
		return object{"sessionId": id, "modes": object{"currentModeId": mode, "availableModes": outcomes}, "configOptions": configOptions(mode)}, nil
	case "session/set_mode":
		return a.setMode(id, str(p["modeId"]))
	case "session/set_config_option":
		if str(p["configId"]) != "outcome" {
			return nil, invalidParams(errors.New("Unknown config option"))
		}
		mode, ok := p["value"].(string)
		if !ok {
			return nil, invalidParams(errors.New("Outcome must be a string"))
		}
		if _, err := a.setMode(id, mode); err != nil {
			return nil, err
		}
		return object{"configOptions": configOptions(mode)}, nil
	case "session/cancel":
		a.mu.Lock()
		s := a.sessions[id]
		if s != nil && s.cancel != nil {
			s.cancel()
		}
		a.mu.Unlock()
		return nil, nil
	case "session/prompt":
		return a.prompt(ctx, id, list(p["prompt"]))
	default:
		return nil, &rpcError{-32601, "Method not found", nil}
	}
}
func (a *acpAgent) permission(ctx context.Context, id string, call object) error {
	update := object{"sessionUpdate": "tool_call"}
	for key, value := range call {
		update[key] = value
	}
	if err := a.update(id, update); err != nil {
		return err
	}
	response, err := a.rpc.request(ctx, "session/request_permission", object{"sessionId": id, "toolCall": call, "options": []object{{"optionId": "allow", "name": "Allow once", "kind": "allow_once"}, {"optionId": "reject", "name": "Reject", "kind": "reject_once"}}})
	if err != nil {
		return err
	}
	if ctx.Err() != nil {
		return ctx.Err()
	}
	selected := obj(response["outcome"])
	if str(selected["outcome"]) != "selected" || str(selected["optionId"]) != "allow" {
		_ = a.update(id, object{"sessionUpdate": "tool_call_update", "toolCallId": call["toolCallId"], "status": "failed"})
		a.mu.Lock()
		if s := a.sessions[id]; s != nil && s.cancel != nil {
			s.cancel()
		}
		a.mu.Unlock()
		return errors.New("Permission rejected; workflow stopped.")
	}
	return nil
}
func (a *acpAgent) command(ctx context.Context, id, root string, input object) (CommandResult, error) {
	command, seconds, err := validateCommand(input)
	if err != nil {
		return CommandResult{}, err
	}
	toolID := uuid()
	purpose := str(input["purpose"])
	if purpose == "" {
		purpose = "Run command"
	}
	call := object{"toolCallId": toolID, "title": purpose, "kind": "execute", "status": "pending", "rawInput": object{"command": command, "cwd": root, "timeoutSeconds": seconds}, "content": []object{{"type": "content", "content": object{"type": "text", "text": command}}}}
	if err := a.permission(ctx, id, call); err != nil {
		return CommandResult{}, err
	}
	if ctx.Err() != nil {
		return CommandResult{}, ctx.Err()
	}
	started := time.Now()
	shell := "/bin/sh"
	args := []string{"-lc", command}
	if runtime.GOOS == "windows" {
		shell = "cmd.exe"
		args = []string{"/d", "/s", "/c", command}
	}
	// Learn the terminal ID even if cancellation arrives while the client creates it.
	terminal, err := a.rpc.request(a.rpc.ctx, "terminal/create", object{"sessionId": id, "command": shell, "args": args, "cwd": root, "outputByteLimit": maxOutput})
	if err != nil {
		return CommandResult{}, err
	}
	terminalID := str(terminal["terminalId"])
	if terminalID == "" {
		return CommandResult{}, errors.New("Client returned no terminal ID")
	}
	params := object{"sessionId": id, "terminalId": terminalID}
	cleanup := func(method string) {
		cleanupCtx, cancel := context.WithTimeout(a.rpc.ctx, 20*time.Second)
		defer cancel()
		_, _ = a.rpc.request(cleanupCtx, method, params)
	}
	defer cleanup("terminal/release")
	if ctx.Err() != nil {
		cleanup("terminal/kill")
		return CommandResult{}, ctx.Err()
	}
	if err := a.update(id, object{"sessionUpdate": "tool_call_update", "toolCallId": toolID, "status": "in_progress", "content": []object{{"type": "terminal", "terminalId": terminalID}}}); err != nil {
		return CommandResult{}, err
	}
	deadline, cancel := context.WithTimeout(ctx, time.Duration(seconds)*time.Second)
	exit, err := a.rpc.request(deadline, "terminal/wait_for_exit", params)
	timedOut := errors.Is(deadline.Err(), context.DeadlineExceeded) && ctx.Err() == nil
	cancel()
	if err != nil {
		cleanup("terminal/kill")
		if !timedOut {
			return CommandResult{}, err
		}
	}
	output, err := a.rpc.request(ctx, "terminal/output", params)
	if err != nil {
		return CommandResult{}, err
	}
	code := 1
	if value, ok := exit["exitCode"]; ok {
		code = num(value)
	}
	if timedOut {
		code = 124
	}
	result := CommandResult{command, code, str(output["output"]), output["truncated"] == true, timedOut, time.Since(started).Milliseconds()}
	status := "completed"
	if code != 0 {
		status = "failed"
	}
	if err := a.update(id, object{"sessionUpdate": "tool_call_update", "toolCallId": toolID, "status": status, "rawOutput": result}); err != nil {
		return CommandResult{}, err
	}
	return result, nil
}
func (a *acpAgent) prompt(requestCtx context.Context, id string, prompt []any) (any, error) {
	s, err := a.session(id)
	if err != nil {
		return nil, err
	}
	a.mu.Lock()
	if a.stopped || s.cancel != nil || a.running[s.root] {
		a.mu.Unlock()
		return nil, invalidParams(errors.New("A testing workflow is already running in this repository. Cancel it or wait for completion."))
	}
	ctx, cancel := context.WithCancel(requestCtx)
	s.cancel = cancel
	a.wg.Add(1)
	a.running[s.root] = true
	mode := s.mode
	a.mu.Unlock()
	defer a.wg.Done()
	defer func() { cancel(); a.mu.Lock(); s.cancel = nil; delete(a.running, s.root); a.mu.Unlock() }()
	report := func(text string) {
		_ = a.update(id, object{"sessionUpdate": "agent_message_chunk", "content": object{"type": "text", "text": text + "\n\n"}})
	}
	action := func() error {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		textParts := []string{}
		hasLink := false
		for _, item := range prompt {
			block := obj(item)
			switch str(block["type"]) {
			case "text":
				textParts = append(textParts, str(block["text"]))
			case "resource", "resource_link":
				hasLink = true
			default:
				return errors.New("Use a text prompt and Java file links; images and audio are not supported.")
			}
		}
		text := strings.TrimSpace(strings.Join(textParts, "\n"))
		selectedID := mode
		input := text
		if strings.HasPrefix(text, "/") {
			parts := regexp.MustCompile(`\s+`).Split(text, 2)
			selectedID = strings.TrimPrefix(parts[0], "/")
			input = ""
			if len(parts) > 1 {
				input = parts[1]
			}
		}
		if selectedID == "help" || (text == "" && !hasLink) {
			report(acpHelp())
			return nil
		}
		selected, err := outcome(selectedID)
		if err != nil {
			return err
		}
		scope, err := promptScope(ctx, s.root, input, prompt, s.cwd)
		if err != nil {
			return err
		}
		if err := a.requireAuth(ctx); err != nil {
			return err
		}
		if _, err := a.setMode(id, selectedID); err != nil {
			return err
		}
		report(str(selected["name"]) + ": " + strings.Join(scope.SelectedPaths, ", ") + ". Using your existing build and test tools.")
		consentID := uuid()
		if err := a.permission(ctx, id, object{"toolCallId": consentID, "title": "Authorize the JAIPilot managed testing service", "kind": "fetch", "status": "pending", "content": []object{{"type": "content", "content": object{"type": "text", "text": "This run uses your JAIPilot account credits and sends selected paths, project metadata, and approved local command output to JAIPilot and its model provider. Commands can read source into their output. No remote workspace or bulk source archive is used. Review each command before approving it."}}}}); err != nil {
			return err
		}
		_ = a.update(id, object{"sessionUpdate": "tool_call_update", "toolCallId": consentID, "status": "completed"})
		result, err := a.run(ctx, s.root, selectedID, scope, workflowOptions{Protocol: 4, UserRequest: text, Report: report, Execute: func(ctx context.Context, root string, input object) (CommandResult, error) {
			return a.command(ctx, id, root, input)
		}})
		if err != nil {
			return err
		}
		if ctx.Err() != nil {
			return ctx.Err()
		}
		report(resultText(result))
		return nil
	}
	err = action()
	if ctx.Err() != nil {
		return object{"stopReason": "cancelled"}, nil
	}
	if e, ok := err.(*rpcError); ok && e.Code == -32000 {
		return nil, err
	}
	if err != nil {
		report("Blocked: " + err.Error() + "\n\n" + scopeHint)
	}
	return object{"stopReason": "end_turn"}, nil
}
func resultText(result object) string {
	status := "Blocked"
	if str(result["status"]) == "complete" {
		status = "Completed"
	}
	evidence := obj(result["verification"])
	lines := []string{}
	for key, value := range evidence {
		if value != nil && value != "" {
			text := str(value)
			if text == "" {
				text = jsonText(value)
			}
			lines = append(lines, "- "+key+": "+text)
		}
	}
	for _, key := range []string{"testFailures", "nextActions"} {
		if len(list(result[key])) > 0 {
			lines = append(lines, "- "+key+": "+jsonText(result[key]))
		}
	}
	gitStatus := str(result["gitStatus"])
	if gitStatus == "" {
		gitStatus = "No changes"
	}
	return fmt.Sprintf("%s: %s\n\n%s\n\nTime taken: %.1fs.\n\nGit status:\n```text\n%s\n```\nReview the complete diff in your IDE before accepting changes.", status, str(result["summary"]), strings.Join(lines, "\n"), float64(num(result["durationMs"]))/1000, gitStatus)
}
