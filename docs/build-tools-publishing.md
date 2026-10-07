# Build tools publisher setup

Maven Central version 1.2.1 is published under the verified `com.jaipilot` namespace
in the Jaipilot organization. The parent, shared core, and Maven plugin were verified
from an empty dependency cache; the public artifact and signature bytes match the
validated publishing bundle. The `com.jaipilot` Gradle plugin 1.2.1 is submitted through
JAIPilot's corporate publisher account and is awaiting the Portal's initial approval.

The publishing source is commit `ff4a0c4053b6e1443fbc4f9603544e7cd84e8043`;
[all nine CI jobs passed](https://github.com/JAIPilot/jaipilot/actions/runs/37591836602).
Keep credentials and signing keys outside this repository. The native CLI is released
separately through the existing verified release workflow.

## Maven Central

1. Create or sign in to a [Central Publisher Portal account](https://central.sonatype.com/publishing).
2. [Register and verify `com.jaipilot`](https://central.sonatype.org/register/namespace/)
   using ownership of `jaipilot.com`. Use the Portal's exact DNS TXT verification value.
3. Generate a Portal publishing token. Store its username/password in
   `~/.m2/settings.xml` under server ID `central`. Do not use your website password.
4. Prepare an owner-controlled GPG signing key and publish its public key as described
   in [Central's signing requirements](https://central.sonatype.org/publish/requirements/gpg/).
   The Maven profile uses the GPG plugin's best practices; provide the signing secret
   through its supported environment/agent mechanism, never a command-line password.
5. From the exact tested release checkout, run:

```sh
mvn -f build-tools/pom.xml -Ppublish-central deploy
```

For an encrypted exported key, Maven's Java signer also supports:

```sh
mvn -f build-tools/pom.xml -Ppublish-central -Dgpg.signer=bc \
  -Dgpg.keyFilePath=/private/path/maven-signing-key.asc deploy
```

Supply its passphrase through `MAVEN_GPG_PASSPHRASE` in a private environment.

The profile attaches sources/Javadocs, signs artifacts, and uploads a validated bundle
using Sonatype's [official publishing plugin](https://central.sonatype.org/publish/publish-portal-maven/).
It leaves final publishing under the publisher's control (`autoPublish=false`).
Review the bundle in the Portal and publish it. Confirm the parent, core, and Maven
plugin 1.2.1 coordinates resolve from Maven Central in an empty local repository.

## Gradle Plugin Portal

1. Create or sign in to a [Gradle Plugin Portal account](https://plugins.gradle.org/user/login).
   Use a JAIPilot-controlled corporate account/email for `com.jaipilot`;
   the Portal may request DNS ownership evidence for `jaipilot.com`.
2. Generate publishing API keys and put them in your private `~/.gradle/gradle.properties`
   as instructed by the [Portal publishing guide](https://plugins.gradle.org/docs/publish-plugin).
3. Publish the shared `com.jaipilot:jaipilot-build-tools-core:1.2.1` to Central first.
4. After configuring the publishing keys, validate publication metadata without publishing:

```sh
gradle -p build-tools :gradle-plugin:publishPlugins --validate-only
```

5. From the exact tested release checkout:

```sh
gradle -p build-tools :gradle-plugin:publishPlugins
```

The plugin ID is `com.jaipilot`. The publishing configuration includes website,
source URL, tags, sources, Javadocs, and an explicit declaration that configuration
cache is unsupported. Initial Portal publication goes through its manual review.
After approval, verify installation through `plugins { id 'com.jaipilot' version '1.2.1' }`
with `gradlePluginPortal()` and no `mavenLocal()` or prepopulated cache.

Registry releases are immutable. CLI and adapter release versions advance by patch
only unless the owner explicitly changes that policy. Verify all commands and native
assets before publishing; do not label a local Maven installation as a public release.
