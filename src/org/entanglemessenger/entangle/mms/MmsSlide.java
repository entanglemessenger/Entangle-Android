package org.entanglemessenger.entangle.mms;


import android.content.Context;
import android.support.annotation.NonNull;

import org.entanglemessenger.entangle.attachments.Attachment;

public class MmsSlide extends ImageSlide {

  public MmsSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  @NonNull
  @Override
  public String getContentDescription() {
    return "MMS";
  }

}
