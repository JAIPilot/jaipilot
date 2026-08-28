# Run pinned OpenRewrite recipes

Use the repository wrapper and existing configuration first. Resolve current plugin and recipe
versions from official OpenRewrite documentation and the repository's approved artifact sources at
execution time; the command shapes below intentionally contain no version recommendation.

## Maven

For a configured repository, inspect the effective plugin configuration and use:

```text
./mvnw rewrite:discover
./mvnw rewrite:dryRun
./mvnw rewrite:run
```

Pass `-Drewrite.activeRecipes=<fully.qualified.Recipe>` only when overriding the configured recipe
is intentional. Set `-Drewrite.failOnInvalidActiveRecipes=true` so a misspelled or incompatible
recipe fails rather than becoming a silent no-op.

For approved temporary use without editing the POM, invoke the fully qualified plugin coordinate
at an exact version. When the recipe is not already on the project classpath, supply its exact
artifact coordinate:

```text
./mvnw org.openrewrite.maven:rewrite-maven-plugin:<plugin-version>:dryRun \
  -Drewrite.activeRecipes=<fully.qualified.Recipe> \
  -Drewrite.recipeArtifactCoordinates=<group>:<artifact>:<recipe-version> \
  -Drewrite.failOnInvalidActiveRecipes=true
```

Run the same command shape with `run` only after reviewing the dry-run output. Locate every emitted
patch; Maven commonly writes `target/site/rewrite/rewrite.patch`, and multi-module builds can emit
more than one report. Do not assume an empty console summary means no patch exists.

## Gradle

For a configured repository, inspect the root and subproject Rewrite configuration and use:

```text
./gradlew rewriteDiscover
./gradlew rewriteDryRun
./gradlew rewriteRun
```

Gradle dry runs commonly write `build/reports/rewrite/rewrite.patch`. Inspect reports from all
affected subprojects.

When the repository has no plugin, obtain approval and place a pinned Groovy or Kotlin init script
in a temporary directory outside the checkout. The script may apply the exact Rewrite plugin and
recipe artifacts from repository-approved sources without making them permanent build inputs. Run
the wrapper with `--init-script <file>` and an explicit
`-Drewrite.activeRecipe=<fully.qualified.Recipe>`. Remove the script after the migration. Never copy
an official example containing `latest.release` into a real run.

## Review the generated candidate

Before applying, enumerate every proposed path and review the patch itself. After applying, compare
the actual path set and content with the preview. Inspect dependency locks, catalogs, POMs, Gradle
scripts, YAML, XML, properties, resources, and tests—not only Java files. Keep formatter-only churn
out unless formatting is the named recipe outcome.

Run the identical dry run again after the candidate and any manual fixes. No output is the strongest
idempotence signal. Remaining output requires an explicit explanation and another bounded decision;
do not hide it by disabling the recipe.

## Custom recipe gate

Use an existing recipe-development module when available. Otherwise keep temporary recipe code
outside the target repository unless the user requests a maintained repository-owned recipe.
Require focused before/after tests with type attribution, representative positive cases, near-miss
and no-op cases, option validation, multi-source-set or language-level cases when relevant, and a
second cycle that produces no further change. Test the recipe independently before running it on
the target checkout, then apply the same preview, diff-review, and repository verification gates.

## Official references

- [Maven plugin configuration](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
- [Gradle plugin configuration](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
- [Run Gradle recipes without changing the build](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build)
