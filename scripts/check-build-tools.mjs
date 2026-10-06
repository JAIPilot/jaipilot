import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';

const version = readFileSync('VERSION', 'utf8').trim();
const windows = process.platform === 'win32';
const binary = resolve(process.argv[2] || `dist/jaipilot${windows ? '.exe' : ''}`);
const gradle = process.env.JAIPILOT_TEST_GRADLE || (windows ? 'gradle.bat' : 'gradle');
const maven = windows ? 'mvn.cmd' : 'mvn';
const root = mkdtempSync(join(tmpdir(), 'jaipilot build tools '));
let count = 0;
function put(dir, name, value) {
  const path = join(dir, name);
  mkdirSync(resolve(path, '..'), { recursive: true });
  writeFileSync(path, value);
}
function command(executable, args, cwd, success = true, message) {
  const batch = windows && /\.(bat|cmd)$/.test(executable);
  const quote = value => '"' + value.replaceAll('"', '""') + '"';
  const result = spawnSync(batch ? (process.env.ComSpec || 'cmd.exe') : executable, batch ? ['/d', '/s', '/c', '"' + [executable, ...args].map(quote).join(' ') + '"'] : args, {
    windowsVerbatimArguments: batch, cwd, encoding: 'utf8', timeout: 240_000,
    shell: false, maxBuffer: 4 * 1024 * 1024,
    env: { ...process.env, JAIPILOT_NO_UPDATE: '1' } });
  const output = (result.stdout || '') + (result.stderr || '');
  if ((result.status === 0) !== success || result.error || (message && !output.includes(message))) {
    throw new Error(`${executable} ${args.join(' ')} failed expectation (${result.status})\n${output}`);
  }
  return output;
}
function source(dir, initialize = true) {
  put(dir, '.gitignore', 'target/\nbuild/\n.gradle/\n');
  put(dir, 'src/main/java/com/acme/Calculator.java', `package com.acme;
public class Calculator {
  public int sign(int value) {
    if (value > 0) {
      return 1;
    }
    return -1;
  }
}
`);
  put(dir, 'src/test/java/com/acme/CalculatorTest.java', `package com.acme;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class CalculatorTest {
  @Test public void positive() { assertEquals(1, new Calculator().sign(2)); }
}
`);
  if (!initialize) return;
  command('git', ['init'], dir);
  command('git', ['add', '.'], dir);
  command('git', ['-c', 'user.name=JAIPilot integration', '-c', 'user.email=integration@example.invalid', 'commit', '-m', 'fixture'], dir);
}
const pom = (minimum, jacoco = true) => `<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>com.acme</groupId><artifactId>coverage-fixture</artifactId><version>1.0</version>
<properties><maven.compiler.release>17</maven.compiler.release><minimum>${minimum}</minimum></properties>
<dependencies><dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency></dependencies>
<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version></plugin>
${jacoco ? `<plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.15</version><executions>
<execution><id>agent</id><goals><goal>prepare-agent</goal></goals></execution>
<execution><id>gate</id><goals><goal>check</goal></goals><configuration><rules><rule><element>CLASS</element><includes><include>com.acme.Calculator</include></includes><limits><limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>\${minimum}</minimum></limit></limits></rule></rules></configuration></execution>
</executions></plugin>` : ''}</plugins></build></project>`;
const mavenDir = join(root, 'maven fixture');
mkdirSync(mavenDir);
put(mavenDir, 'pom.xml', pom('0.90'));
source(mavenDir);
const mavenGoal = `com.jaipilot:jaipilot-maven-plugin:${version}:check`;
const mavenArgs = [mavenGoal, '-Djaipilot.coverage.line=0', `-Djaipilot.executable=${binary}`, '-Dstyle.color=never'];
command(maven, mavenArgs, mavenDir, false, 'Coverage requirements remain unmet'); count++;
let policy = JSON.parse(readFileSync(join(mavenDir, 'target/jaipilot/coverage-policy.json'), 'utf8'));
assert(policy.targets.some(t => t.minimumPercent === 90 && t.element === 'CLASS' && t.includes[0] === 'com.acme.Calculator'));
assert.equal(readFileSync(join(mavenDir, 'pom.xml'), 'utf8'), pom('0.90'));
put(mavenDir, 'pom.xml', pom('0'));
command(maven, mavenArgs, mavenDir, true, 'Coverage requirements passed.'); count++;
put(mavenDir, 'pom.xml', pom('0', false));
command(maven, mavenArgs, mavenDir, false, 'JaCoCo is not configured'); count++;

