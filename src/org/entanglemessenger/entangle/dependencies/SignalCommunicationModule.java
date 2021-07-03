package org.entanglemessenger.entangle.dependencies;

import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.entanglemessenger.entangle.BuildConfig;
import org.entanglemessenger.entangle.CreateProfileActivity;
import org.entanglemessenger.entangle.DeviceListFragment;
import org.entanglemessenger.entangle.crypto.storage.SignalProtocolStoreImpl;
import org.entanglemessenger.entangle.events.ReminderUpdateEvent;
import org.entanglemessenger.entangle.jobs.AttachmentDownloadJob;
import org.entanglemessenger.entangle.jobs.AvatarDownloadJob;
import org.entanglemessenger.entangle.jobs.CleanPreKeysJob;
import org.entanglemessenger.entangle.jobs.CreateSignedPreKeyJob;
import org.entanglemessenger.entangle.jobs.GcmRefreshJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceBlockedUpdateJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceContactUpdateJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceGroupUpdateJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceProfileKeyUpdateJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceReadReceiptUpdateJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceReadUpdateJob;
import org.entanglemessenger.entangle.jobs.MultiDeviceVerifiedUpdateJob;
import org.entanglemessenger.entangle.jobs.PushGroupSendJob;
import org.entanglemessenger.entangle.jobs.PushGroupUpdateJob;
import org.entanglemessenger.entangle.jobs.PushMediaSendJob;
import org.entanglemessenger.entangle.jobs.PushNotificationReceiveJob;
import org.entanglemessenger.entangle.jobs.PushTextSendJob;
import org.entanglemessenger.entangle.jobs.RefreshAttributesJob;
import org.entanglemessenger.entangle.jobs.RefreshPreKeysJob;
import org.entanglemessenger.entangle.jobs.RequestGroupInfoJob;
import org.entanglemessenger.entangle.jobs.RetrieveProfileAvatarJob;
import org.entanglemessenger.entangle.jobs.RetrieveProfileJob;
import org.entanglemessenger.entangle.jobs.RotateSignedPreKeyJob;
import org.entanglemessenger.entangle.jobs.SendReadReceiptJob;
import org.entanglemessenger.entangle.preferences.AppProtectionPreferenceFragment;
import org.entanglemessenger.entangle.push.SecurityEventListener;
import org.entanglemessenger.entangle.push.SignalServiceNetworkAccess;
import org.entanglemessenger.entangle.service.MessageRetrievalService;
import org.entanglemessenger.entangle.service.WebRtcCallService;
import org.entanglemessenger.entangle.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     MultiDeviceBlockedUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class,
                                     RequestGroupInfoJob.class,
                                     PushGroupUpdateJob.class,
                                     AvatarDownloadJob.class,
                                     RotateSignedPreKeyJob.class,
                                     WebRtcCallService.class,
                                     RetrieveProfileJob.class,
                                     MultiDeviceVerifiedUpdateJob.class,
                                     CreateProfileActivity.class,
                                     RetrieveProfileAvatarJob.class,
                                     MultiDeviceProfileKeyUpdateJob.class,
                                     SendReadReceiptJob.class,
                                     MultiDeviceReadReceiptUpdateJob.class,
                                     AppProtectionPreferenceFragment.class})
public class SignalCommunicationModule {

  private static final String TAG = SignalCommunicationModule.class.getSimpleName();

  private final Context                      context;
  private final SignalServiceNetworkAccess   networkAccess;

  private SignalServiceAccountManager  accountManager;
  private SignalServiceMessageSender   messageSender;
  private SignalServiceMessageReceiver messageReceiver;

  public SignalCommunicationModule(Context context, SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;
  }

  @Provides
  synchronized SignalServiceAccountManager provideSignalAccountManager() {
    if (this.accountManager == null) {
      this.accountManager = new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                                            new DynamicCredentialsProvider(context),
                                                            BuildConfig.USER_AGENT);
    }

    return this.accountManager;
  }

  @Provides
  synchronized SignalServiceMessageSender provideSignalMessageSender() {
    if (this.messageSender == null) {
      this.messageSender = new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                                          new DynamicCredentialsProvider(context),
                                                          new SignalProtocolStoreImpl(context),
                                                          BuildConfig.USER_AGENT,
                                                          Optional.fromNullable(MessageRetrievalService.getPipe()),
                                                          Optional.of(new SecurityEventListener(context)));
    } else {
      this.messageSender.setMessagePipe(MessageRetrievalService.getPipe());
    }

    return this.messageSender;
  }

  @Provides
  synchronized SignalServiceMessageReceiver provideSignalMessageReceiver() {
    if (this.messageReceiver == null) {
      this.messageReceiver = new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                                              new DynamicCredentialsProvider(context),
                                                              BuildConfig.USER_AGENT,
                                                              new PipeConnectivityListener());
    }

    return this.messageReceiver;
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.w(TAG, "onConnected()");
    }

    @Override
    public void onConnecting() {
      Log.w(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
      EventBus.getDefault().post(new ReminderUpdateEvent());
    }

  }

}
