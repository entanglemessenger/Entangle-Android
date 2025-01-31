package org.entanglemessenger.entangle.jobs;

import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.entanglemessenger.entangle.BuildConfig;
import org.entanglemessenger.entangle.events.ReminderUpdateEvent;
import org.entanglemessenger.entangle.jobmanager.JobParameters;
import org.entanglemessenger.entangle.jobmanager.requirements.NetworkRequirement;
import org.entanglemessenger.entangle.transport.RetryLaterException;
import org.entanglemessenger.entangle.util.TextSecurePreferences;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServiceOutageDetectionJob extends ContextJob {

  private static final String TAG = ServiceOutageDetectionJob.class.getSimpleName();

  private static final String IP_SUCCESS = "127.0.0.1";
  private static final String IP_FAILURE = "127.0.0.2";
  private static final long   CHECK_TIME = 1000 * 60;

  public ServiceOutageDetectionJob(Context context) {
    super(context, new JobParameters.Builder()
                                    .withGroupId(ServiceOutageDetectionJob.class.getSimpleName())
                                    .withRequirement(new NetworkRequirement(context))
                                    .withRetryCount(5)
                                    .create());
  }

  @Override
  public void onAdded() {
  }

  @Override
  public void onRun() throws RetryLaterException {
    long timeSinceLastCheck = System.currentTimeMillis() - TextSecurePreferences.getLastOutageCheckTime(context);
    if (timeSinceLastCheck < CHECK_TIME) {
      Log.w(TAG, "Skipping service outage check. Too soon.");
      return;
    }

    try {
      InetAddress address = InetAddress.getByName(BuildConfig.SIGNAL_SERVICE_STATUS_URL);
      Log.i(TAG, "Received outage check address: " + address.getHostAddress());

      if (IP_SUCCESS.equals(address.getHostAddress())) {
        Log.i(TAG, "Service is available.");
        TextSecurePreferences.setServiceOutage(context, false);
      } else if (IP_FAILURE.equals(address.getHostAddress())) {
        Log.w(TAG, "Service is down.");
        TextSecurePreferences.setServiceOutage(context, true);
      } else {
        Log.w(TAG, "Service status check returned an unrecognized IP address. Could be a weird network state. Prompting retry.");
        throw new RetryLaterException(new Exception("Unrecognized service outage IP address."));
      }

      TextSecurePreferences.setLastOutageCheckTime(context, System.currentTimeMillis());
      EventBus.getDefault().post(new ReminderUpdateEvent());
    } catch (UnknownHostException e) {
      throw new RetryLaterException(e);
    }
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onCanceled() {
    Log.i(TAG, "Service status check could not complete. Assuming success to avoid false positives due to bad network.");
    TextSecurePreferences.setServiceOutage(context, false);
    TextSecurePreferences.setLastOutageCheckTime(context, System.currentTimeMillis());
    EventBus.getDefault().post(new ReminderUpdateEvent());
  }
}
