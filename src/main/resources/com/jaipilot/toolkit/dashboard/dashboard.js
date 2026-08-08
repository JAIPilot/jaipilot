const byId = (id) => document.getElementById(id);
const format = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });
const severityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
let selectedId = location.hash.slice(1);
let sequence = 0;
let request;

function text(id, value) {
  const element = byId(id);
  if (element) element.textContent = value;
}

function number(value, suffix = '') {
  return Number.isFinite(value) ? `${format.format(value)}${suffix}` : '—';
}

function status(id, label, kind = 'neutral') {
  const element = byId(id);
  element.textContent = label;
  element.className = `status ${kind}`;
}

function age(timestamp) {
  const time = Date.parse(timestamp);
  if (!Number.isFinite(time)) return 'unknown time';
  const seconds = Math.max(0, Math.round((Date.now() - time) / 1000));
  if (seconds < 10) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return new Date(time).toLocaleDateString();
}

function renderRepositories(view) {
  const select = byId('repository-select');
  const ids = new Set(view.repositories.map((repository) => repository.id));
  if (!ids.has(selectedId)) selectedId = view.selectedRepository?.id || '';
  select.replaceChildren();
  for (const repository of view.repositories) {
    const option = document.createElement('option');
    option.value = repository.id;
    option.textContent = `${repository.displayName} · ${repository.qualityScore ?? '…'} · ${repository.findings} findings`;
    option.selected = repository.id === selectedId;
    select.append(option);
  }
  select.disabled = view.repositories.length < 2;
}

function renderEmpty() {
  clearQuality();
  clearProof();
  renderImpact({});
  const link = byId('github-link');
  link.removeAttribute('href');
  link.classList.add('hidden');
  text('repository-path', 'Open a Java repository with your coding agent to initialize JAIPilot.');
  status('quality-status', 'INITIALIZING');
  status('proof-status', 'NOT APPLICABLE');
  status('architecture-status', 'NOT APPLICABLE');
  text('analysis-meta', 'No repository has been detected on this machine yet.');
}

function renderRepository(repository) {
  if (!repository) return renderEmpty();
  selectedId = repository.id;
  location.hash = selectedId;
  text('repository-path', repository.projectRoot);
  const link = byId('github-link');
  if (validGitHub(repository.githubUrl)) {
    link.href = repository.githubUrl;
    link.classList.remove('hidden');
  } else {
    link.removeAttribute('href');
    link.classList.add('hidden');
  }
  if (repository.quality) renderQuality(repository); else clearQuality();
  renderProof(repository);
  renderImpact(repository.impact || {});
  if (repository.analysisStatus === 'initializing') {
    status('quality-status', 'INITIALIZING');
    text('analysis-meta', 'JAIPilot detected this repository and is collecting its first current snapshot.');
  } else if (repository.analysisStatus === 'failed') {
    status('quality-status', 'FAILED', 'failed');
    text('analysis-meta', `${repository.error || 'Snapshot failed.'} · ${age(repository.updatedAt)}`);
  }
}

function validGitHub(value) {
  if (typeof value !== 'string') return false;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'github.com';
  } catch (_) {
    return false;
  }
}

