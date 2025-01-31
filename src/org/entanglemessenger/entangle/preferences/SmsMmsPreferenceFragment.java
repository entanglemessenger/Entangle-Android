package org.entanglemessenger.entangle.preferences;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import org.entanglemessenger.entangle.ApplicationPreferencesActivity;
import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.util.TextSecurePreferences;
import org.entanglemessenger.entangle.util.Util;

public class SmsMmsPreferenceFragment extends CorrectedPreferenceFragment {
  private static final String KITKAT_DEFAULT_PREF = "pref_set_default";
  private static final String MMS_PREF            = "pref_mms_preferences";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);


    this.findPreference(MMS_PREF)
      .setOnPreferenceClickListener(new ApnPreferencesClickListener());

    initializePlatformSpecificOptions();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_sms_mms);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__sms_mms);

    initializeDefaultPreference();
  }

  private void initializePlatformSpecificOptions() {
    PreferenceScreen preferenceScreen    = getPreferenceScreen();
    Preference       defaultPreference   = findPreference(KITKAT_DEFAULT_PREF);
    Preference       allSmsPreference    = findPreference(TextSecurePreferences.ALL_SMS_PREF);
    Preference       allMmsPreference    = findPreference(TextSecurePreferences.ALL_MMS_PREF);
    Preference       manualMmsPreference = findPreference(MMS_PREF);

    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      if (allSmsPreference != null) preferenceScreen.removePreference(allSmsPreference);
      if (allMmsPreference != null) preferenceScreen.removePreference(allMmsPreference);
    } else if (defaultPreference != null) {
      preferenceScreen.removePreference(defaultPreference);
    }

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && manualMmsPreference != null) {
      preferenceScreen.removePreference(manualMmsPreference);
    }
  }

  private void initializeDefaultPreference() {
    if (VERSION.SDK_INT < VERSION_CODES.KITKAT) return;

    Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
    if (Util.isDefaultSmsProvider(getActivity())) {
      defaultPreference.setIntent(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
      defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_enabled));
      defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_change_your_default_sms_app));
    } else {
      if(Build.VERSION.SDK_INT < VERSION_CODES.Q){
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getActivity().getPackageName());
        defaultPreference.setIntent(intent);
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_disabled));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_make_signal_your_default_sms_app));
      } else {
        RoleManager roleManager = getActivity().getSystemService(RoleManager.class);
        if(roleManager.isRoleAvailable(RoleManager.ROLE_SMS)){
          if(!roleManager.isRoleHeld(RoleManager.ROLE_SMS)){
            Intent roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
            defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_disabled));
            defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_make_signal_your_default_sms_app));

            defaultPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
              @Override
              public boolean onPreferenceClick(Preference preference) {
                getActivity().startActivityForResult(roleRequestIntent, 5555);
                return true;
              }
            });
          }
        }
      }
    }
  }

  private class ApnPreferencesClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      Fragment            fragment            = new MmsPreferencesFragment();
      FragmentManager     fragmentManager     = getActivity().getSupportFragmentManager();
      FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
      fragmentTransaction.replace(android.R.id.content, fragment);
      fragmentTransaction.addToBackStack(null);
      fragmentTransaction.commit();

      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final String on                 = context.getString(R.string.ApplicationPreferencesActivity_on);
    final String onCaps             = context.getString(R.string.ApplicationPreferencesActivity_On);
    final String off                = context.getString(R.string.ApplicationPreferencesActivity_off);
    final String offCaps            = context.getString(R.string.ApplicationPreferencesActivity_Off);
    final int    smsMmsSummaryResId = R.string.ApplicationPreferencesActivity_sms_mms_summary;

    boolean postKitkatSMS = Util.isDefaultSmsProvider(context);
    boolean preKitkatSMS  = TextSecurePreferences.isInterceptAllSmsEnabled(context);
    boolean preKitkatMMS  = TextSecurePreferences.isInterceptAllMmsEnabled(context);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      if (postKitkatSMS)                      return onCaps;
      else                                    return offCaps;
    } else {
      if      (preKitkatSMS && preKitkatMMS)  return onCaps;
      else if (preKitkatSMS && !preKitkatMMS) return context.getString(smsMmsSummaryResId, on,  off);
      else if (!preKitkatSMS && preKitkatMMS) return context.getString(smsMmsSummaryResId, off, on);
      else                                    return offCaps;
    }
  }
}
