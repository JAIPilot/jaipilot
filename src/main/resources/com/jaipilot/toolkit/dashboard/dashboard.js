const byId = (id) => document.getElementById(id);
const number = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });

function setText(id, value) {
  const element = byId(id);
  if (element) element.textContent = value;
}

function finite(value) {
  return Number.isFinite(value);
}

function valueOrDash(value, suffix = '') {
  return finite(value) ? `${number.format(value)}${suffix}` : '—';
}

function signed(value) {
  if (!finite(value)) return '0.0';
  return `${value > 0 ? '+' : ''}${number.format(value)}`;
}

function setStatus(id, label, state = 'neutral') {
  const element = byId(id);
  if (!element) return;
  element.textContent = label;
  element.classList.remove('neutral', 'passed', 'warning', 'failed');
  element.classList.add(state);
}

function relativeTime(timestamp) {
  if (!timestamp || !Number.isFinite(Date.parse(timestamp))) return 'never';
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(timestamp)) / 1000));
  if (seconds < 10) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return new Date(timestamp).toLocaleDateString();
}

function olderThan(candidate, reference) {
  return candidate && reference
    && Number.isFinite(Date.parse(candidate))
    && Number.isFinite(Date.parse(reference))
    && Date.parse(candidate) < Date.parse(reference);
}

function renderCurrentQuality(evidence) {
  const quality = evidence.currentQuality || {};
  const available = finite(quality.qualityScore);
  setText('quality-score', valueOrDash(quality.qualityScore));
  setText('reliability-score', valueOrDash(quality.reliabilityScore));
  setText('maintainability-score', valueOrDash(quality.maintainabilityScore));
  setText('complexity-score', valueOrDash(quality.complexityScore));
  setText('duplication-score', valueOrDash(quality.duplicationScore));
  setText('debt-minutes', finite(quality.remediationDebtMinutes)
    ? `${number.format(quality.remediationDebtMinutes)} min`
    : '—');
  setText('source-lines', valueOrDash(quality.linesOfCode));
  setText('bug-risk-count', `${valueOrDash(quality.bugRiskCount)} bug risks`);
  setText('code-smell-count', `${valueOrDash(quality.codeSmellCount)} code smells`);
  setText('complexity-detail', finite(quality.maximumCyclomaticComplexity)
    ? `Maximum ${number.format(quality.maximumCyclomaticComplexity)} · cognitive ${valueOrDash(quality.maximumCognitiveComplexity)}`
    : 'Maximum —');
  setText('duplication-detail', finite(quality.duplicatedLineCount)
    ? `${number.format(quality.duplicatedLineCount)} lines · ${valueOrDash(quality.duplicationPercent, '%')}`
    : '— duplicated lines');
  setText('debt-ratio', `${valueOrDash(quality.remediationDebtRatioPercent, '%')} debt ratio`);
  setText('source-detail', finite(quality.fileCount)
    ? `${number.format(quality.fileCount)} files · ${valueOrDash(quality.methodCount)} methods`
    : '— files · — methods');

  if (!available) {
    const revision = quality.revision ? ` · commit ${quality.revision.slice(0, 12)}` : '';
    if (quality.analysisStatus === 'failed') {
      setStatus('quality-status', 'FAILED', 'failed');
      setText('analysis-meta', `Current quality refresh failed${revision} · ${relativeTime(quality.capturedAt)}`);
    } else if (quality.analysisStatus === 'no_java_sources') {
      setStatus('quality-status', 'NO JAVA', 'neutral');
      setText('analysis-meta', `No Java production sources in the current project${revision} · ${relativeTime(quality.capturedAt)}`);
    } else {
      setStatus('quality-status', 'NOT ANALYZED', 'neutral');
      setText('analysis-meta', 'Waiting for the first whole-project analysis after an agent Git commit.');
    }
  } else if ((quality.parseFailures || 0) > 0) {
    setStatus('quality-status', 'INCOMPLETE', 'failed');
    setText('analysis-meta', `${quality.source || 'Current analysis'} · ${quality.parseFailures} parse failure(s) · ${relativeTime(quality.capturedAt)}`);
  } else {
    const score = quality.qualityScore;
    if (score >= 90) setStatus('quality-status', 'GOOD', 'passed');
    else if (score >= 75) setStatus('quality-status', 'REVIEW', 'warning');
    else setStatus('quality-status', 'NEEDS WORK', 'failed');
    const scope = quality.scope === 'whole_project' ? 'whole project' : 'selected scope';
    const revision = quality.revision ? ` · commit ${quality.revision.slice(0, 12)}` : '';
    const elapsed = finite(quality.analysisElapsedNanos)
      ? ` · ${number.format(quality.analysisElapsedNanos / 1_000_000)} ms analysis`
      : '';
    setText(
      'analysis-meta',
      `${quality.source || 'Quality analysis'} · ${scope}${revision} · ${relativeTime(quality.capturedAt)}${elapsed}`,
    );
  }

  renderFindings(quality.findings || evidence.findings || {});
  renderArchitecture(evidence.architecture || {}, quality.capturedAt);
  renderProof(evidence, quality.capturedAt);
}