function renderQuality(repository) {
  const quality = repository.quality;
  const metrics = quality?.metrics;
  if (!metrics) return;
  text('quality-score', number(metrics.qualityScore));
  text('reliability', number(metrics.reliabilityScore));
  text('maintainability', number(metrics.maintainabilityScore));
  text('complexity', number(metrics.complexityScore));
  text('duplication', number(metrics.duplicationScore));
  text('debt', `${number(metrics.remediationDebtMinutes)} min`);
  text('source-lines', number(metrics.linesOfCode));
  text('bugs', `${number(metrics.bugRiskCount)} bug risks`);
  text('smells', `${number(metrics.codeSmellCount)} code smells`);
  text('complexity-detail', `Maximum ${number(metrics.maximumCyclomaticComplexity)} · cognitive ${number(metrics.maximumCognitiveComplexity)}`);
  text('duplication-detail', `${number(metrics.duplicatedLineCount)} lines · ${number(metrics.duplicationPercent, '%')}`);
  text('debt-ratio', `${number(metrics.remediationDebtRatioPercent, '%')} debt ratio`);
  text('source-detail', `${number(metrics.fileCount)} files · ${number(metrics.methodCount)} methods`);
  const state = metrics.qualityScore >= 90 ? ['GOOD', 'passed']
    : metrics.qualityScore >= 75 ? ['REVIEW', 'warning'] : ['NEEDS WORK', 'failed'];
  status('quality-status', state[0], state[1]);
  const revision = quality.revision ? ` · ${quality.revision.slice(0, 12)}` : '';
  text('analysis-meta', `Current whole-project snapshot${revision} · ${age(quality.capturedAt)} · gate ${quality.gateStatus}`);
  renderFindings(quality);
}

function renderFindings(quality) {
  const findings = [...(quality.findings || [])].sort((a, b) =>
    (severityOrder[a.severity] ?? 9) - (severityOrder[b.severity] ?? 9)
      || String(a.relativePath).localeCompare(String(b.relativePath))
      || (a.line || 0) - (b.line || 0)
      || String(a.id).localeCompare(String(b.id)));
  const recorded = quality.metrics?.findingsBySeverity || {};
  const counts = { CRITICAL: recorded.CRITICAL || 0, HIGH: recorded.HIGH || 0,
    MEDIUM: recorded.MEDIUM || 0, LOW: recorded.LOW || 0 };
  text('findings-total', quality.totalFindings);
  text('critical', counts.CRITICAL); text('high', counts.HIGH); text('medium', counts.MEDIUM); text('low', counts.LOW);
  const shown = Math.min(findings.length, 50);
  text('findings-meta', `${quality.totalFindings} active · showing ${shown} · ${quality.parseFailures} parse failures`);
  const body = byId('findings');
  body.replaceChildren();
  if (!findings.length) {
    row(body, ['No active findings in the current snapshot.'], true);
    return;
  }
  for (const finding of findings.slice(0, 50)) {
    const tr = document.createElement('tr');
    const severity = document.createElement('td');
    const badge = document.createElement('b');
    badge.className = finding.severity.toLowerCase();
    badge.textContent = finding.severity;
    severity.append(badge);
    const rule = document.createElement('td'); rule.className = 'rule'; rule.textContent = finding.id;
    const message = document.createElement('td'); message.textContent = finding.message;
    const location = document.createElement('td'); location.className = 'location';
    location.textContent = `${finding.relativePath}${finding.line ? `:${finding.line}` : ''}`;
    tr.append(severity, rule, message, location); body.append(tr);
  }
}

function clearQuality() {
  for (const id of ['quality-score', 'reliability', 'maintainability', 'complexity', 'duplication',
    'debt', 'source-lines']) text(id, '—');
  text('bugs', '— bug risks'); text('smells', '— code smells');
  text('complexity-detail', 'Maximum —'); text('duplication-detail', '— duplicated lines');
  text('debt-ratio', '— debt ratio'); text('source-detail', '— files · — methods');
  text('findings-total', '—'); text('critical', 0); text('high', 0); text('medium', 0); text('low', 0);
  text('findings-meta', 'Snapshot is initializing.');
  const body = byId('findings'); body.replaceChildren();
  row(body, ['Collecting current findings.'], true);
}

function row(body, values, empty = false) {
  const tr = document.createElement('tr');
  for (const value of values) {
    const td = document.createElement('td');
    if (empty) { td.colSpan = 4; td.className = 'empty'; }
    td.textContent = value; tr.append(td);
  }
  body.append(tr);
}

