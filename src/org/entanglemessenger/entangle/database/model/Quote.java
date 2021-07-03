package org.entanglemessenger.entangle.database.model;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.entanglemessenger.entangle.database.Address;
import org.entanglemessenger.entangle.mms.SlideDeck;

public class Quote {

  private final long      id;
  private final Address   author;
  private final String    text;
  private final SlideDeck attachment;

  public Quote(long id, @NonNull Address author, @Nullable String text, @NonNull SlideDeck attachment) {
    this.id         = id;
    this.author     = author;
    this.text       = text;
    this.attachment = attachment;
  }

  public long getId() {
    return id;
  }

  public @NonNull Address getAuthor() {
    return author;
  }

  public @Nullable String getText() {
    return text;
  }

  public @NonNull SlideDeck getAttachment() {
    return attachment;
  }
}
