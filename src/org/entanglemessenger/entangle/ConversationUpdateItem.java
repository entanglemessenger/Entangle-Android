package org.entanglemessenger.entangle;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.entanglemessenger.entangle.crypto.IdentityKeyParcelable;
import org.entanglemessenger.entangle.database.IdentityDatabase;
import org.entanglemessenger.entangle.database.IdentityDatabase.IdentityRecord;
import org.entanglemessenger.entangle.database.model.MessageRecord;
import org.entanglemessenger.entangle.mms.GlideRequests;
import org.entanglemessenger.entangle.recipients.Recipient;
import org.entanglemessenger.entangle.recipients.RecipientModifiedListener;
import org.entanglemessenger.entangle.util.DateUtils;
import org.entanglemessenger.entangle.util.GroupUtil;
import org.entanglemessenger.entangle.util.IdentityUtil;
import org.entanglemessenger.entangle.util.Util;
import org.entanglemessenger.entangle.util.concurrent.ListenableFuture;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ConversationUpdateItem extends LinearLayout
    implements RecipientModifiedListener, BindableConversationItem
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private Set<MessageRecord> batchSelected;

  private ImageView     icon;
  private TextView      body;
  private TextView      date;
  private Recipient     sender;
  private MessageRecord messageRecord;
  private Locale        locale;

  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.icon = findViewById(R.id.conversation_update_icon);
    this.body = findViewById(R.id.conversation_update_body);
    this.date = findViewById(R.id.conversation_update_date);

    this.setOnClickListener(new InternalClickListener(null));
  }

  @Override
  public void bind(@NonNull MessageRecord      messageRecord,
                   @NonNull GlideRequests      glideRequests,
                   @NonNull Locale             locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   @NonNull Recipient          conversationRecipient,
                            boolean            pulseUpdate)
  {
    this.batchSelected = batchSelected;

    bind(messageRecord, locale);
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    // No events to report yet
  }

  @Override
  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  private void bind(@NonNull MessageRecord messageRecord, @NonNull Locale locale) {
    this.messageRecord = messageRecord;
    this.sender        = messageRecord.getIndividualRecipient();
    this.locale        = locale;

    this.sender.addListener(this);

    if      (messageRecord.isGroupAction())           setGroupRecord(messageRecord);
    else if (messageRecord.isCallLog())               setCallRecord(messageRecord);
    else if (messageRecord.isJoined())                setJoinedRecord(messageRecord);
    else if (messageRecord.isExpirationTimerUpdate()) setTimerRecord(messageRecord);
    else if (messageRecord.isEndSession())            setEndSessionRecord(messageRecord);
    else if (messageRecord.isIdentityUpdate())        setIdentityRecord(messageRecord);
    else if (messageRecord.isIdentityVerified() ||
             messageRecord.isIdentityDefault())       setIdentityVerifyUpdate(messageRecord);
    else                                              throw new AssertionError("Neither group nor log nor joined.");

    if (batchSelected.contains(messageRecord)) setSelected(true);
    else                                       setSelected(false);
  }

  private void setCallRecord(MessageRecord messageRecord) {
    if      (messageRecord.isIncomingCall()) icon.setImageResource(R.drawable.ic_call_received_grey600_24dp);
    else if (messageRecord.isOutgoingCall()) icon.setImageResource(R.drawable.ic_call_made_grey600_24dp);
    else                                     icon.setImageResource(R.drawable.ic_call_missed_grey600_24dp);

    body.setText(messageRecord.getDisplayBody());
    date.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getDateReceived()));
    date.setVisibility(View.VISIBLE);
  }

  private void setTimerRecord(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      icon.setImageResource(R.drawable.ic_timer_white_24dp);
      icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    } else {
      icon.setImageResource(R.drawable.ic_timer_off_white_24dp);
      icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    }

    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }

  private void setIdentityRecord(final MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_security_white_24dp);
    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }

  private void setIdentityVerifyUpdate(final MessageRecord messageRecord) {
    if (messageRecord.isIdentityVerified()) icon.setImageResource(R.drawable.ic_check_white_24dp);
    else                                    icon.setImageResource(R.drawable.ic_info_outline_white_24dp);

    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }

  private void setGroupRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_group_grey600_24dp);
    icon.clearColorFilter();

    GroupUtil.getDescription(getContext(), messageRecord.getBody()).addListener(this);
    body.setText(messageRecord.getDisplayBody());

    date.setVisibility(View.GONE);
  }

  private void setJoinedRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_favorite_grey600_24dp);
    icon.clearColorFilter();
    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }

  private void setEndSessionRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_refresh_white_24dp);
    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText(messageRecord.getDisplayBody());
    date.setVisibility(View.GONE);
  }
  
  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> bind(messageRecord, locale));
  }

  @Override
  public void setOnClickListener(View.OnClickListener l) {
    super.setOnClickListener(new InternalClickListener(l));
  }

  @Override
  public void unbind() {
    if (sender != null) {
      sender.removeListener(this);
    }
  }

  private class InternalClickListener implements View.OnClickListener {

    @Nullable private final View.OnClickListener parent;

    InternalClickListener(@Nullable View.OnClickListener parent) {
      this.parent = parent;
    }

    @Override
    public void onClick(View v) {
      if ((!messageRecord.isIdentityUpdate()  &&
           !messageRecord.isIdentityDefault() &&
           !messageRecord.isIdentityVerified()) ||
          !batchSelected.isEmpty())
      {
        if (parent != null) parent.onClick(v);
        return;
      }

      final Recipient sender = ConversationUpdateItem.this.sender;

      IdentityUtil.getRemoteIdentityKey(getContext(), sender).addListener(new ListenableFuture.Listener<Optional<IdentityRecord>>() {
        @Override
        public void onSuccess(Optional<IdentityRecord> result) {
          if (result.isPresent()) {
            Intent intent = new Intent(getContext(), VerifyIdentityActivity.class);
            intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, sender.getAddress());
            intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(result.get().getIdentityKey()));
            intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);

            getContext().startActivity(intent);
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
    }
  }

}
