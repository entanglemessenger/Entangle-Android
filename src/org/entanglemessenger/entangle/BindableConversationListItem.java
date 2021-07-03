package org.entanglemessenger.entangle;

import android.support.annotation.NonNull;

import org.entanglemessenger.entangle.database.model.ThreadRecord;
import org.entanglemessenger.entangle.mms.GlideRequests;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests, @NonNull Locale locale,
                   @NonNull Set<Long> selectedThreads, boolean batchMode);
}
