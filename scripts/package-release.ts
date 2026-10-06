import { join } from "node:path";
import { VERSION } from "../cli/version.ts";

// Build a tap formula from the actual compiled release checksums, never guessed hashes.
const directory = Deno.args[0];
if (!directory) throw new Error("Usage: package-release.ts <release-assets-directory>");
const targets = [
  "aarch64-apple-darwin",
  "x86_64-apple-darwin",
  "aarch64-unknown-linux-gnu",
  "x86_64-unknown-linux-gnu",
];
const hashes: Record<string, string> = {};
for (const target of targets) {
  const checksum = (await Deno.readTextFile(join(directory, `jaipilot-${target}.sha256`)))
    .trim().split(/\s+/)[0];
  if (!/^[0-9a-f]{64}$/.test(checksum)) throw new Error(`Invalid checksum for ${target}`);
  hashes[target] = checksum;
}
function platform(os: string, suffix: string) {
  return `  on_${os} do
    if Hardware::CPU.arm?
      url "https://github.com/JAIPilot/jaipilot/releases/download/v${VERSION}/jaipilot-aarch64-${suffix}", using: :nounzip
      sha256 "${hashes[`aarch64-${suffix}`]}"
    else
      url "https://github.com/JAIPilot/jaipilot/releases/download/v${VERSION}/jaipilot-x86_64-${suffix}", using: :nounzip
      sha256 "${hashes[`x86_64-${suffix}`]}"
    end
  end`;
}
await Deno.writeTextFile(
  join(directory, "jaipilot.rb"),
  `class Jaipilot < Formula
  desc "Java testing workflows with a local CLI and MCP server"
  homepage "https://www.jaipilot.com"
  version "${VERSION}"
  license "MIT"

${platform("macos", "apple-darwin")}

${platform("linux", "unknown-linux-gnu")}

  def install
    bin.install Dir["jaipilot-*"].first => "jaipilot"
  end

  test do
    assert_equal "JAIPilot CLI ${VERSION}", shell_output("#{bin}/jaipilot --version").strip
    assert_match "jaipilot run", shell_output("#{bin}/jaipilot --help")
  end
end
`,
);
