/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.entanglemessenger.entangle.jobs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.entanglemessenger.entangle.PlayServicesProblemActivity;
import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.dependencies.InjectableType;
import org.entanglemessenger.entangle.jobmanager.JobParameters;
import org.entanglemessenger.entangle.jobmanager.requirements.NetworkRequirement;
import org.entanglemessenger.entangle.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import javax.inject.Inject;

public class GcmRefreshJob extends ContextJob implements InjectableType {

  private static final String TAG = GcmRefreshJob.class.getSimpleName();

  public static final String REGISTRATION_ID = "585370220617";

  @Inject transient SignalServiceAccountManager textSecureAccountManager;

  public GcmRefreshJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withRetryCount(1)
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws Exception {
    if (TextSecurePreferences.isGcmDisabled(context)) return;

    Log.w(TAG, "Reregistering GCM...");
    int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);

    if (result != ConnectionResult.SUCCESS) {
      notifyGcmFailure();
    } else {
      String gcmId = GoogleCloudMessaging.getInstance(context).register(REGISTRATION_ID);
      textSecureAccountManager.setGcmId(Optional.of(gcmId));

      TextSecurePreferences.setGcmRegistrationId(context, gcmId);
      TextSecurePreferences.setGcmRegistrationIdLastSetTime(context, System.currentTimeMillis());
      TextSecurePreferences.setWebsocketRegistered(context, true);
    }
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "GCM reregistration failed after retry attempt exhaustion!");
  }

  @Override
  public boolean onShouldRetry(Exception throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    return true;
  }

  private void notifyGcmFailure() {
    Intent                     intent        = new Intent(context, PlayServicesProblemActivity.class);
    PendingIntent              pendingIntent = PendingIntent.getActivity(context, 1122, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    // ENT
    NotificationCompat.Builder builder       = new NotificationCompat.Builder(context, context.getString(R.string.app_name));

    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                                      R.drawable.ic_action_warning_red));
    builder.setContentTitle(context.getString(R.string.GcmRefreshJob_Permanent_Signal_communication_failure));
    builder.setContentText(context.getString(R.string.GcmRefreshJob_Signal_was_unable_to_register_with_Google_Play_Services));
    builder.setTicker(context.getString(R.string.GcmRefreshJob_Permanent_Signal_communication_failure));
    builder.setVibrate(new long[] {0, 1000});
    builder.setContentIntent(pendingIntent);

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(12, builder.build());
  }

}
