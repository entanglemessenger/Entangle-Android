package org.entanglemessenger.entangle.mms;


import android.support.annotation.Nullable;

import org.entanglemessenger.entangle.attachments.Attachment;
import org.entanglemessenger.entangle.database.Address;

import java.util.List;

public class QuoteModel {

  private final long             id;
  private final Address          author;
  private final String           text;
  private final List<Attachment> attachments;

  public QuoteModel(long id, Address author, String text, @Nullable List<Attachment> attachments) {
    this.id         = id;
    this.author     = author;
    this.text       = text;
    this.attachments = attachments;
  }

  public long getId() {
    return id;
  }

  public Address getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }
}
