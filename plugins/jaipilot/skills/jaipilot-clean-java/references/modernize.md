# Upgrade to verified compatible stable releases

"Latest safe" means the newest stable version demonstrated compatible with the declared repository
boundary. Do not equate the largest version number with safety.

## Inventory and research

1. Inventory the JDK source, target, release, and toolchain; Maven or Gradle wrapper; build plugins;
   BOMs or platforms; frameworks; direct dependencies; lockfiles; CI images; containers; deployment
   runtime; and supported consumer baseline.
2. Resolve candidate stable releases from authoritative vendor release notes and package metadata.
   Exclude snapshots, milestones, release candidates, abandoned coordinates, and unverified mirrors.
   If current release data or network access is unavailable, report the candidate as unknown.
3. Read migration guides, removed APIs, minimum JDK and build requirements, known incompatibilities,
   security notes, and transitive graph changes before editing versions.
4. Do not upgrade a dependency that the unused workflow proves removable. Do not use dynamic
   version ranges or silently add repositories.

## Plan isolated upgrade axes

Normally separate:

1. JDK toolchain and CI/runtime images;
2. build wrapper and plugins;
3. BOM or core framework;
4. direct libraries in compatible groups; and
5. source migrations and deprecated API removal.

For Java 25, first prove that the repository and every production environment support it, that the
build and analysis plugins understand its bytecode, and that consumers permit the new minimum.
Use the repository's toolchain and release conventions; do not merely change one integer.

## Migrate and verify

1. Prefer configured pinned OpenRewrite migration recipes, exactly scoped dry runs, and reviewed
   patches. Otherwise make the smallest documented migration manually.
2. Snapshot resolved dependency trees and artifacts before and after each axis. Investigate new
   transitive dependencies, exclusions, split packages, license changes, and logging/provider swaps.
3. Run compile and focused tests per batch, then clean verification across applicable profiles,
   integration tests, packaging, container or runtime smoke, architecture, analysis, API
   compatibility, and serialization checks.
4. Do not suppress warnings, illegal access, linkage errors, flaky tests, or security failures to
   complete an upgrade. Roll back the failed axis and report the highest verified version.

Report every component's old version, evaluated release, accepted version, evidence, migration
edits, rejected version and reason, unresolved advisories, runtime assumptions, and exact commands.
Never claim "all latest" when even one component or environment was not verified.
