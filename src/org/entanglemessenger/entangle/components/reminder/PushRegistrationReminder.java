package org.entanglemessenger.entangle.components.reminder;

import android.content.Context;
import android.content.Intent;
import android.view.View.OnClickListener;

import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.RegistrationActivity;
import org.entanglemessenger.entangle.util.TextSecurePreferences;

public class PushRegistrationReminder extends Reminder {

  public PushRegistrationReminder(final Context context) {
    super(context.getString(R.string.reminder_header_push_title),
          context.getString(R.string.reminder_header_push_text));

    final OnClickListener okListener = v -> {
      Intent intent = new Intent(context, RegistrationActivity.class);
      intent.putExtra(RegistrationActivity.RE_REGISTRATION_EXTRA, true);
      context.startActivity(intent);
    };

    setOkListener(okListener);
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible(Context context) {
    return !TextSecurePreferences.isPushRegistered(context);
  }
}
