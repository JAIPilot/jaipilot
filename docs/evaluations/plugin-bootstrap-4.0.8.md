# JAIPilot 4.0.8 cold-bootstrap evidence

Measured on 2026-08-09 on macOS 13.1 x86_64 against the public JAIPilot 4.0.7 GitHub release.
Each observation used a new empty application directory, the same 12,357,687-byte JAR and 93-byte
checksum, the same installer and machine, and warm DNS/network state. Times cover both GitHub
downloads, SHA-256 verification, and atomic installation. p95 is nearest-rank for five runs.

## Observations

| Installer | Raw seconds | Median | p95 |
| --- | --- | ---: | ---: |
| Sequential JAR then checksum | 7.651, 15.831, 13.299, 17.819, 18.351 | 15.831 | 18.351 |
| Concurrent JAR and checksum | 6.400, 6.555, 16.463, 13.235, 12.924 | 12.924 | 16.463 |

Concurrent transfer reduced median complete-bootstrap time by 18.4% and p95 by 10.3%. Every
successful observation verified the downloaded JAR against the release checksum before publishing
it. Network variance remains visible; the candidate does not claim that GitHub always responds in
under ten seconds.

## Host boundary

A real cold Codex 0.147.0 plugin start failed against 4.0.7 because its default ten-second MCP
startup window expired before the public download completed. JAIPilot 4.0.8 removes the sequential
request and declares a 20-second MCP startup boundary, above the observed 16.463-second p95 without
changing the installer's bounded connection, transfer, retry, or checksum rules. If bootstrap still
cannot complete, startup fails explicitly rather than exposing partial tools or unverified bytes.

The wider boundary applies only when the versioned payload is absent. Once installed, the existing
five-run plugin MCP initialize plus six-tool list evidence remains 1.09, 1.09, 1.10, 1.11, and 1.13
seconds (median 1.10, p95 1.13). No repository inspection or build occurs during either startup path.
