package org.entanglemessenger.entangle.database.loaders;

import android.content.Context;
import android.database.Cursor;

import org.entanglemessenger.entangle.database.DatabaseFactory;
import org.entanglemessenger.entangle.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientDatabase(getContext())
                          .getBlocked();
  }

}
