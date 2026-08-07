const byId = (id) => document.getElementById(id);
const number = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });

function setText(id, value) {
  const element = byId(id);
  if (element) element.textContent = value;
}

function valueOrDash(value, suffix = '') {
  return Number.isFinite(value) ? `${number.format(value)}${suffix}` : '—';
}

function signed(value) {
  if (!Number.isFinite(value)) return '0.0';
  const prefix = value > 0 ? '+' : '';
  return `${prefix}${number.format(value)}`;
}

function setGauge(id, textId, value) {
  const score = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
  byId(id)?.style.setProperty('--score', score);
  setText(textId, Number.isFinite(value) ? number.format(value) : '—');
}

function setBadge(id, text, state = 'neutral') {
  const badge = byId(id);
  if (!badge) return;
  badge.textContent = text;
  badge.classList.remove('passed', 'failed', 'warning', 'neutral');
  badge.classList.add(state);
}

function relativeTime(timestamp) {
  if (!timestamp) return 'never';
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(timestamp)) / 1000));
  if (seconds < 10) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return new Date(timestamp).toLocaleDateString();
}

function renderImpact(impact) {
  setText('coverage-change', `${signed(impact.coveragePointsChanged)} pts`);
  setText('targets-improved', `Across ${impact.targetsImproved} applied targets`);
  setText('findings-resolved', number.format(impact.findingsResolved));
  setText(
    'debt-removed',
    `${number.format(impact.debtMinutesRemoved)} remediation min removed · ${signed(impact.qualityPointsChanged)} quality pts`,
  );
  setText('mutations-killed', number.format(impact.mutationsKilled));
  setText('tests-executed', `${number.format(impact.changedTestsExecuted)} changed tests execution-proven`);
  setText('files-changed', number.format(impact.filesChanged));
  setText('applied-runs', `Across ${number.format(impact.appliedRuns)} transactional applies`);
  setText('proof-count', number.format(impact.diffProofsPassed));
  setText('prepared-count', number.format(impact.testRunsPrepared + impact.cleanupRunsPrepared));
  setText('validated-count', number.format(impact.validationsReadyToApply));
  setText('applied-count', number.format(impact.appliedRuns));
  setText('test-runs', number.format(impact.testRunsApplied));
  setText('cleanup-runs', number.format(impact.cleanupRunsApplied));
  setText('discarded-runs', number.format(impact.discardedRuns));

  const prepared = impact.testRunsPrepared + impact.cleanupRunsPrepared;
  const validationRate = prepared ? Math.min(100, impact.validationsReadyToApply / prepared * 100) : 0;
  const applyRate = impact.validationsReadyToApply
    ? Math.min(100, impact.appliedRuns / impact.validationsReadyToApply * 100)
    : 0;
  byId('prepared-progress')?.style.setProperty('width', `${validationRate}%`);
  byId('validated-progress')?.style.setProperty('width', `${applyRate}%`);
}

function renderEvidence(evidence) {
  setGauge('quality-gauge', 'quality-score', evidence.qualityScore);
  setGauge('test-gauge', 'test-score', evidence.testQualityScore);
  setGauge('mutation-gauge', 'mutation-score', evidence.mutationScore);
  setText('line-coverage', valueOrDash(evidence.lineCoverage, '%'));
  setText('branch-coverage', valueOrDash(evidence.branchCoverage, '%'));
  setText('verified-targets', number.format(evidence.verifiedTargetCount || 0));

  const state = byId('proof-state');
  const badge = byId('proof-badge');
  state?.classList.remove('passed', 'failed');
  badge?.classList.remove('passed', 'failed', 'neutral');
  if (evidence.lastProofPassed === true) {
    setText('proof-state', 'PROVEN');
    setText('proof-badge', `Passed ${relativeTime(evidence.lastProofAt)}`);
    state?.classList.add('passed');
    badge?.classList.add('passed');
  } else if (evidence.lastProofPassed === false) {
    setText('proof-state', 'GAPS FOUND');
    setText('proof-badge', `Needs work · ${relativeTime(evidence.lastProofAt)}`);
    state?.classList.add('failed');
    badge?.classList.add('failed');
  } else {
    setText('proof-state', 'AWAITING');
    setText('proof-badge', 'No proof yet');
    badge?.classList.add('neutral');
  }
  renderCurrentStatus(evidence);
}

function newestEvidence(...values) {
  return values
    .filter((value) => value?.capturedAt)
    .sort((left, right) => Date.parse(right.capturedAt) - Date.parse(left.capturedAt))[0];
}