function renderFindings(findings) {
  const available = finite(findings.total);
  const total = available ? findings.total : null;
  const allItems = Array.isArray(findings.items) ? findings.items : [];
  const items = allItems.slice(0, 12);
  setText('findings-total', total === null ? '—' : number.format(total));
  setText('findings-critical', number.format(findings.critical || 0));
  setText('findings-high', number.format(findings.high || 0));
  setText('findings-medium', number.format(findings.medium || 0));
  setText('findings-low', number.format(findings.low || 0));
  if (!available) {
    setText('findings-meta', 'No current quality analysis has run.');
  } else {
    const incomplete = findings.parseFailures ? ` · ${findings.parseFailures} parse failure(s)` : '';
    const limited = total > items.length ? ` · showing ${items.length} of ${number.format(total)}` : '';
    setText(
      'findings-meta',
      `${findings.source || 'Quality analysis'} · ${relativeTime(findings.capturedAt)}${incomplete}${limited}`,
    );
  }

  const body = byId('findings-list');
  if (!body) return;
  body.replaceChildren();
  if (!items.length) {
    const row = document.createElement('tr');
    row.className = 'empty-row';
    const cell = document.createElement('td');
    cell.colSpan = 4;
    cell.textContent = available && total === 0
      ? 'No active findings in the current analyzed project.'
      : 'No findings evidence yet.';
    row.append(cell);
    body.append(row);
    return;
  }
  for (const finding of items) {
    const row = document.createElement('tr');
    const severityCell = document.createElement('td');
    const severity = document.createElement('b');
    const severityName = (finding.severity || 'LOW').toLowerCase();
    severity.className = `severity ${severityName}`;
    severity.textContent = finding.severity || 'LOW';
    severityCell.append(severity);
    const rule = document.createElement('td');
    rule.className = 'finding-rule';
    rule.textContent = finding.id || finding.category || 'Quality rule';
    const message = document.createElement('td');
    message.className = 'finding-message';
    message.textContent = finding.message || 'Finding detected.';
    if (finding.remediation) {
      const remediation = document.createElement('small');
      remediation.textContent = finding.remediation;
      message.append(remediation);
    }
    const location = document.createElement('td');
    location.className = 'finding-location';
    location.textContent = `${finding.relativePath || finding.symbol || 'Analyzed source'}${finding.line ? `:${finding.line}` : ''}`;
    row.append(severityCell, rule, message, location);
    body.append(row);
  }
}

function replaceIssueList(id, items, emptyMessage, renderer) {
  const root = byId(id);
  if (!root) return;
  root.replaceChildren();
  if (!items.length) {
    const empty = document.createElement('li');
    empty.className = 'empty-item';
    empty.textContent = emptyMessage;
    root.append(empty);
    return;
  }
  for (const item of items) root.append(renderer(item));
}

function issueItem(title, detail, meta, state = 'failed') {
  const item = document.createElement('li');
  item.className = `issue-item ${state}`;
  const heading = document.createElement('strong');
  heading.textContent = title;
  const message = document.createElement('span');
  message.textContent = detail;
  const context = document.createElement('small');
  context.textContent = meta;
  item.append(heading, message, context);
  return item;
}

