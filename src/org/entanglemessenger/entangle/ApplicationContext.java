/*
 * Copyright (C) 2013 Open Whisper Systems
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
package org.entanglemessenger.entangle;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.support.multidex.MultiDexApplication;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.security.ProviderInstaller;

import org.entanglemessenger.entangle.crypto.PRNGFixes;
import org.entanglemessenger.entangle.dependencies.AxolotlStorageModule;
import org.entanglemessenger.entangle.dependencies.InjectableType;
import org.entanglemessenger.entangle.dependencies.SignalCommunicationModule;
import org.entanglemessenger.entangle.jobmanager.JobManager;
import org.entanglemessenger.entangle.jobmanager.dependencies.DependencyInjector;
import org.entanglemessenger.entangle.jobmanager.persistence.JavaJobSerializer;
import org.entanglemessenger.entangle.jobmanager.requirements.NetworkRequirementProvider;
import org.entanglemessenger.entangle.jobs.CreateSignedPreKeyJob;
import org.entanglemessenger.entangle.jobs.GcmRefreshJob;
import org.entanglemessenger.entangle.jobs.requirements.MasterSecretRequirementProvider;
import org.entanglemessenger.entangle.jobs.requirements.ServiceRequirementProvider;
import org.entanglemessenger.entangle.jobs.requirements.SqlCipherMigrationRequirementProvider;
import org.entanglemessenger.entangle.push.SignalServiceNetworkAccess;
import org.entanglemessenger.entangle.service.DirectoryRefreshListener;
import org.entanglemessenger.entangle.service.ExpiringMessageManager;
import org.entanglemessenger.entangle.service.LocalBackupListener;
import org.entanglemessenger.entangle.service.RotateSignedPreKeyListener;
import org.entanglemessenger.entangle.service.UpdateApkRefreshListener;
import org.entanglemessenger.entangle.util.TextSecurePreferences;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
import org.whispersystems.libsignal.util.AndroidSignalProtocolLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DependencyInjector {

  private static final String TAG = ApplicationContext.class.getName();

  private ExpiringMessageManager expiringMessageManager;
  private JobManager             jobManager;
  private ObjectGraph            objectGraph;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    initializeRandomNumberFix();
    initializeLogging();
    initializeDependencyInjection();
    initializeJobManager();
    initializeExpiringMessageManager();
    initializeGcmCheck();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeCircumvention();
    //initializeWebRtc();

    // ENT set notification channels
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

      // Default Channel - used for actionable items, so we want these notifications to be intrusive
      NotificationChannel mChannel = new NotificationChannel(this.getString(R.string.app_name), this.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
      mChannel.enableLights(true);
      mChannel.setVibrationPattern(new long[]{100, 100, 100});
      mNotificationManager.createNotificationChannel(mChannel);

      // Silent Channel - used for non-actionable items, so we want these notifications to be out of sight
      NotificationChannel silentChannel = new NotificationChannel(
              this.getString(R.string.other_notification_channel_id),
              this.getString(R.string.ContactShareEditActivity_type_missing),
              NotificationManager.IMPORTANCE_MIN);
      mNotificationManager.createNotificationChannel(silentChannel);
    }
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("EntangleJobs")
                                .withDependencyInjector(this)
                                .withJobSerializer(new JavaJobSerializer())
                                .withRequirementProviders(new MasterSecretRequirementProvider(this),
                                                          new ServiceRequirementProvider(this),
                                                          new NetworkRequirementProvider(this),
                                                          new SqlCipherMigrationRequirementProvider())
                                .withConsumerThreads(5)
                                .build();
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new SignalCommunicationModule(this, new SignalServiceNetworkAccess(this)),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getGcmRegistrationIdLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getGcmRegistrationId(this) == null || nextSetTime <= System.currentTimeMillis()) {
        this.jobManager.add(new GcmRefreshJob(this));
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      PeerConnectionFactory.initialize(InitializationOptions.builder(this)
                                                            .setEnableVideoHwAcceleration(true)
                                                            .createInitializationOptions());
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

}
