/**
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
package org.entanglemessenger.entangle.database;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.entanglemessenger.entangle.database.helpers.SQLCipherOpenHelper;

import java.util.Set;

public abstract class Database {

  protected static final String ID_WHERE              = "_id = ?";
  private   static final String CONVERSATION_URI      = "content://org.entanglemessenger.entangle.database.conversation/";
  private   static final String CONVERSATION_LIST_URI = "content://org.entanglemessenger.entangle.database.conversationlist";
  private   static final String ATTACHMENT_URI        = "content://org.entanglemessenger.entangle.database.attachment";

  protected       SQLCipherOpenHelper databaseHelper;
  protected final Context             context;

  public Database(Context context, SQLCipherOpenHelper databaseHelper) {
    this.context        = context;
    this.databaseHelper = databaseHelper;
  }

  protected void notifyConversationListeners(Set<Long> threadIds) {
    for (long threadId : threadIds)
      notifyConversationListeners(threadId);
  }

  protected void notifyConversationListeners(long threadId) {
    context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_URI + threadId), null);
  }

  protected void notifyConversationListListeners() {
    context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_LIST_URI), null);
  }

  protected void setNotifyConverationListeners(Cursor cursor, long threadId) {
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_URI + threadId));
  }

  protected void setNotifyConverationListListeners(Cursor cursor) {
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_LIST_URI));
  }

  protected void registerAttachmentListeners(@NonNull ContentObserver observer) {
    context.getContentResolver().registerContentObserver(Uri.parse(ATTACHMENT_URI),
                                                         true,
                                                         observer);
  }

  protected void notifyAttachmentListeners() {
    context.getContentResolver().notifyChange(Uri.parse(ATTACHMENT_URI), null);
  }

  public void reset(SQLCipherOpenHelper databaseHelper) {
    this.databaseHelper = databaseHelper;
  }

}