function renderArchitecture(architecture, currentQualityAt) {
  const available = typeof architecture.complete === 'boolean';
  const stale = available && olderThan(architecture.capturedAt, currentQualityAt);
  const violations = finite(architecture.violationCount) ? architecture.violationCount : null;
  setText('architecture-violations', violations === null ? '—' : number.format(violations));
  setText('architecture-classes', available ? number.format(architecture.compiledClassCount || 0) : '—');
  setText('architecture-ruleset', finite(architecture.rulesetVersion) ? `v${architecture.rulesetVersion}` : '—');

  if (!available) {
    setStatus('architecture-status', 'NOT RUN', 'neutral');
    setText('architecture-summary', architecture.incompleteReason || 'No architecture proof has run.');
  } else if (stale) {
    setStatus('architecture-status', 'STALE', 'warning');
    setText('architecture-summary', 'The latest ArchUnit proof predates the current commit analysis. Prove the current diff.');
  } else if (!architecture.complete) {
    setStatus('architecture-status', 'INCOMPLETE', 'failed');
    setText('architecture-summary', architecture.incompleteReason || 'Architecture evidence is incomplete.');
  } else if (architecture.goalMet === true) {
    setStatus('architecture-status', 'CLEAN', 'passed');
    setText('architecture-summary', 'No package-cycle violations were found in the changed-code scope.');
  } else {
    setStatus('architecture-status', 'VIOLATIONS', 'failed');
    setText('architecture-summary', `${number.format(violations || 0)} architecture violation(s) require remediation.`);
  }

  const engine = architecture.engine
    ? `${architecture.engine}${architecture.engineVersion ? ` ${architecture.engineVersion}` : ''}`
    : 'ArchUnit';
  const rules = Array.isArray(architecture.rules) && architecture.rules.length
    ? architecture.rules.join(', ')
    : 'rules pending';
  setText('architecture-meta', available
    ? `${engine} · ${rules} · ${relativeTime(architecture.capturedAt)}`
    : 'Pinned ArchUnit evidence will appear here.');
  const items = Array.isArray(architecture.violations) ? architecture.violations : [];
  replaceIssueList(
    'architecture-list',
    items,
    available && architecture.goalMet ? 'Architecture proof is clean.' : 'No architecture violations to display.',
    (violation) => issueItem(
      `${violation.severity || 'ISSUE'} · ${violation.id || 'Architecture rule'}`,
      violation.message || 'Architecture violation detected.',
      `${violation.relativePath || violation.originClass || 'Analyzed class'}${violation.line ? `:${violation.line}` : ''}`,
    ),
  );
}

function renderProof(evidence, currentQualityAt) {
  const gates = evidence.gates || {};
  const proofAvailable = typeof evidence.lastProofPassed === 'boolean';
  const stale = proofAvailable && olderThan(evidence.lastProofAt, currentQualityAt);
  setText('test-score', valueOrDash(evidence.testQualityScore));
  setText('mutation-score', valueOrDash(evidence.mutationScore, '%'));
  setText('verified-targets', proofAvailable ? number.format(evidence.verifiedTargetCount || 0) : '—');
  setText('line-coverage', valueOrDash(evidence.lineCoverage, '%'));
  setText('branch-coverage', valueOrDash(evidence.branchCoverage, '%'));
  setText('proof-time', proofAvailable ? `Latest proof ${relativeTime(evidence.lastProofAt)}` : 'No proof has run.');

  if (!proofAvailable) setStatus('proof-status', 'NOT RUN', 'neutral');
  else if (stale) setStatus('proof-status', 'STALE', 'warning');
  else if (evidence.lastProofPassed) setStatus('proof-status', 'PASSED', 'passed');
  else setStatus('proof-status', 'FAILED', 'failed');

  const failures = Array.isArray(gates.failures) ? gates.failures : [];
  const warnings = Array.isArray(gates.warnings) ? gates.warnings : [];
  const items = [
    ...failures.map((message) => ({ label: 'FAILURE', message, state: 'failed' })),
    ...warnings.map((message) => ({ label: 'WARNING', message, state: 'warning' })),
  ];
  setText('gate-message-count', proofAvailable ? items.length : '—');
  replaceIssueList(
    'gate-list',
    items,
    stale ? 'Proof must be refreshed for the current commit.'
      : proofAvailable && evidence.lastProofPassed ? 'All changed-code gates passed.'
        : 'No changed-code proof evidence yet.',
    (item) => issueItem(item.label, item.message, gates.source || 'Changed-code proof', item.state),
  );
}

