package org.entanglemessenger.entangle.util;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.style.ClickableSpan;
import android.view.View;

import org.entanglemessenger.entangle.VerifyIdentityActivity;
import org.entanglemessenger.entangle.crypto.IdentityKeyParcelable;
import org.entanglemessenger.entangle.database.Address;
import org.entanglemessenger.entangle.database.documents.IdentityKeyMismatch;
import org.whispersystems.libsignal.IdentityKey;

public class VerifySpan extends ClickableSpan {

  private final Context     context;
  private final Address     address;
  private final IdentityKey identityKey;

  public VerifySpan(@NonNull Context context, @NonNull IdentityKeyMismatch mismatch) {
    this.context     = context;
    this.address     = mismatch.getAddress();
    this.identityKey = mismatch.getIdentityKey();
  }

  public VerifySpan(@NonNull Context context, @NonNull Address address, @NonNull IdentityKey identityKey) {
    this.context     = context;
    this.address     = address;
    this.identityKey = identityKey;
  }

  @Override
  public void onClick(View widget) {
    Intent intent = new Intent(context, VerifyIdentityActivity.class);
    intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, address);
    intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(identityKey));
    intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);
    context.startActivity(intent);
  }
}