function renderProof(repository) {
  const proof = repository.proof;
  const fingerprint = repository.quality?.fingerprint;
  const required = repository.quality?.gateStatus === 'review_required';
  const current = proof && fingerprint && proof.fingerprint === fingerprint;
  if (!proof) {
    status('proof-status', required ? 'REQUIRED' : 'NOT APPLICABLE', required ? 'warning' : 'neutral');
    status('architecture-status', required ? 'REQUIRED' : 'NOT APPLICABLE', required ? 'warning' : 'neutral');
    clearProof();
    return;
  }
  if (!current) {
    status('proof-status', 'STALE', 'warning');
    status('architecture-status', 'STALE', 'warning');
    text('proof-meta', 'The latest proof does not match the current source fingerprint.');
    clearProofFacts();
    messages('proof-messages', ['Old proof facts are hidden until the exact current diff is proved.']);
    messages('architecture-messages', ['Old architecture facts are hidden until the exact current diff is proved.']);
    return;
  }
  status('proof-status', proof.passed ? 'PASSED' : 'FAILED', proof.passed ? 'passed' : 'failed');
  text('proof-meta', `Exact fingerprint verified ${age(proof.verifiedAt)}`);
  text('line-coverage', number(proof.lineCoverage, '%'));
  text('branch-coverage', number(proof.branchCoverage, '%'));
  text('mutation', number(proof.mutationScore, '%'));
  text('test-quality', number(proof.testQualityScore));
  text('targets', number(proof.targetCount));
  text('proof-fingerprint', proof.fingerprint.slice(0, 12));
  messages('proof-messages', [...(proof.failures || []), ...(proof.warnings || [])], 'No proof failures or warnings.');
  if (proof.targetCount === 0) status('architecture-status', 'NOT APPLICABLE', 'neutral');
  else if (proof.architectureComplete !== true) status('architecture-status', 'INCOMPLETE', 'failed');
  else if ((proof.architectureViolations || 0) === 0) status('architecture-status', 'CLEAN', 'passed');
  else status('architecture-status', 'VIOLATIONS', 'failed');
  text('architecture-violations', number(proof.architectureViolations));
  text('architecture-ruleset', proof.architectureRulesetVersion ? `v${proof.architectureRulesetVersion}` : '—');
  messages('architecture-messages', proof.architectureMessages || [], 'No architecture violations.');
}

function clearProof() {
  text('proof-meta', 'No current diff proof.'); clearProofFacts();
  messages('proof-messages', ['No current proof messages.']);
  messages('architecture-messages', ['No current architecture messages.']);
}

function clearProofFacts() {
  for (const id of ['line-coverage', 'branch-coverage', 'mutation', 'test-quality', 'targets',
    'proof-fingerprint', 'architecture-violations', 'architecture-ruleset']) text(id, '—');
}

function messages(id, values, fallback) {
  const root = byId(id); root.replaceChildren();
  for (const value of values.length ? values : [fallback || 'No current evidence.']) {
    const item = document.createElement('li'); item.textContent = value; root.append(item);
  }
}

function renderImpact(impact) {
  const signed = (value) => `${value > 0 ? '+' : ''}${number(value || 0)}`;
  text('quality-change', signed(impact.qualityScoreChange));
  text('findings-resolved', signed(impact.findingsResolved));
  text('debt-removed', `${signed(impact.debtMinutesRemoved)} min`);
}

async function refresh() {
  const current = ++sequence;
  request?.abort(); request = new AbortController();
  const query = selectedId ? `?repository=${encodeURIComponent(selectedId)}` : '';
  try {
    const response = await fetch(`/api/metrics${query}`, { cache: 'no-store', signal: request.signal });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const view = await response.json();
    if (current !== sequence) return;
    renderRepositories(view); renderRepository(view.selectedRepository); text('version', `JAIPilot ${view.version}`);
  } catch (error) {
    if (error.name !== 'AbortError') text('analysis-meta', 'Local dashboard data is temporarily unavailable.');
  }
}

byId('repository-select').addEventListener('change', (event) => {
  selectedId = event.target.value; location.hash = selectedId; refresh();
});
window.addEventListener('hashchange', () => { selectedId = location.hash.slice(1); refresh(); });
refresh(); setInterval(refresh, 5000);
