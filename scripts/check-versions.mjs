import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const version = readFileSync('VERSION', 'utf8').trim();
assert.match(version, /^\d+\.\d+\.\d+$/);
for (const [path, pattern] of [
  ['internal/jaipilot/common.go', /const Version = "([^"]+)"/],
  ['npm/package.json', /"version": "([^"]+)"/],
  ['build-tools/pom.xml', /<artifactId>jaipilot-build-tools<\/artifactId>\s*<version>([^<]+)<\/version>/],
  ['build-tools/build.gradle', /version = '([^']+)'/],
  ['build-tools/core/src/main/java/com/jaipilot/build/NativeExecutable.java', /VERSION = "([^"]+)"/],
]) {
  assert.equal(readFileSync(path, 'utf8').match(pattern)?.[1], version, `Release version differs in ${path}`);
}
console.log(`CLI, npm, Maven, Gradle and native bootstrap versions agree: ${version}`);
