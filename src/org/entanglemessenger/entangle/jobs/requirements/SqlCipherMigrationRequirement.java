package org.entanglemessenger.entangle.jobs.requirements;


import android.content.Context;
import android.support.annotation.NonNull;

import org.entanglemessenger.entangle.jobmanager.dependencies.ContextDependent;
import org.entanglemessenger.entangle.jobmanager.requirements.SimpleRequirement;
import org.entanglemessenger.entangle.util.TextSecurePreferences;

public class SqlCipherMigrationRequirement extends SimpleRequirement implements ContextDependent {

  @SuppressWarnings("unused")
  private static final String TAG = SqlCipherMigrationRequirement.class.getSimpleName();

  private transient Context context;

  public SqlCipherMigrationRequirement(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return !TextSecurePreferences.getNeedsSqlCipherMigration(context);
  }
}
