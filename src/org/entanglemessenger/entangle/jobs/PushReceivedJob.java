package org.entanglemessenger.entangle.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.entanglemessenger.entangle.ApplicationContext;
import org.entanglemessenger.entangle.database.Address;
import org.entanglemessenger.entangle.database.DatabaseFactory;
import org.entanglemessenger.entangle.database.MessagingDatabase.SyncMessageId;
import org.entanglemessenger.entangle.database.RecipientDatabase;
import org.entanglemessenger.entangle.jobmanager.JobManager;
import org.entanglemessenger.entangle.jobmanager.JobParameters;
import org.entanglemessenger.entangle.recipients.Recipient;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public abstract class PushReceivedJob extends ContextJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  protected PushReceivedJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  public void handle(SignalServiceEnvelope envelope) {
    Address   source    = Address.fromExternal(context, envelope.getSource());
    Recipient recipient = Recipient.from(context, source, false);

    if (!isActiveNumber(recipient)) {
      DatabaseFactory.getRecipientDatabase(context).setRegistered(recipient, RecipientDatabase.RegisteredState.REGISTERED);
      ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context, recipient, false));
    }

    if (envelope.isReceipt()) {
      handleReceipt(envelope);
    } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage()) {
      handleMessage(envelope, source);
    } else {
      Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, Address source) {
    Recipient  recipients = Recipient.from(context, source, false);
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    if (!recipients.isBlocked()) {
      long messageId = DatabaseFactory.getPushDatabase(context).insert(envelope);
      jobManager.add(new PushDecryptJob(context, messageId));
    } else {
      Log.w(TAG, "*** Received blocked push message, ignoring...");
    }
  }

  private void handleReceipt(SignalServiceEnvelope envelope) {
    Log.w(TAG, String.format("Received receipt: (XXXXX, %d)", envelope.getTimestamp()));
    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(new SyncMessageId(Address.fromExternal(context, envelope.getSource()),
                                                                                               envelope.getTimestamp()), System.currentTimeMillis());
  }

  private boolean isActiveNumber(@NonNull Recipient recipient) {
    return recipient.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED;
  }


}
