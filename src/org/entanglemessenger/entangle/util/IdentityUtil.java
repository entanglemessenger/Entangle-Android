package org.entanglemessenger.entangle.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.util.Log;

import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.crypto.storage.TextSecureIdentityKeyStore;
import org.entanglemessenger.entangle.crypto.storage.TextSecureSessionStore;
import org.entanglemessenger.entangle.database.Address;
import org.entanglemessenger.entangle.database.DatabaseFactory;
import org.entanglemessenger.entangle.database.GroupDatabase;
import org.entanglemessenger.entangle.database.IdentityDatabase;
import org.entanglemessenger.entangle.database.IdentityDatabase.IdentityRecord;
import org.entanglemessenger.entangle.database.MessagingDatabase.InsertResult;
import org.entanglemessenger.entangle.database.SmsDatabase;
import org.entanglemessenger.entangle.notifications.MessageNotifier;
import org.entanglemessenger.entangle.recipients.Recipient;
import org.entanglemessenger.entangle.sms.IncomingIdentityDefaultMessage;
import org.entanglemessenger.entangle.sms.IncomingIdentityUpdateMessage;
import org.entanglemessenger.entangle.sms.IncomingIdentityVerifiedMessage;
import org.entanglemessenger.entangle.sms.IncomingTextMessage;
import org.entanglemessenger.entangle.sms.OutgoingIdentityDefaultMessage;
import org.entanglemessenger.entangle.sms.OutgoingIdentityVerifiedMessage;
import org.entanglemessenger.entangle.sms.OutgoingTextMessage;
import org.entanglemessenger.entangle.util.concurrent.ListenableFuture;
import org.entanglemessenger.entangle.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class IdentityUtil {

  private static final String TAG = IdentityUtil.class.getSimpleName();

  @SuppressLint("StaticFieldLeak")
  @UiThread
  public static ListenableFuture<Optional<IdentityRecord>> getRemoteIdentityKey(final Context context, final Recipient recipient) {
    final SettableFuture<Optional<IdentityRecord>> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Optional<IdentityRecord>>() {
      @Override
      protected Optional<IdentityRecord> doInBackground(Recipient... recipient) {
        return DatabaseFactory.getIdentityDatabase(context)
                              .getIdentity(recipient[0].getAddress());
      }

      @Override
      protected void onPostExecute(Optional<IdentityRecord> result) {
        future.set(result);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient);

    return future;
  }

  public static void markIdentityVerified(Context context, Recipient recipient, boolean verified, boolean remote)
  {
    long                 time          = System.currentTimeMillis();
    SmsDatabase          smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader        = groupDatabase.getGroups();

    GroupDatabase.GroupRecord groupRecord;

    while ((groupRecord = reader.getNext()) != null) {
      if (groupRecord.getMembers().contains(recipient.getAddress()) && groupRecord.isActive() && !groupRecord.isMms()) {
        SignalServiceGroup group = new SignalServiceGroup(groupRecord.getId());

        if (remote) {
          IncomingTextMessage incoming = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.of(group), 0);

          if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
          else          incoming = new IncomingIdentityDefaultMessage(incoming);

          smsDatabase.insertMessageInbox(incoming);
        } else {
          Recipient           groupRecipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(group.getGroupId(), false)), true);
          long                threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
          OutgoingTextMessage outgoing ;

          if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipient);
          else          outgoing = new OutgoingIdentityDefaultMessage(recipient);

          DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null);
        }
      }
    }

    if (remote) {
      IncomingTextMessage incoming = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.absent(), 0);

      if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
      else          incoming = new IncomingIdentityDefaultMessage(incoming);

      smsDatabase.insertMessageInbox(incoming);
    } else {
      OutgoingTextMessage outgoing;

      if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipient);
      else          outgoing = new OutgoingIdentityDefaultMessage(recipient);

      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

      Log.w(TAG, "Inserting verified outbox...");
      DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null);
    }
  }

  public static void markIdentityUpdate(Context context, Recipient recipient) {
    long                 time          = System.currentTimeMillis();
    SmsDatabase          smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader        = groupDatabase.getGroups();

    GroupDatabase.GroupRecord groupRecord;

    while ((groupRecord = reader.getNext()) != null) {
      if (groupRecord.getMembers().contains(recipient.getAddress()) && groupRecord.isActive()) {
        SignalServiceGroup            group       = new SignalServiceGroup(groupRecord.getId());
        IncomingTextMessage           incoming    = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.of(group), 0);
        IncomingIdentityUpdateMessage groupUpdate = new IncomingIdentityUpdateMessage(incoming);

        smsDatabase.insertMessageInbox(groupUpdate);
      }
    }

    IncomingTextMessage           incoming         = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.absent(), 0);
    IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
    Optional<InsertResult>        insertResult     = smsDatabase.insertMessageInbox(individualUpdate);

    if (insertResult.isPresent()) {
      MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
    }
  }

  public static void saveIdentity(Context context, String number, IdentityKey identityKey) {
    synchronized (SESSION_LOCK) {
      IdentityKeyStore      identityKeyStore = new TextSecureIdentityKeyStore(context);
      SessionStore          sessionStore     = new TextSecureSessionStore(context);
      SignalProtocolAddress address          = new SignalProtocolAddress(number, 1);

      if (identityKeyStore.saveIdentity(address, identityKey)) {
        if (sessionStore.containsSession(address)) {
          SessionRecord sessionRecord = sessionStore.loadSession(address);
          sessionRecord.archiveCurrentState();

          sessionStore.storeSession(address, sessionRecord);
        }
      }
    }
  }

  public static void processVerifiedMessage(Context context, VerifiedMessage verifiedMessage) {
    synchronized (SESSION_LOCK) {
      IdentityDatabase         identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      Recipient                recipient        = Recipient.from(context, Address.fromExternal(context, verifiedMessage.getDestination()), true);
      Optional<IdentityRecord> identityRecord   = identityDatabase.getIdentity(recipient.getAddress());

      if (!identityRecord.isPresent() && verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT) {
        Log.w(TAG, "No existing record for default status");
        return;
      }

      if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT              &&
          identityRecord.isPresent()                                                          &&
          identityRecord.get().getIdentityKey().equals(verifiedMessage.getIdentityKey())      &&
          identityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.DEFAULT)
      {
        identityDatabase.setVerified(recipient.getAddress(), identityRecord.get().getIdentityKey(), IdentityDatabase.VerifiedStatus.DEFAULT);
        markIdentityVerified(context, recipient, false, true);
      }

      if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.VERIFIED &&
          (!identityRecord.isPresent() ||
              (identityRecord.isPresent() && !identityRecord.get().getIdentityKey().equals(verifiedMessage.getIdentityKey())) ||
              (identityRecord.isPresent() && identityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.VERIFIED)))
      {
        saveIdentity(context, verifiedMessage.getDestination(), verifiedMessage.getIdentityKey());
        identityDatabase.setVerified(recipient.getAddress(), verifiedMessage.getIdentityKey(), IdentityDatabase.VerifiedStatus.VERIFIED);
        markIdentityVerified(context, recipient, true, true);
      }
    }
  }


  public static @Nullable String getUnverifiedBannerDescription(@NonNull Context context,
                                                                @NonNull List<Recipient> unverified)
  {
    return getPluralizedIdentityDescription(context, unverified,
                                            R.string.IdentityUtil_unverified_banner_one,
                                            R.string.IdentityUtil_unverified_banner_two,
                                            R.string.IdentityUtil_unverified_banner_many);
  }

  public static @Nullable String getUnverifiedSendDialogDescription(@NonNull Context context,
                                                                    @NonNull List<Recipient> unverified)
  {
    return getPluralizedIdentityDescription(context, unverified,
                                            R.string.IdentityUtil_unverified_dialog_one,
                                            R.string.IdentityUtil_unverified_dialog_two,
                                            R.string.IdentityUtil_unverified_dialog_many);
  }

  public static @Nullable String getUntrustedSendDialogDescription(@NonNull Context context,
                                                                   @NonNull List<Recipient> untrusted)
  {
    return getPluralizedIdentityDescription(context, untrusted,
                                            R.string.IdentityUtil_untrusted_dialog_one,
                                            R.string.IdentityUtil_untrusted_dialog_two,
                                            R.string.IdentityUtil_untrusted_dialog_many);
  }

  private static @Nullable String getPluralizedIdentityDescription(@NonNull Context context,
                                                                   @NonNull List<Recipient> recipients,
                                                                   @StringRes int resourceOne,
                                                                   @StringRes int resourceTwo,
                                                                   @StringRes int resourceMany)
  {
    if (recipients.isEmpty()) return null;

    if (recipients.size() == 1) {
      String name = recipients.get(0).toShortString();
      return context.getString(resourceOne, name);
    } else {
      String firstName  = recipients.get(0).toShortString();
      String secondName = recipients.get(1).toShortString();

      if (recipients.size() == 2) {
        return context.getString(resourceTwo, firstName, secondName);
      } else {
        String nMore;

        if (recipients.size() == 3) {
          nMore = context.getResources().getQuantityString(R.plurals.identity_others, 1);
        } else {
          nMore = context.getResources().getQuantityString(R.plurals.identity_others, recipients.size() - 2);
        }

        return context.getString(resourceMany, firstName, secondName, nMore);
      }
    }
  }
}