function renderCurrentStatus(evidence) {
  const findings = evidence.findings || {};
  const architecture = evidence.architecture || {};
  const gates = evidence.gates || {};
  const newest = newestEvidence(findings, architecture, gates);
  setBadge(
    'current-status-source',
    newest ? `${newest.source} · ${relativeTime(newest.capturedAt)}` : 'Awaiting analyzed evidence',
    'neutral',
  );
  renderArchitecture(architecture);
  renderFindings(findings);
  renderGates(gates);
}

function replaceStatusList(id, items, emptyMessage, renderItem) {
  const root = byId(id);
  if (!root) return;
  root.replaceChildren();
  if (!items.length) {
    const empty = document.createElement('li');
    empty.className = 'empty-status';
    empty.textContent = emptyMessage;
    root.append(empty);
    return;
  }
  for (const item of items) root.append(renderItem(item));
}

function statusItem(title, detail, meta, state = '') {
  const item = document.createElement('li');
  item.className = `status-item${state ? ` ${state}` : ''}`;
  const heading = document.createElement('strong');
  heading.textContent = title;
  const message = document.createElement('span');
  message.textContent = detail;
  const context = document.createElement('small');
  context.textContent = meta;
  item.append(heading, message, context);
  return item;
}

function renderArchitecture(architecture) {
  const available = typeof architecture.complete === 'boolean';
  const violations = Number.isFinite(architecture.violationCount) ? architecture.violationCount : null;
  setText('architecture-violations', violations === null ? '—' : number.format(violations));
  setText(
    'architecture-classes',
    available ? number.format(architecture.compiledClassCount || 0) : '—',
  );
  setText('architecture-ruleset', Number.isFinite(architecture.rulesetVersion)
    ? `v${architecture.rulesetVersion}`
    : '—');

  if (!available) {
    setBadge('architecture-badge', 'AWAITING', 'neutral');
    setText('architecture-summary', architecture.incompleteReason || 'No architecture proof has run yet.');
  } else if (!architecture.complete) {
    setBadge('architecture-badge', 'INCOMPLETE', 'failed');
    setText('architecture-summary', architecture.incompleteReason || 'Architecture evidence is incomplete.');
  } else if (architecture.goalMet === true) {
    setBadge('architecture-badge', 'CLEAN', 'passed');
    setText('architecture-summary', 'No package-cycle violations were found in the analyzed scope.');
  } else {
    setBadge('architecture-badge', 'GAPS FOUND', 'failed');
    setText('architecture-summary', `${number.format(violations || 0)} architecture violation(s) require remediation.`);
  }

  const engine = architecture.engine
    ? `${architecture.engine}${architecture.engineVersion ? ` ${architecture.engineVersion}` : ''}`
    : 'ArchUnit';
  const rules = Array.isArray(architecture.rules) && architecture.rules.length
    ? architecture.rules.join(', ')
    : 'rules pending';
  const items = Array.isArray(architecture.violations) ? architecture.violations.slice(0, 6) : [];
  const architectureShown = violations > items.length ? ` · showing ${items.length} of ${violations}` : '';
  setText(
    'architecture-meta',
    available ? `${engine} · ${rules}${architectureShown}` : 'Pinned ArchUnit evidence will appear here.',
  );
  replaceStatusList(
    'architecture-list',
    items,
    available && architecture.goalMet ? 'Architecture gate is clean.' : 'No architecture violations to display.',
    (violation) => statusItem(
      `${violation.severity || 'ISSUE'} · ${violation.id || 'Architecture rule'}`,
      violation.message || 'Architecture violation detected.',
      `${violation.relativePath || violation.originClass || 'Analyzed class'}${violation.line ? `:${violation.line}` : ''}`,
      'failed',
    ),
  );
}

