package org.entanglemessenger.entangle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import org.entanglemessenger.entangle.crypto.storage.TextSecureIdentityKeyStore;
import org.entanglemessenger.entangle.database.Address;
import org.entanglemessenger.entangle.database.DatabaseFactory;
import org.entanglemessenger.entangle.database.MmsDatabase;
import org.entanglemessenger.entangle.database.MmsSmsDatabase;
import org.entanglemessenger.entangle.database.PushDatabase;
import org.entanglemessenger.entangle.database.SmsDatabase;
import org.entanglemessenger.entangle.database.documents.IdentityKeyMismatch;
import org.entanglemessenger.entangle.database.model.MessageRecord;
import org.entanglemessenger.entangle.jobs.PushDecryptJob;
import org.entanglemessenger.entangle.recipients.Recipient;
import org.entanglemessenger.entangle.sms.MessageSender;
import org.entanglemessenger.entangle.util.Base64;
import org.entanglemessenger.entangle.util.VerifySpan;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class ConfirmIdentityDialog extends AlertDialog {

  @SuppressWarnings("unused")
  private static final String TAG = ConfirmIdentityDialog.class.getSimpleName();

  private OnClickListener callback;

  public ConfirmIdentityDialog(Context context,
                               MessageRecord messageRecord,
                               IdentityKeyMismatch mismatch)
  {
    super(context);

      Recipient       recipient       = Recipient.from(context, mismatch.getAddress(), false);
      String          name            = recipient.toShortString();
      String          introduction    = String.format(context.getString(R.string.ConfirmIdentityDialog_your_safety_number_with_s_has_changed), name, name);
      SpannableString spannableString = new SpannableString(introduction + " " +
                                                            context.getString(R.string.ConfirmIdentityDialog_you_may_wish_to_verify_your_safety_number_with_this_contact));

      spannableString.setSpan(new VerifySpan(context, mismatch),
                              introduction.length()+1, spannableString.length(),
                              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

      setTitle(name);
      setMessage(spannableString);

      setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.ConfirmIdentityDialog_accept), new AcceptListener(messageRecord, mismatch, recipient.getAddress()));
      setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),               new CancelListener());
  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
                   .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {

    private final MessageRecord       messageRecord;
    private final IdentityKeyMismatch mismatch;
    private final Address             address;

    private AcceptListener(MessageRecord messageRecord, IdentityKeyMismatch mismatch, Address address) {
      this.messageRecord = messageRecord;
      this.mismatch      = mismatch;
      this.address       = address;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>()
      {
        @Override
        protected Void doInBackground(Void... params) {
          synchronized (SESSION_LOCK) {
            SignalProtocolAddress      mismatchAddress  = new SignalProtocolAddress(address.toPhoneString(), 1);
            TextSecureIdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(getContext());

            identityKeyStore.saveIdentity(mismatchAddress, mismatch.getIdentityKey(), true);
          }

          processMessageRecord(messageRecord);
          processPendingMessageRecords(messageRecord.getThreadId(), mismatch);

          return null;
        }

        private void processMessageRecord(MessageRecord messageRecord) {
          if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord);
          else                            processIncomingMessageRecord(messageRecord);
        }

        private void processPendingMessageRecords(long threadId, IdentityKeyMismatch mismatch) {
          MmsSmsDatabase        mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(getContext());
          Cursor                cursor         = mmsSmsDatabase.getIdentityConflictMessagesForThread(threadId);
          MmsSmsDatabase.Reader reader         = mmsSmsDatabase.readerFor(cursor);
          MessageRecord         record;

          try {
            while ((record = reader.getNext()) != null) {
              for (IdentityKeyMismatch recordMismatch : record.getIdentityKeyMismatches()) {
                if (mismatch.equals(recordMismatch)) {
                  processMessageRecord(record);
                }
              }
            }
          } finally {
            if (reader != null)
              reader.close();
          }
        }

        private void processOutgoingMessageRecord(MessageRecord messageRecord) {
          SmsDatabase        smsDatabase        = DatabaseFactory.getSmsDatabase(getContext());
          MmsDatabase        mmsDatabase        = DatabaseFactory.getMmsDatabase(getContext());

          if (messageRecord.isMms()) {
            mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getAddress(),
                                                 mismatch.getIdentityKey());

            if (messageRecord.getRecipient().isPushGroupRecipient()) {
              MessageSender.resendGroupMessage(getContext(), messageRecord, mismatch.getAddress());
            } else {
              MessageSender.resend(getContext(), messageRecord);
            }
          } else {
            smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getAddress(),
                                                 mismatch.getIdentityKey());

            MessageSender.resend(getContext(), messageRecord);
          }
        }

        private void processIncomingMessageRecord(MessageRecord messageRecord) {
          try {
            PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(getContext());
            SmsDatabase  smsDatabase  = DatabaseFactory.getSmsDatabase(getContext());

            smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getAddress(),
                                                 mismatch.getIdentityKey());

            boolean legacy = !messageRecord.isContentBundleKeyExchange();

            SignalServiceEnvelope envelope = new SignalServiceEnvelope(SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
                                                                       messageRecord.getIndividualRecipient().getAddress().toPhoneString(),
                                                                       messageRecord.getRecipientDeviceId(), "",
                                                                       messageRecord.getDateSent(),
                                                                       legacy ? Base64.decode(messageRecord.getBody()) : null,
                                                                       !legacy ? Base64.decode(messageRecord.getBody()) : null);

            long pushId = pushDatabase.insert(envelope);

            ApplicationContext.getInstance(getContext())
                              .getJobManager()
                              .add(new PushDecryptJob(getContext(), pushId, messageRecord.getId()));
          } catch (IOException e) {
            throw new AssertionError(e);
          }
        }

      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

      if (callback != null) callback.onClick(null, 0);
    }
  }

  private class CancelListener implements OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }

}