// CLI discovery must preserve the same evaluated Maven rules without an adapter invocation.
put(mavenDir, 'pom.xml', pom('0'));
command(binary, ['run', 'improve_coverage', '--all', '--coverage-target', '0', '--repo', mavenDir], mavenDir, true, 'Coverage requirements passed.'); count++;

// Reactor inheritance resolves each module's properties, not the aggregator's values.
const reactor = join(root, 'reactor fixture');
mkdirSync(reactor);
const parent = pom('0.90').replace('<artifactId>coverage-fixture</artifactId>', '<artifactId>reactor</artifactId><packaging>pom</packaging><modules><module>a</module><module>b</module></modules>');
put(reactor, 'pom.xml', parent);
put(reactor, '.gitignore', 'target/\n');
const child = (name, minimum) => `<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><parent><groupId>com.acme</groupId><artifactId>reactor</artifactId><version>1.0</version></parent><artifactId>${name}</artifactId><properties><minimum>${minimum}</minimum></properties></project>`;
for (const name of ['a', 'b']) { const dir=join(reactor,name);mkdirSync(dir);put(dir,'pom.xml',child(name,name==='a'?'0':'0.90'));source(dir,false); }
command('git', ['init'], reactor);
command('git', ['add', '.'], reactor);
command('git', ['-c','user.name=JAIPilot integration','-c','user.email=integration@example.invalid','commit','-m','reactor'],reactor);
command(maven,mavenArgs,reactor,false,'Coverage requirements remain unmet'); count++;
policy=JSON.parse(readFileSync(join(reactor,'target/jaipilot/coverage-policy.json'),'utf8'));
assert(policy.targets.some(t=>t.report.startsWith('a/') && t.minimumPercent===0));
assert(policy.targets.some(t=>t.report.startsWith('b/') && t.minimumPercent===90));
put(reactor,'b/pom.xml',child('b','0'));
command(maven,mavenArgs,reactor,true,'Coverage requirements passed.'); count++;

const gradleDir = join(root, 'gradle fixture');
mkdirSync(gradleDir);
put(gradleDir, 'settings.gradle', `pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }
rootProject.name = 'coverage-fixture'
`);
const build = (minimum, jacoco = true) => `plugins { id 'java'; ${jacoco ? "id 'jacoco';" : ''} id 'com.jaipilot' version '${version}' }
repositories { mavenCentral(); mavenLocal() }
dependencies { testImplementation 'junit:junit:4.13.2' }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
${jacoco ? `jacoco { toolVersion = '0.8.15' }
jacocoTestCoverageVerification { violationRules { rule { element = 'CLASS'; includes = ['com.acme.Calculator']; limit { counter = 'LINE'; value = 'COVEREDRATIO'; minimum = ${minimum} } } } }
` : ''}`;
put(gradleDir, 'build.gradle', build('0.90'));
source(gradleDir);
// Child builds use the same Gradle version as the parent, including an absolute PATH launcher.
const pathEnv = process.env.PATH;
process.env.PATH = resolve(gradle, '..') + (windows ? ';' : ':') + pathEnv;
const gradleArgs = ['jaipilotCheck', '-Pjaipilot.coverage.line=0', `-Pjaipilot.executable=${binary}`, '--no-daemon'];
command(gradle, gradleArgs, gradleDir, false, 'Coverage requirements remain unmet'); count++;
policy = JSON.parse(readFileSync(join(gradleDir, 'build/jaipilot/coverage-policy.json'), 'utf8'));
assert(policy.targets.some(t => t.minimumPercent === 90 && t.element === 'CLASS'));
assert.equal(readFileSync(join(gradleDir, 'build.gradle'), 'utf8'), build('0.90'));
put(gradleDir, 'build.gradle', build('0'));
command(gradle, gradleArgs, gradleDir, true, 'Coverage requirements passed.'); count++;
put(gradleDir, 'build.gradle', build('0', false));
command(gradle, gradleArgs, gradleDir, false, 'JaCoCo is not active'); count++;
console.log(`${count} real Maven/Gradle integration scenarios passed. Fixtures: ${root}`);