function renderFindings(findings) {
  const available = Number.isFinite(findings.total);
  const total = available ? findings.total : null;
  setText('findings-total', total === null ? '—' : number.format(total));
  setText('findings-critical', number.format(findings.critical || 0));
  setText('findings-high', number.format(findings.high || 0));
  setText('findings-medium', number.format(findings.medium || 0));
  setText('findings-low', number.format(findings.low || 0));
  if (!available) {
    setBadge('findings-badge', 'AWAITING', 'neutral');
  } else if ((findings.parseFailures || 0) > 0) {
    setBadge('findings-badge', 'INCOMPLETE', 'failed');
  } else if (total === 0) {
    setBadge('findings-badge', 'CLEAN', 'passed');
  } else if ((findings.critical || 0) + (findings.high || 0) > 0) {
    setBadge('findings-badge', 'ACTION REQUIRED', 'failed');
  } else {
    setBadge('findings-badge', 'REVIEW', 'warning');
  }
  const items = Array.isArray(findings.items) ? findings.items.slice(0, 8) : [];
  const findingsShown = total > items.length ? ` · showing ${items.length} of ${number.format(total)}` : '';
  setText('findings-meta', available
    ? `${findings.source || 'Latest analysis'} · ${findings.parseFailures || 0} parse failure(s)${findingsShown} · ${relativeTime(findings.capturedAt)}`
    : 'Run quality analysis or proof to populate current findings.');
  replaceStatusList(
    'findings-list',
    items,
    available && total === 0 ? 'No active findings in the latest analyzed scope.' : 'No findings evidence yet.',
    (finding) => statusItem(
      `${finding.severity || 'INFO'} · ${finding.category || finding.id || 'Finding'}`,
      finding.message || 'Source finding detected.',
      `${finding.relativePath || finding.symbol || 'Analyzed source'}${finding.line ? `:${finding.line}` : ''}`,
      ['CRITICAL', 'HIGH'].includes(finding.severity) ? 'failed' : 'warning',
    ),
  );
}

function renderGates(gates) {
  const failures = Array.isArray(gates.failures) ? gates.failures : [];
  const warnings = Array.isArray(gates.warnings) ? gates.warnings : [];
  const items = [
    ...failures.slice(0, 6).map((message) => ({ message, state: 'failed', label: 'BLOCKING' })),
    ...warnings.slice(0, Math.max(0, 6 - failures.length)).map(
      (message) => ({ message, state: 'warning', label: 'WARNING' }),
    ),
  ];
  const empty = gates.passed === true
    ? 'Every reported proof and apply gate passed.'
    : gates.passed === false
      ? 'The latest gate did not pass, but no detailed message was recorded.'
      : 'No validation or changed-code proof has run yet.';
  replaceStatusList(
    'gate-list',
    items,
    empty,
    (item) => statusItem(item.label, item.message, gates.source || 'Latest gate', item.state),
  );
}

function renderUsage(usage, generatedAt) {
  setText('total-commands', number.format(usage.totalCommands));
  setText('success-rate', `${number.format(usage.successRatePercent)}%`);
  setText('projects-seen', number.format(usage.projectsSeen));
  setText('average-duration', usage.averageCommandDurationMillis >= 1000
    ? `${number.format(usage.averageCommandDurationMillis / 1000)} s`
    : `${number.format(usage.averageCommandDurationMillis)} ms`);
  setText('last-updated', `Updated ${relativeTime(generatedAt)}`);
}

function renderCommands(commands) {
  const root = byId('command-bars');
  if (!root) return;
  root.replaceChildren();
  const visible = commands.slice(0, 6);
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
    bar.style.setProperty('--width', `${Math.max(4, item.count / maximum * 100)}%`);
    track.append(bar);
    const count = document.createElement('b');
    count.textContent = number.format(item.count);
    row.append(label, track, count);
    root.append(row);
  }
  if (!visible.length) {
    const empty = document.createElement('div');
    empty.className = 'empty-state';
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
    const dot = document.createElement('span');
    dot.className = 'activity-dot';
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
    row.append(dot, detail, time);
    root.append(row);
  }
  if (!activity.length) {
    const empty = document.createElement('li');
    empty.className = 'empty-state';
    empty.textContent = 'JAIPilot activity will appear here.';
    root.append(empty);
  }
}

async function refresh() {
  try {
    const response = await fetch('/api/metrics', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const metrics = await response.json();
    renderImpact(metrics.impact);
    renderEvidence(metrics.latestEvidence);
    renderUsage(metrics.usage, metrics.generatedAt);
    renderCommands(metrics.commands);
    renderActivity(metrics.recentActivity);
  } catch (error) {
    setText('last-updated', 'Metrics temporarily unavailable');
  }
}

fetch('/api/health', { cache: 'no-store' })
  .then((response) => response.json())
  .then((health) => setText('dashboard-version', `JAIPilot ${health.version} · localhost only`))
  .catch(() => setText('dashboard-version', 'JAIPilot · localhost only'));

refresh();
setInterval(refresh, 3000);
