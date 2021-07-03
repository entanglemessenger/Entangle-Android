package org.entanglemessenger.entangle;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import org.entanglemessenger.entangle.contactshare.Contact;
import org.entanglemessenger.entangle.database.model.MessageRecord;
import org.entanglemessenger.entangle.database.model.MmsMessageRecord;
import org.entanglemessenger.entangle.mms.GlideRequests;
import org.entanglemessenger.entangle.recipients.Recipient;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MessageRecord      messageRecord,
            @NonNull GlideRequests      glideRequests,
            @NonNull Locale             locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipient          recipients,
                     boolean            pulseHighlight);

  MessageRecord getMessageRecord();

  void setEventListener(@Nullable EventListener listener);

  interface EventListener {
    void onQuoteClicked(MmsMessageRecord messageRecord);
    void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView);
    void onAddToContactsClicked(@NonNull Contact contact);
    void onMessageSharedContactClicked(@NonNull List<Recipient> choices);
    void onInviteSharedContactClicked(@NonNull List<Recipient> choices);
  }
}
