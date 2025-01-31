/*
 * Copyright (C) 2011 Whisper Systems
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
package org.entanglemessenger.entangle.sms;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import org.entanglemessenger.entangle.ApplicationContext;
import org.entanglemessenger.entangle.attachments.Attachment;
import org.entanglemessenger.entangle.database.Address;
import org.entanglemessenger.entangle.database.AttachmentDatabase;
import org.entanglemessenger.entangle.database.DatabaseFactory;
import org.entanglemessenger.entangle.database.MmsDatabase;
import org.entanglemessenger.entangle.database.RecipientDatabase;
import org.entanglemessenger.entangle.database.SmsDatabase;
import org.entanglemessenger.entangle.database.ThreadDatabase;
import org.entanglemessenger.entangle.database.model.MessageRecord;
import org.entanglemessenger.entangle.database.model.MmsMessageRecord;
import org.entanglemessenger.entangle.jobmanager.JobManager;
import org.entanglemessenger.entangle.jobs.MmsSendJob;
import org.entanglemessenger.entangle.jobs.PushGroupSendJob;
import org.entanglemessenger.entangle.jobs.PushMediaSendJob;
import org.entanglemessenger.entangle.jobs.PushTextSendJob;
import org.entanglemessenger.entangle.jobs.SmsSendJob;
import org.entanglemessenger.entangle.mms.MmsException;
import org.entanglemessenger.entangle.mms.OutgoingMediaMessage;
import org.entanglemessenger.entangle.push.AccountManagerFactory;
import org.entanglemessenger.entangle.recipients.Recipient;
import org.entanglemessenger.entangle.service.ExpiringMessageManager;
import org.entanglemessenger.entangle.util.TextSecurePreferences;
import org.entanglemessenger.entangle.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.io.IOException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  public static long send(final Context context,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms,
                          final SmsDatabase.InsertListener insertListener)
  {
    SmsDatabase database    = DatabaseFactory.getSmsDatabase(context);
    Recipient   recipient   = message.getRecipient();
    boolean     keyExchange = message.isKeyExchange();

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    } else {
      allocatedThreadId = threadId;
    }

    long messageId = database.insertMessageOutbox(allocatedThreadId, message, forceSms, System.currentTimeMillis(), insertListener);

    sendTextMessage(context, recipient, forceSms, keyExchange, messageId, message.getExpiresIn());

    return allocatedThreadId;
  }

  public static long send(final Context context,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms,
                          final SmsDatabase.InsertListener insertListener)
  {
    try {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

      long allocatedThreadId;

      if (threadId == -1) {
        allocatedThreadId = threadDatabase.getThreadIdFor(message.getRecipient(), message.getDistributionType());
      } else {
        allocatedThreadId = threadId;
      }

      Recipient recipient = message.getRecipient();
      long      messageId = database.insertMessageOutbox(message, allocatedThreadId, forceSms, insertListener);

      sendMediaMessage(context, recipient, forceSms, messageId, message.getExpiresIn());

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static void resendGroupMessage(Context context, MessageRecord messageRecord, Address filterAddress) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");
    sendGroupPush(context, messageRecord.getRecipient(), messageRecord.getId(), filterAddress);
  }

  public static void resend(Context context, MessageRecord messageRecord) {
    try {
      long       messageId   = messageRecord.getId();
      boolean    forceSms    = messageRecord.isForcedSms();
      boolean    keyExchange = messageRecord.isKeyExchange();
      long       expiresIn   = messageRecord.getExpiresIn();
      Recipient  recipient   = messageRecord.getRecipient();

      if (messageRecord.isMms()) {
        sendMediaMessage(context, recipient, forceSms, messageId, expiresIn);
      } else {
        sendTextMessage(context, recipient, forceSms, keyExchange, messageId, expiresIn);
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private static void sendMediaMessage(Context context, Recipient recipient, boolean forceSms, long messageId, long expiresIn)
      throws MmsException
  {
    if (!forceSms && isSelfSend(context, recipient)) {
      sendMediaSelf(context, messageId, expiresIn);
    } else if (isGroupPushSend(recipient)) {
      sendGroupPush(context, recipient, messageId, null);
    } else if (!forceSms && isPushMediaSend(context, recipient)) {
      sendMediaPush(context, recipient, messageId);
    } else {
      sendMms(context, messageId);
    }
  }

  private static void sendTextMessage(Context context, Recipient recipient,
                                      boolean forceSms, boolean keyExchange,
                                      long messageId, long expiresIn)
  {
    if (!forceSms && isSelfSend(context, recipient)) {
      sendTextSelf(context, messageId, expiresIn);
    } else if (!forceSms && isPushTextSend(context, recipient, keyExchange)) {
      sendTextPush(context, recipient, messageId);
    } else {
      sendSms(context, recipient, messageId);
    }
  }

  private static void sendTextSelf(Context context, long messageId, long expiresIn) {
    SmsDatabase database = DatabaseFactory.getSmsDatabase(context);

    database.markAsSent(messageId, true);

    Pair<Long, Long> messageAndThreadId = database.copyMessageInbox(messageId);
    database.markAsPush(messageAndThreadId.first);

    if (expiresIn > 0) {
      ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, false, expiresIn);
    }
  }

  private static void sendMediaSelf(Context context, long messageId, long expiresIn)
      throws MmsException
  {
    ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase            database               = DatabaseFactory.getMmsDatabase(context);

    database.markAsSent(messageId, true);
    database.copyMessageInbox(messageId);
    markAttachmentsAsUploaded(messageId, database, DatabaseFactory.getAttachmentDatabase(context));

    if (expiresIn > 0) {
      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, true, expiresIn);
    }
  }

  private static void sendTextPush(Context context, Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushTextSendJob(context, messageId, recipient.getAddress()));
  }

  private static void sendMediaPush(Context context, Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushMediaSendJob(context, messageId, recipient.getAddress()));
  }

  private static void sendGroupPush(Context context, Recipient recipient, long messageId, Address filterAddress) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushGroupSendJob(context, messageId, recipient.getAddress(), filterAddress));
  }

  private static void sendSms(Context context, Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new SmsSendJob(context, messageId, recipient.getName()));
  }

  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new MmsSendJob(context, messageId));
  }

  private static boolean isPushTextSend(Context context, Recipient recipient, boolean keyExchange) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (keyExchange) {
      return false;
    }

    return isPushDestination(context, recipient);
  }

  private static boolean isPushMediaSend(Context context, Recipient recipient) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (recipient.isGroupRecipient()) {
      return false;
    }

    return isPushDestination(context, recipient);
  }

  private static boolean isGroupPushSend(Recipient recipient) {
    return recipient.getAddress().isGroup() &&
           !recipient.getAddress().isMmsGroup();
  }

  private static boolean isSelfSend(Context context, Recipient recipient) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (recipient.isGroupRecipient()) {
      return false;
    }

    return Util.isOwnNumber(context, recipient.getAddress());
  }

  private static boolean isPushDestination(Context context, Recipient destination) {
    if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
      return true;
    } else if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
      return false;
    } else {
      try {
        SignalServiceAccountManager   accountManager = AccountManagerFactory.createManager(context);
        Optional<ContactTokenDetails> registeredUser = accountManager.getContact(destination.getAddress().serialize());

        if (!registeredUser.isPresent()) {
          DatabaseFactory.getRecipientDatabase(context).setRegistered(destination, RecipientDatabase.RegisteredState.NOT_REGISTERED);
          return false;
        } else {
          DatabaseFactory.getRecipientDatabase(context).setRegistered(destination, RecipientDatabase.RegisteredState.REGISTERED);
          return true;
        }
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

  private static void markAttachmentsAsUploaded(long mmsId, @NonNull MmsDatabase mmsDatabase, @NonNull AttachmentDatabase attachmentDatabase) {
    try (MmsDatabase.Reader reader = mmsDatabase.readerFor(mmsDatabase.getMessage(mmsId))) {
      MessageRecord message = reader.getNext();

      if (message != null && message.isMms()) {
        for (Attachment attachment : ((MmsMessageRecord) message).getSlideDeck().asAttachments()) {
          attachmentDatabase.markAttachmentUploaded(mmsId, attachment);
        }
      }
    }
  }
}
