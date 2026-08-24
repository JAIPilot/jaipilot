# Privacy policy

Effective: August 25, 2026

JAIPilot bundles static Java engineering skills and an optional hosted remote-execution MCP. Using
the skills locally does not send repository contents to JAIPilot. The plugin has no advertising,
cookies, or product-analytics SDK.

The coding host and repository tools remain separate systems. The host may read files and run
commands under its own privacy policy and the user's configuration. Maven, Gradle, Git, analyzers,
plugins, and dependencies may access local or network resources according to their configuration.

When the user or host agent explicitly invokes JAIPilot Remote:

- the user signs in through jaipilot.com and grants the coding host an OAuth connection;
- the host may explicitly upload tracked and unignored staged, unstaged, and untracked repository
  files through a short-lived private upload URL;
- JAIPilot verifies ownership, archive size, and SHA-256 before extracting the source into a
  temporary private workspace;
- command inputs, bounded output logs, account/workspace metadata, and operational metadata pass
  through JAIPilot Cloud and its managed infrastructure providers;
- the uploaded archive is deleted after workspace preparation or failure;
- the workspace and its same-workspace caches are deleted on explicit cleanup or hard TTL; and
- JAIPilot does not place customer source in this public repository or a product analytics store.

The remote sandbox has outbound network access, so repository build code and dependencies may send
data elsewhere. Provider logs, backups, security controls, and retention are governed by Supabase,
JAIPilot's managed execution provider, and any contacted dependency service.

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
