package org.entanglemessenger.entangle.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.WebRtcCallActivity;
import org.entanglemessenger.entangle.recipients.Recipient;
import org.entanglemessenger.entangle.service.WebRtcCallService;

/**
 * Manages the state of the WebRtc items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallNotificationBuilder {

  public static final int WEBRTC_NOTIFICATION   = 313388;

  public static final int TYPE_INCOMING_RINGING    = 1;
  public static final int TYPE_OUTGOING_RINGING    = 2;
  public static final int TYPE_ESTABLISHED         = 3;
  public static final int TYPE_INCOMING_CONNECTING = 4;


  public static Notification getCallInProgressNotification(Context context, int type, Recipient recipient) {
    Intent contentIntent = new Intent(context, WebRtcCallActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.app_name))
        .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
        .setContentIntent(pendingIntent)
        .setOngoing(true);

    if(recipient.getName() != null){
      builder.setContentTitle(recipient.getName());
    } else if (!TextUtils.isEmpty(recipient.getProfileName())) {
      builder.setContentTitle(recipient.getAddress().serialize() + " (~" + recipient.getProfileName() + ")");
    } else {
      builder.setContentTitle(recipient.getAddress().serialize());
    }

    if (type == TYPE_INCOMING_CONNECTING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__incoming_signal_call));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setChannelId(context.getString(R.string.other_notification_channel_id));
      builder.setContentIntent(null);
    } else if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__incoming_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_DENY_CALL, R.drawable.ic_close_grey600_32dp,   R.string.NotificationBarManager__deny_call));
      builder.addAction(getActivityNotificationAction(context, WebRtcCallActivity.ANSWER_ACTION, R.drawable.ic_phone_grey600_32dp, R.string.NotificationBarManager__answer_call));
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
      builder.setChannelId(context.getString(R.string.other_notification_channel_id));
    } else {
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__end_call));
      builder.setChannelId(context.getString(R.string.other_notification_channel_id));
    }

    return builder.build();
  }

  private static NotificationCompat.Action getServiceNotificationAction(Context context, String action, int iconResId, int titleResId) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(action);

    PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);

    return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
  }

  private static NotificationCompat.Action getActivityNotificationAction(@NonNull Context context, @NonNull String action,
                                                                         @DrawableRes int iconResId, @StringRes int titleResId)
  {
    Intent intent = new Intent(context, WebRtcCallActivity.class);
    intent.setAction(action);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

    return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
  }
}
