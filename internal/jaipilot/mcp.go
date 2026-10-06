package jaipilot

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
)

//go:embed mcp_contract.json
var mcpContractBytes []byte
var mcpContract struct {
	Instructions string   `json:"instructions"`
	Tools        []object `json:"tools"`
}

func init() {
	if err := json.Unmarshal(mcpContractBytes, &mcpContract); err != nil {
		panic(err)
	}
}

type mcpHandler struct {
	jobs        *Jobs
	mu          sync.Mutex
	initialized bool
}

func invalidParams(err error) *rpcError {
	return &rpcError{-32602, "Invalid arguments: " + err.Error(), nil}
}
func (m *mcpHandler) handle(ctx context.Context, method string, params json.RawMessage) (any, error) {
	result, err := m.dispatch(ctx, method, params)
	if method == "tools/call" {
		if e, ok := err.(*rpcError); ok && e.Code == -32602 {
			return object{"isError": true, "content": []object{{"type": "text", "text": e.Message}}}, nil
		}
	}
	return result, err
}

func (m *mcpHandler) dispatch(ctx context.Context, method string, params json.RawMessage) (any, error) {
	if method == "initialize" {
		var p object
		if err := json.Unmarshal(params, &p); err != nil {
			return nil, invalidParams(err)
		}
		version := str(p["protocolVersion"])
		switch version {
		case "2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25":
		default:
			version = "2025-11-25"
		}
		m.mu.Lock()
		m.initialized = true
		m.mu.Unlock()
		return object{"protocolVersion": version, "capabilities": object{"tools": object{}}, "serverInfo": object{"name": "jaipilot", "version": Version}, "instructions": mcpContract.Instructions}, nil
	}
	if method == "ping" || method == "notifications/initialized" {
		return object{}, nil
	}
	m.mu.Lock()
	initialized := m.initialized
	m.mu.Unlock()
	if !initialized {
		return nil, &rpcError{-32000, "Initialize first", nil}
	}
	if method == "tools/list" {
		return object{"tools": mcpContract.Tools}, nil
	}
	if method != "tools/call" {
		if strings.HasPrefix(method, "notifications/") {
			return nil, nil
		}
		return nil, &rpcError{-32601, "Method not found", nil}
	}
	var call struct {
		Name      string          `json:"name"`
		Arguments json.RawMessage `json:"arguments"`
		Meta      object          `json:"_meta,omitempty"`
	}
	if err := decodeJSON(bytes.NewReader(params), &call); err != nil {
		return nil, invalidParams(err)
	}
	var view JobView
	var err error
	switch call.Name {
	case "lock_behavior":
		var in LockInput
		in.TimeoutSeconds = 3600
		if err = decodeJSON(bytes.NewReader(call.Arguments), &in); err != nil {
			return nil, invalidParams(err)
		}
		if strings.TrimSpace(in.TestCommand) == "" || len(in.TestPaths) == 0 || in.TimeoutSeconds < 1 || in.TimeoutSeconds > 7200 {
			return nil, invalidParams(errors.New("test_command, test_paths and timeout_seconds are required and must be valid"))
		}
		for _, p := range in.TestPaths {
			if strings.TrimSpace(p) == "" {
				return nil, invalidParams(errors.New("test_paths cannot be empty"))
			}
		}
		if _, err := resolveScope(ctx, m.jobs.root, in.Scope); err != nil {
			return nil, invalidParams(err)
		}
		view, err = m.jobs.lock(in)
	case "verify_behavior":
		var in struct {
			ID string `json:"baseline_id"`
		}
		if err = decodeJSON(bytes.NewReader(call.Arguments), &in); err != nil {
			return nil, invalidParams(err)
		}
		if !uuidPattern.MatchString(in.ID) {
			return nil, invalidParams(errors.New("Invalid baseline_id"))
		}
		view, err = m.jobs.verify(in.ID)
	case "get_job_status", "cancel_job":
		var in struct {
			ID   string `json:"job_id"`
			Wait int    `json:"wait_seconds,omitempty"`
		}
		in.Wait = 10
		if err = decodeJSON(bytes.NewReader(call.Arguments), &in); err != nil {
			return nil, invalidParams(err)
		}
		if !uuidPattern.MatchString(in.ID) || in.Wait < 0 || in.Wait > 30 {
			return nil, invalidParams(errors.New("Invalid job_id or wait_seconds"))
		}
		if call.Name == "cancel_job" {
			var strict struct {
				ID string `json:"job_id"`
			}
			if err = decodeJSON(bytes.NewReader(call.Arguments), &strict); err != nil {
				return nil, invalidParams(err)
			}
			view, err = m.jobs.cancel(in.ID)
		} else {
			view, err = m.jobs.status(ctx, in.ID, in.Wait)
		}
	default:
		return nil, &rpcError{-32602, fmt.Sprintf("Unknown tool: %s", call.Name), nil}
	}
	if err != nil {
		return object{"isError": true, "content": []object{{"type": "text", "text": err.Error()}}}, nil
	}
	return object{"content": []object{{"type": "text", "text": jsonText(view)}}, "structuredContent": view}, nil
}
