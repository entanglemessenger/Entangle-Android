package org.entanglemessenger.entangle.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.entanglemessenger.entangle.attachments.Attachment;
import org.entanglemessenger.entangle.contactshare.Contact;
import org.entanglemessenger.entangle.recipients.Recipient;

import java.util.List;

public class OutgoingSecureMediaMessage extends OutgoingMediaMessage {

  public OutgoingSecureMediaMessage(Recipient recipient, String body,
                                    List<Attachment> attachments,
                                    long sentTimeMillis,
                                    int distributionType,
                                    long expiresIn,
                                    @Nullable QuoteModel quote,
                                    @NonNull List<Contact> contacts)
  {
    super(recipient, body, attachments, sentTimeMillis, -1, expiresIn, distributionType, quote, contacts);
  }

  public OutgoingSecureMediaMessage(OutgoingMediaMessage base) {
    super(base);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
