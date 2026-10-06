package com.jaipilot.maven;

import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "run", aggregator = true, threadSafe = true)
public final class RunMojo extends AbstractCoverageMojo {
  @Override
  protected boolean checkOnly() {
    return false;
  }
}
