package com.jaipilot.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class JAIPilotExtension {
  public abstract ListProperty<String> getForwardProperties();

  public abstract Property<Double> getLineCoverage();

  public abstract Property<Double> getBranchCoverage();

  public abstract Property<String> getExecutable();

  public abstract Property<Integer> getMaxIterations();

  public abstract Property<Integer> getTimeoutSeconds();
}
