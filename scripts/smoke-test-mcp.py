#!/usr/bin/env python3
"""Black-box initialize + tools/list smoke for JAIPilot's stdio MCP server."""

from __future__ import annotations

import json
import os
import selectors
import subprocess
import sys
import time


EXPECTED_TOOLS = {
    "jaipilot_inspect",
    "jaipilot_snapshot",
    "jaipilot_quality",
    "jaipilot_rewrite",
    "jaipilot_diff_gate",
    "jaipilot_prove_diff",
}


def fail(message: str) -> None:
    raise SystemExit(f"MCP smoke failed: {message}")


def response(process: subprocess.Popen[bytes], request_id: int, timeout: float = 10.0) -> dict:
    selector = selectors.DefaultSelector()
    assert process.stdout is not None
    selector.register(process.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            fail(f"server exited with {process.returncode}")
        events = selector.select(max(0.0, deadline - time.monotonic()))
        if not events:
            continue
        line = process.stdout.readline()
        if not line:
            fail("server closed stdout")
        try:
            message = json.loads(line)
        except json.JSONDecodeError as exception:
            fail(f"non-protocol stdout: {line!r} ({exception})")
        if message.get("id") == request_id:
            return message
    fail(f"timed out waiting for response {request_id}")


def send(process: subprocess.Popen[bytes], message: dict) -> None:
    assert process.stdin is not None
    process.stdin.write(json.dumps(message, separators=(",", ":")).encode() + b"\n")
    process.stdin.flush()


def main() -> None:
    if len(sys.argv) < 2:
        fail("usage: smoke-test-mcp.py <server-command> [args ...]")
    environment = os.environ.copy()
    environment.setdefault("JAIPILOT_DASHBOARD_DISABLED", "1")
    process = subprocess.Popen(
        sys.argv[1:],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=environment,
    )
    try:
        send(process, {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "jaipilot-smoke", "version": "1"},
            },
        })
        initialized = response(process, 1)
        if "result" not in initialized or initialized.get("error") is not None:
            fail(f"initialize returned {initialized}")
        send(process, {"jsonrpc": "2.0", "method": "notifications/initialized"})
        send(process, {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}})
        listed = response(process, 2)
        tools = listed.get("result", {}).get("tools", [])
        names = {tool.get("name") for tool in tools if isinstance(tool, dict)}
        if names != EXPECTED_TOOLS:
            fail(f"expected {sorted(EXPECTED_TOOLS)}, received {sorted(names)}")
        print(f"MCP initialize/tools-list passed with {len(names)} tools.")
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


if __name__ == "__main__":
    main()
