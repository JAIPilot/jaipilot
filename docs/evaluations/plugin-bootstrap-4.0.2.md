# JAIPilot 4.0.2 plugin bootstrap evidence

Measured on 2026-08-08 on macOS 13.1 x86_64. Java launch checks used Corretto 17.0.13.
The candidate was built from the final 4.0.2 source diff with warm Maven dependencies. p95 is the
nearest-rank value for five runs.

## Download boundary

| Payload | Bytes | Contents |
| --- | ---: | --- |
| v4.0.1 macOS x64 release archive | 52,547,992 | Toolkit, duplicated plugin, and private JRE |
| v4.0.2 portable plugin JAR | 12,355,248 | Toolkit only; host Java 17+ |

The cold payload is 40,192,744 bytes smaller, a 76.5% reduction. v4.0.2 publishes one portable JAR
instead of four operating-system/architecture archives. The v4.0.1 size is the release asset's HTTP
`Content-Length`; the candidate size is the generated file's byte count.

## Warm protocol path

The complete installed-plugin MCP `initialize` plus exact six-tool `tools/list` observations were
1.11, 1.13, 1.09, 1.10, and 1.09 seconds: median 1.10 seconds, p95 1.13 seconds. Stdout contained
protocol messages only. The earlier v4.0.1 bundled-runtime evidence recorded median 1.10 seconds and
p95 1.41 seconds; the runtimes differ, so this is a user-visible regression check rather than a
synthetic same-JVM microbenchmark.

## Failure-path acceptance

`scripts/smoke-test-install.sh` verified all of the following against the generated payload:

- two initial HTTP 503 responses were retried and the fourth request completed the JAR plus checksum;
- the installed bytes matched the release SHA-256 before publication;
- a failed detached SessionStart download wrote diagnostics only to its owner-private session log;
- Stop returned success with zero stdout/stderr and did not start an installer while the payload was
  unavailable;
- non-Java directories created neither download state nor repository/dashboard state;
- no private JRE or duplicate plugin tree was installed; and
- a real stdio MCP initialize/tools-list exchange returned exactly the six public tools.

The bounded curl path uses a 10-second connection timeout, a 180-second total timeout, four retries,
and `--retry-all-errors` when the installed curl supports it. SHA-256 validation remains mandatory
after every successful transfer.
