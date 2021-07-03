package org.entanglemessenger.entangle.jobmanager.requirements;

import android.support.annotation.NonNull;

import org.entanglemessenger.entangle.jobmanager.Job;

public abstract class SimpleRequirement implements Requirement {

  @Override
  public boolean isPresent(@NonNull Job job) {
    return isPresent();
  }

  @Override
  public void onRetry(@NonNull Job job) {
  }

  public abstract boolean isPresent();
}
