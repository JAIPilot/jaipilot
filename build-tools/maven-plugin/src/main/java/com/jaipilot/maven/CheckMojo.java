package com.jaipilot.maven;

import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "check", aggregator = true, threadSafe = true)
public final class CheckMojo extends AbstractCoverageMojo {
  @Override
  protected boolean checkOnly() {
    return true;
  }
}
