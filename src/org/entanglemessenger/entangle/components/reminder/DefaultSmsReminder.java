package org.entanglemessenger.entangle.components.reminder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.provider.Telephony;
import android.view.View;
import android.view.View.OnClickListener;

import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.util.TextSecurePreferences;
import org.entanglemessenger.entangle.util.Util;

public class DefaultSmsReminder extends Reminder {

  @TargetApi(VERSION_CODES.KITKAT)
  public DefaultSmsReminder(final Context context) {
    super(context.getString(R.string.reminder_header_sms_default_title),
          context.getString(R.string.reminder_header_sms_default_text));

    final OnClickListener okListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        if(Build.VERSION.SDK_INT < VERSION_CODES.Q){
          TextSecurePreferences.setPromptedDefaultSmsProvider(context, true);
          Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
          intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
          context.startActivity(intent);
        } else {
          RoleManager roleManager = context.getSystemService(RoleManager.class);
          if(roleManager.isRoleAvailable(RoleManager.ROLE_SMS)){
            if(!roleManager.isRoleHeld(RoleManager.ROLE_SMS)){
              Intent roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
              ((Activity)context).startActivityForResult(roleRequestIntent, 5555);
            }
          }
        }
      }
    };
    final OnClickListener dismissListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedDefaultSmsProvider(context, true);
      }
    };
    setOkListener(okListener);
    setDismissListener(dismissListener);
  }

  public static boolean isEligible(Context context) {
    final boolean isDefault = Util.isDefaultSmsProvider(context);
    if (isDefault) {
      TextSecurePreferences.setPromptedDefaultSmsProvider(context, false);
    }

    return !isDefault && !TextSecurePreferences.hasPromptedDefaultSmsProvider(context);
  }
}
