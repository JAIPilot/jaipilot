import { join } from "node:path";
import { readFile, writeFile } from "node:fs/promises";
const VERSION = (await readFile(new URL("../VERSION", import.meta.url), "utf8")).trim();

// Build a tap formula from the actual compiled release checksums, never guessed hashes.
const directory = process.argv[2];
if (!directory) throw new Error("Usage: package-release.ts <release-assets-directory>");
const targets = [
  "aarch64-apple-darwin",
  "x86_64-apple-darwin",
  "aarch64-unknown-linux-gnu",
  "x86_64-unknown-linux-gnu",
];
const hashes = {};
for (const target of targets) {
  for (const prefix of ["jaipilot", "jaipilot-notices"]) {
    const asset = `${prefix}-${target}${prefix === "jaipilot-notices" ? ".zip" : ".gz"}`;
    const checksum = (await readFile(join(directory, `${asset}.sha256`), "utf8"))
      .trim().split(/\s+/)[0];
    if (!/^[0-9a-f]{64}$/.test(checksum)) throw new Error(`Invalid checksum for ${asset}`);
    hashes[asset] = checksum;
  }
}
function platform(os, suffix) {
  return `  on_${os} do
    if Hardware::CPU.arm?
      url "https://github.com/JAIPilot/jaipilot/releases/download/v${VERSION}/jaipilot-aarch64-${suffix}.gz", using: :nounzip
      sha256 "${hashes[`jaipilot-aarch64-${suffix}.gz`]}"
      resource "notices" do
        url "https://github.com/JAIPilot/jaipilot/releases/download/v${VERSION}/jaipilot-notices-aarch64-${suffix}.zip"
        sha256 "${hashes[`jaipilot-notices-aarch64-${suffix}.zip`]}"
      end
    else
      url "https://github.com/JAIPilot/jaipilot/releases/download/v${VERSION}/jaipilot-x86_64-${suffix}.gz", using: :nounzip
      sha256 "${hashes[`jaipilot-x86_64-${suffix}.gz`]}"
      resource "notices" do
        url "https://github.com/JAIPilot/jaipilot/releases/download/v${VERSION}/jaipilot-notices-x86_64-${suffix}.zip"
        sha256 "${hashes[`jaipilot-notices-x86_64-${suffix}.zip`]}"
      end
    end
  end`;
}
await writeFile(
  join(directory, "jaipilot.rb"),
  `class Jaipilot < Formula
  desc "Java testing workflows with a local CLI and MCP server"
  homepage "https://www.jaipilot.com"
  version "${VERSION}"
  license "MIT"

${platform("macos", "apple-darwin")}

${platform("linux", "unknown-linux-gnu")}

  def install
    archive = Dir["jaipilot-*.gz"].first
    system "gzip", "-d", archive
    binary = archive.delete_suffix(".gz")
    chmod 0755, binary
    bin.install binary => "jaipilot"
    resource("notices").stage do
      (share/"jaipilot").install "LICENSE", "THIRD_PARTY_NOTICES.md", "licenses"
    end
  end

  test do
    assert_equal "JAIPilot CLI ${VERSION}", shell_output("#{bin}/jaipilot --version").strip
    assert_match "jaipilot run", shell_output("#{bin}/jaipilot --help")
  end
end
`,
);
