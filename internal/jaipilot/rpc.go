package jaipilot

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"sync"
)

type rpcMessage struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id,omitempty"`
	Method  string          `json:"method,omitempty"`
	Params  json.RawMessage `json:"params,omitempty"`
	Result  json.RawMessage `json:"result,omitempty"`
	Error   *rpcError       `json:"error,omitempty"`
}
type rpcError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

func (e *rpcError) Error() string { return e.Message }

type rpcHandler func(context.Context, string, json.RawMessage) (any, error)
type rpcConnection struct {
	writer   io.Writer
	mu       sync.Mutex
	pending  map[string]chan rpcMessage
	requests map[string]context.CancelFunc
	serial   uint64
	wg       sync.WaitGroup
	ctx      context.Context
	cancel   context.CancelFunc
}

func newRPC(ctx context.Context, w io.Writer) *rpcConnection {
	ctx, cancel := context.WithCancel(ctx)
	return &rpcConnection{writer: w, pending: map[string]chan rpcMessage{}, requests: map[string]context.CancelFunc{}, ctx: ctx, cancel: cancel}
}
func (r *rpcConnection) send(v any) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	return json.NewEncoder(r.writer).Encode(v)
}
func (r *rpcConnection) notify(method string, params any) error {
	return r.send(object{"jsonrpc": "2.0", "method": method, "params": params})
}
func (r *rpcConnection) request(ctx context.Context, method string, params any) (object, error) {
	r.mu.Lock()
	r.serial++
	id := fmt.Sprintf("jaipilot-%d", r.serial)
	key := jsonText(id)
	ch := make(chan rpcMessage, 1)
	r.pending[key] = ch
	r.mu.Unlock()
	defer func() { r.mu.Lock(); delete(r.pending, key); r.mu.Unlock() }()
	if err := r.send(object{"jsonrpc": "2.0", "id": id, "method": method, "params": params}); err != nil {
		return nil, err
	}
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	case <-r.ctx.Done():
		return nil, r.ctx.Err()
	case response := <-ch:
		if response.Error != nil {
			return nil, response.Error
		}
		var result object
		if err := json.Unmarshal(response.Result, &result); err != nil {
			return nil, err
		}
		return result, nil
	}
}
func (r *rpcConnection) serve(reader io.Reader, handler rpcHandler) error {
	scanner := bufio.NewScanner(reader)
	scanner.Buffer(make([]byte, 4096), 16<<20)
	for scanner.Scan() {
		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}
		var m rpcMessage
		if json.Unmarshal(line, &m) != nil {
			_ = r.send(object{"jsonrpc": "2.0", "id": nil, "error": rpcError{-32700, "Invalid JSON", nil}})
			continue
		}
		if m.JSONRPC != "2.0" {
			_ = r.send(object{"jsonrpc": "2.0", "id": json.RawMessage(m.ID), "error": rpcError{-32600, "Invalid JSON-RPC request", nil}})
			continue
		}
		if m.Method == "" {
			r.mu.Lock()
			ch := r.pending[string(m.ID)]
			r.mu.Unlock()
			if ch != nil {
				select {
				case ch <- m:
				default:
				}
			}
			continue
		}
		if m.Method == "notifications/cancelled" || m.Method == "$/cancel_request" {
			var p object
			_ = json.Unmarshal(m.Params, &p)
			r.mu.Lock()
			cancel := r.requests[jsonText(p["requestId"])]
			r.mu.Unlock()
			if cancel != nil {
				cancel()
			}
			continue
		}
		ctx, cancel := context.WithCancel(r.ctx)
		key := string(m.ID)
		if len(m.ID) > 0 {
			r.mu.Lock()
			if _, duplicate := r.requests[key]; duplicate {
				r.mu.Unlock()
				cancel()
				_ = r.send(object{"jsonrpc": "2.0", "id": m.ID, "error": rpcError{-32600, "Duplicate active request ID", nil}})
				continue
			}
			r.requests[key] = cancel
			r.mu.Unlock()
		}
		r.wg.Add(1)
		go func(m rpcMessage) {
			defer r.wg.Done()
			defer cancel()
			defer func() { r.mu.Lock(); delete(r.requests, string(m.ID)); r.mu.Unlock() }()
			result, err := handler(ctx, m.Method, m.Params)
			if len(m.ID) == 0 {
				return
			}
			response := object{"jsonrpc": "2.0", "id": m.ID}
			if err != nil {
				e, ok := err.(*rpcError)
				if !ok {
					e = &rpcError{-32603, err.Error(), nil}
				}
				response["error"] = e
			} else {
				if result == nil {
					result = object{}
				}
				response["result"] = result
			}
			_ = r.send(response)
		}(m)
	}
	r.cancel()
	r.wg.Wait()
	return scanner.Err()
}