function renderImpact(impact) {
  setText('coverage-change', `${signed(impact.coveragePointsChanged)} pts`);
  setText('findings-resolved', number.format(impact.findingsResolved));
  setText('debt-removed', `${number.format(impact.debtMinutesRemoved)} debt minutes removed`);
  setText('mutations-killed', number.format(impact.mutationsKilled));
  setText('tests-executed', `${number.format(impact.changedTestsExecuted)} changed tests proven`);
  setText('files-changed', number.format(impact.filesChanged));
  setText('applied-runs', `${number.format(impact.appliedRuns)} transactional applies`);
}

function renderUsage(usage, generatedAt) {
  setText('total-commands', number.format(usage.totalCommands));
  setText('success-rate', `${number.format(usage.successRatePercent)}%`);
  setText('projects-seen', number.format(usage.projectsSeen));
  setText('average-duration', usage.averageCommandDurationMillis >= 1000
    ? `${number.format(usage.averageCommandDurationMillis / 1000)} s`
    : `${number.format(usage.averageCommandDurationMillis)} ms`);
  setText('last-updated', `Dashboard refreshed ${relativeTime(generatedAt)}`);
}

function renderCommands(commands) {
  const root = byId('command-bars');
  if (!root) return;
  root.replaceChildren();
  const visible = commands.slice(0, 7);
  const maximum = Math.max(1, ...visible.map((item) => item.count));
  for (const item of visible) {
    const row = document.createElement('div');
    row.className = 'command-row';
    const label = document.createElement('span');
    label.textContent = item.command;
    label.title = item.command;
    const track = document.createElement('div');
    track.className = 'command-track';
    const bar = document.createElement('i');
    bar.style.setProperty('--width', `${Math.max(3, item.count / maximum * 100)}%`);
    track.append(bar);
    const count = document.createElement('b');
    count.textContent = number.format(item.count);
    row.append(label, track, count);
    root.append(row);
  }
  if (!visible.length) {
    const empty = document.createElement('div');
    empty.className = 'empty-item';
    empty.textContent = 'Command usage will appear here.';
    root.append(empty);
  }
}

function renderActivity(activity) {
  const root = byId('activity-list');
  if (!root) return;
  root.replaceChildren();
  for (const item of activity.slice(0, 8)) {
    const row = document.createElement('li');
    row.className = `activity-item${item.successful ? '' : ' failed'}`;
    const detail = document.createElement('div');
    const summary = document.createElement('strong');
    summary.textContent = item.summary;
    const meta = document.createElement('small');
    meta.textContent = `${item.command} · ${item.durationMillis >= 1000
      ? `${number.format(item.durationMillis / 1000)} s`
      : `${item.durationMillis} ms`}`;
    detail.append(summary, meta);
    const time = document.createElement('span');
    time.className = 'activity-time';
    time.textContent = relativeTime(item.at);
    row.append(detail, time);
    root.append(row);
  }
  if (!activity.length) {
    const empty = document.createElement('li');
    empty.className = 'empty-item';
    empty.textContent = 'JAIPilot activity will appear here.';
    root.append(empty);
  }
}

async function refresh() {
  try {
    const response = await fetch('/api/metrics', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const metrics = await response.json();
    renderCurrentQuality(metrics.latestEvidence || {});
    renderImpact(metrics.impact || {});
    renderUsage(metrics.usage || {}, metrics.generatedAt);
    renderCommands(metrics.commands || []);
    renderActivity(metrics.recentActivity || []);
  } catch (error) {
    setText('last-updated', 'Metrics temporarily unavailable');
  }
}

fetch('/api/health', { cache: 'no-store' })
  .then((response) => response.json())
  .then((health) => setText('dashboard-version', `JAIPilot ${health.version}`))
  .catch(() => setText('dashboard-version', 'JAIPilot'));

refresh();
setInterval(refresh, 3000);
