# Local dashboard startup evaluation

This evaluation measures the local runner overhead introduced by automatic dashboard availability.
It compares the pre-dashboard `origin/main` revision `83f2212` with the dashboard implementation in
this change. It is startup evidence, not a claim about build, analysis, coverage, or mutation speed.

## Environment and method

| Boundary | Value |
| --- | --- |
| Machine | Intel Core i7-9750H, macOS x86_64, Darwin 22.2.0 |
| JDK | Temurin OpenJDK 25.0.3+9 LTS |
| Toolkit | Shaded 3.1.2 JAR built once per revision |
| Dependencies | Local Maven cache warm; no dependency resolution inside timed commands |
| Timing | `/usr/bin/time -p`, wall-clock `real`, seven interleaved runs unless noted |
| Percentiles | Median and nearest-rank p95 |
| Output | Redirected to `/dev/null`; dashboard diagnostics remained on stderr |

The baseline and candidate JARs ran alternately against the same live repository and filesystem
caches. Candidate steady-state measurements used one already healthy dashboard. The `inspect`
candidate includes its real atomic usage-metrics update. Cold-start runs used a new private state
directory each time and terminated the exact recorded dashboard PID after measurement.

## Raw observations

| Journey | Baseline seconds | Candidate seconds | Baseline median / p95 | Candidate median / p95 |
| --- | --- | --- | --- | --- |
| `version`, dashboard already running | 0.39, 0.32, 0.35, 0.35, 0.34, 0.33, 0.34 | 0.34, 0.36, 0.34, 0.37, 0.45, 0.38, 0.38 | 0.34 / 0.39 | 0.37 / 0.45 |
| `inspect --project .`, including metrics | 0.86, 0.97, 0.82, 0.87, 1.10, 0.79, 0.83 | 0.86, 0.97, 0.99, 0.97, 0.98, 0.94, 0.88 | 0.86 / 1.10 | 0.97 / 0.99 |
| First command with asynchronous dashboard spawn (five runs) | n/a | 0.69, 0.67, 0.67, 0.67, 0.66 | n/a | 0.67 / 0.69 |
| Explicit `dashboard` command through verified readiness (five runs) | n/a | 1.70, 1.50, 1.44, 1.97, 1.38 | n/a | 1.50 / 1.97 |

## Conclusion

The steady dashboard check adds 0.03 seconds to the median no-op `version` boundary. A real
`inspect` plus its privacy-preserving, locked metrics update adds 0.11 seconds to the median in this
sample; its observed p95 remained below the baseline sample's noisy tail. The one-time spawn is
asynchronous, so the first command returns in a 0.67-second median while dashboard readiness
continues in the detached process. An explicit request that waits for instance-verified HTTP health
takes a 1.50-second median.

The initial implementation synchronously parsed full server metadata and waited on every first
launch. Earlier measurements were 0.60 seconds steady and 1.42 seconds cold. Reusing the command's
JSON mapper, making normal startup asynchronous, excluding administrative checks from product usage,
and adding an atomic owner-private readiness marker reduced steady median startup to 0.37 seconds
without removing instance-verified health from URL discovery, replacement, or recovery paths.
