package org.entanglemessenger.entangle.contacts.avatars;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


import com.bumptech.glide.load.Key;

import java.io.IOException;
import java.io.InputStream;

public interface ContactPhoto extends Key {

  InputStream openInputStream(Context context) throws IOException;

  @Nullable Uri getUri(@NonNull Context context);

  boolean isProfilePhoto();

}
