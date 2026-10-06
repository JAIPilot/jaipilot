package jaipilot

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"reflect"
	"strings"
	"sync"
	"testing"
)

type acpClientFixture struct {
	mu    sync.Mutex
	rpc   *rpcConnection
	calls []rpcMessage
	reply func(rpcMessage) (object, error)
}

func (c *acpClientFixture) Write(data []byte) (int, error) {
	var m rpcMessage
	if err := json.Unmarshal(data, &m); err != nil {
		return 0, err
	}
	c.mu.Lock()
	c.calls = append(c.calls, m)
	c.mu.Unlock()
	if len(m.ID) > 0 {
		go func() {
			result, err := c.reply(m)
			response := rpcMessage{JSONRPC: "2.0", ID: m.ID, Result: json.RawMessage(jsonText(result))}
			if err != nil {
				response.Error = &rpcError{-32603, err.Error(), nil}
			}
			c.rpc.mu.Lock()
			ch := c.rpc.pending[string(m.ID)]
			c.rpc.mu.Unlock()
			if ch != nil {
				ch <- response
			}
		}()
	}
	return len(data), nil
}
func (c *acpClientFixture) methods() []string {
	c.mu.Lock()
	defer c.mu.Unlock()
	out := []string{}
	for _, m := range c.calls {
		if len(m.ID) > 0 {
			out = append(out, m.Method)
		}
	}
	return out
}
func acpFixture(t *testing.T) (*acpAgent, *acpClientFixture, string) {
	t.Helper()
	root, _, _ := fixture(t)
	client := &acpClientFixture{}
	client.reply = func(m rpcMessage) (object, error) {
		switch m.Method {
		case "session/request_permission":
			return object{"outcome": object{"outcome": "selected", "optionId": "allow"}}, nil
		case "terminal/create":
			return object{"terminalId": "terminal"}, nil
		case "terminal/wait_for_exit":
			return object{"exitCode": 0}, nil
		case "terminal/output":
			return object{"output": "tests passed", "truncated": false}, nil
		default:
			return object{}, nil
		}
	}
	client.rpc = newRPC(context.Background(), client)
	t.Cleanup(client.rpc.cancel)
	a := newACP(client.rpc)
	a.token = func(context.Context) (string, error) { return "test-token", nil }
	a.login = func(context.Context) (string, error) { return "test@example.com", nil }
	return a, client, root
}
func acpCall(t *testing.T, a *acpAgent, method string, p object) (object, error) {
	t.Helper()
	r, err := a.handle(context.Background(), method, json.RawMessage(jsonText(p)))
	return obj(r), err
}
func acpSessionFixture(t *testing.T, a *acpAgent, root string) string {
	t.Helper()
	if _, err := acpCall(t, a, "initialize", object{"protocolVersion": 1, "clientCapabilities": object{"terminal": true}}); err != nil {
		t.Fatal(err)
	}
	r, err := acpCall(t, a, "session/new", object{"cwd": root, "mcpServers": []any{}})
	if err != nil {
		t.Fatal(err)
	}
	return str(r["sessionId"])
}

func TestACPAuthenticationCapabilitiesAndOutcomes(t *testing.T) {
	a, _, root := acpFixture(t)
	a.token = func(context.Context) (string, error) { return "", errors.New("Sign in first") }
	init, err := acpCall(t, a, "initialize", object{"protocolVersion": 1, "clientCapabilities": object{"terminal": true}})
	if err != nil || str(obj(init["agentInfo"])["name"]) != "jaipilot" {
		t.Fatal(init, err)
	}
	_, err = acpCall(t, a, "session/new", object{"cwd": root})
	e, ok := err.(*rpcError)
	if !ok || e.Code != -32000 {
		t.Fatalf("Auth gate: %v", err)
	}
	a.token = func(context.Context) (string, error) { return "token", nil }
	id := acpSessionFixture(t, a, root)
	for _, o := range outcomes {
		r, err := acpCall(t, a, "session/set_config_option", object{"sessionId": id, "configId": "outcome", "value": o["id"]})
		if err != nil || r["configOptions"].([]object)[0]["currentValue"] != o["id"] {
			t.Fatal(r, err)
		}
	}
	if len(outcomes) != 6 {
		t.Fatal("Outcome contract changed")
	}
	for _, p := range []object{{"cwd": "relative"}, {"cwd": root, "mcpServers": []any{object{"name": "untrusted"}}}} {
		if _, err := acpCall(t, a, "session/new", p); err == nil {
			t.Fatal("Accepted unsupported session", p)
		}
	}
	a.terminal = false
	if _, err := acpCall(t, a, "session/new", object{"cwd": root}); err == nil {
		t.Fatal("Accepted client without terminal support")
	}
}

