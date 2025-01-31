package org.entanglemessenger.entangle.jobmanager.requirements;

import android.content.Context;
import android.support.annotation.NonNull;

import org.entanglemessenger.entangle.jobmanager.Job;
import org.entanglemessenger.entangle.jobmanager.dependencies.ContextDependent;

import java.util.concurrent.TimeUnit;

/**
 * Uses exponential backoff to re-schedule network jobs to be retried in the future.
 */
public class NetworkBackoffRequirement implements Requirement, ContextDependent {

  private static final long MAX_WAIT = TimeUnit.SECONDS.toMillis(30);

  private transient Context context;

  public NetworkBackoffRequirement(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public boolean isPresent(@NonNull Job job) {
    return new NetworkRequirement(context).isPresent() && System.currentTimeMillis() >= calculateNextRunTime(job);
  }

  @Override
  public void onRetry(@NonNull Job job) {
    if (!(new NetworkRequirement(context).isPresent())) {
      job.resetRunStats();
      return;
    }

    BackoffReceiver.setUniqueAlarm(context, NetworkBackoffRequirement.calculateNextRunTime(job));
  }

  @Override
  public void setContext(Context context) {
    this.context = context.getApplicationContext();
  }

  private static long calculateNextRunTime(@NonNull Job job) {
    long targetTime   = job.getLastRunTime() + (long) (Math.pow(2, job.getRunIteration() - 1) * 1000);
    long furthestTime = System.currentTimeMillis() + MAX_WAIT;

    return Math.min(targetTime, Math.min(furthestTime, job.getRetryUntil()));
  }
}
