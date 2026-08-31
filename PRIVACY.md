# Privacy policy

Effective: August 31, 2026

JAIPilot bundles static Java engineering skills and an optional hosted remote-execution MCP. Using
the skills locally does not send repository contents to JAIPilot. The plugin has no advertising,
cookies, or product-analytics SDK.

The coding host and repository tools remain separate systems. The host may read files and run
commands under its own privacy policy and the user's configuration. Maven, Gradle, Git, analyzers,
plugins, and dependencies may access local or network resources according to their configuration.

When the user connects JAIPilot Remote:

- the user signs in through JAIPilot's OAuth provider; JAIPilot processes the account email and a
  stable authentication user ID to enforce ownership, access, concurrency, and usage limits;
- signing in or reading a skill does not upload source or start compute;
- after repository-specific consent, the host may upload only a ZIP produced by `git archive` from
  one declared exact commit through a short-lived private S3 URL; staged, unstaged, untracked,
  ignored, and `.git` content is excluded;
- JAIPilot verifies account ownership, declared commit identity, archive byte length, and SHA-256
  before a build may start;
- the selected command, relative working directory, Java version, hardware profile, bounded output
  logs, source/build IDs, provider status, timestamps, exit code, and operational quota metadata
  pass through JAIPilot Cloud and its managed infrastructure providers;
- each attempt runs in a fresh AWS CodeBuild container with no persistent workspace or build cache;
- private source is deleted on terminal status or log reads and cancellation, with an S3 lifecycle
  rule as a one-day cleanup backstop; and
- JAIPilot does not place customer source in this public repository or a product analytics store.

The remote sandbox has outbound network access, so repository build code and dependencies may send
data elsewhere. Provider logs, backups, security controls, and retention are governed by Supabase,
Amazon Web Services, and any contacted dependency service. Bounded build logs can contain data that
the selected repository command prints; users and agents should avoid printing secrets or personal
data. Because the archive contains the selected commit, do not upload a commit containing
credentials, personal data, or sensitive source.

Remote execution is a bounded public beta. It is not a claim of private-VPC, regulated-data,
data-residency, or Zero Data Retention support. Do not use it for sensitive or proprietary source
unless the applicable provider terms and controls have been reviewed and accepted.

The public project is hosted on GitHub. GitHub processes visits, issues, discussions, and security
reports under its own privacy terms. Do not include secrets, proprietary source, or personal data in
public reports.

Questions may be raised through [JAIPilot Support](SUPPORT.md). Security concerns should use the
private reporting route in [SECURITY.md](SECURITY.md).

Material changes to this policy will be published in this repository and identified by their
effective date.