func TestACPUsesApprovedIDETerminalAndProtocolFour(t *testing.T) {
	a, client, root := acpFixture(t)
	id := acpSessionFixture(t, a, root)
	ran := false
	a.run = func(ctx context.Context, r, w string, s Scope, o workflowOptions) (object, error) {
		ran = true
		if o.Protocol != 4 || w != "fix_tests" || !reflect.DeepEqual(s.SelectedPaths, []string{"src/main/java/com/acme/Order.java"}) {
			t.Errorf("Changed bounded workflow: %d %s %v", o.Protocol, w, s)
		}
		result, err := o.Execute(ctx, r, object{"command": "echo tests", "purpose": "verify", "timeoutSeconds": 10})
		if err != nil || result.ExitCode != 0 || result.Output != "tests passed" {
			t.Error(result, err)
		}
		return resultFixture(), err
	}
	r, err := acpCall(t, a, "session/prompt", object{"sessionId": id, "prompt": []any{object{"type": "text", "text": "/fix_tests\t--class com.acme.Order"}}})
	if err != nil || r["stopReason"] != "end_turn" || !ran {
		t.Fatal(r, err)
	}
	want := []string{"session/request_permission", "session/request_permission", "terminal/create", "terminal/wait_for_exit", "terminal/output", "terminal/release"}
	if !reflect.DeepEqual(client.methods(), want) {
		t.Fatal(client.methods())
	}
}

func TestACPRejectingPermissionStopsBeforeModelOrCommand(t *testing.T) {
	for _, reject := range []int{1, 2} {
		t.Run(string(rune('0'+reject)), func(t *testing.T) {
			a, client, root := acpFixture(t)
			id := acpSessionFixture(t, a, root)
			original := client.reply
			count := 0
			ran := false
			client.reply = func(m rpcMessage) (object, error) {
				if m.Method == "session/request_permission" {
					count++
					if count == reject {
						return object{"outcome": object{"outcome": "selected", "optionId": "reject"}}, nil
					}
				}
				return original(m)
			}
			a.run = func(ctx context.Context, r, _ string, _ Scope, o workflowOptions) (object, error) {
				ran = true
				_, err := o.Execute(ctx, r, object{"command": "must never execute", "timeoutSeconds": 1})
				return nil, err
			}
			r, err := acpCall(t, a, "session/prompt", object{"sessionId": id, "prompt": []any{object{"type": "text", "text": "--all"}}})
			if err != nil || r["stopReason"] != "cancelled" || ran != (reject == 2) {
				t.Fatal(r, err, ran)
			}
			for _, method := range client.methods() {
				if method == "terminal/create" {
					t.Fatal("Executed rejected command")
				}
			}
		})
	}
}

func TestACPCancellationKillsAndReleasesTerminal(t *testing.T) {
	a, client, root := acpFixture(t)
	id := acpSessionFixture(t, a, root)
	waiting := make(chan struct{})
	original := client.reply
	client.reply = func(m rpcMessage) (object, error) {
		if m.Method == "terminal/wait_for_exit" {
			close(waiting)
			<-client.rpc.ctx.Done()
			return nil, context.Canceled
		}
		return original(m)
	}
	a.run = func(ctx context.Context, r, _ string, _ Scope, o workflowOptions) (object, error) {
		_, err := o.Execute(ctx, r, object{"command": "long-running-tests", "timeoutSeconds": 60})
		return nil, err
	}
	done := make(chan object, 1)
	go func() {
		r, _ := acpCall(t, a, "session/prompt", object{"sessionId": id, "prompt": []any{object{"type": "text", "text": "--all"}}})
		done <- r
	}()
	<-waiting
	_, _ = acpCall(t, a, "session/cancel", object{"sessionId": id})
	r := <-done
	if r["stopReason"] != "cancelled" {
		t.Fatal(r)
	}
	methods := strings.Join(client.methods(), ",")
	if !strings.Contains(methods, "terminal/kill,terminal/release") {
		t.Fatal(methods)
	}
}

func TestRPCRealFramingConcurrentResponsesAndShutdown(t *testing.T) {
	inputReader, inputWriter := io.Pipe()
	outputReader, outputWriter := io.Pipe()
	rpc := newRPC(context.Background(), outputWriter)
	done := make(chan error, 1)
	go func() {
		done <- rpc.serve(inputReader, func(_ context.Context, method string, _ json.RawMessage) (any, error) {
			return object{"method": method}, nil
		})
		outputWriter.Close()
	}()
	go func() {
		fmtData := `{"jsonrpc":"2.0","id":1,"method":"one"}` + "\n" + `{"jsonrpc":"2.0","id":2,"method":"two"}` + "\n"
		_, _ = io.WriteString(inputWriter, fmtData)
	}()
	decoder := json.NewDecoder(outputReader)
	seen := map[string]bool{}
	for i := 0; i < 2; i++ {
		var m rpcMessage
		if err := decoder.Decode(&m); err != nil {
			t.Fatal(err)
		}
		seen[string(m.ID)] = true
	}
	if !seen["1"] || !seen["2"] {
		t.Fatal(seen)
	}
	inputWriter.Close()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}
