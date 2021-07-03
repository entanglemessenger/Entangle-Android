package org.entanglemessenger.entangle.jobs.requirements;

import android.content.Context;

import org.entanglemessenger.entangle.jobmanager.dependencies.ContextDependent;
import org.entanglemessenger.entangle.jobmanager.requirements.SimpleRequirement;
import org.entanglemessenger.entangle.service.KeyCachingService;

public class MasterSecretRequirement extends SimpleRequirement implements ContextDependent {

  private transient Context context;

  public MasterSecretRequirement(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return KeyCachingService.getMasterSecret(context) != null;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
