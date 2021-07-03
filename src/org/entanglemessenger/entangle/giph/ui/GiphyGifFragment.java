package org.entanglemessenger.entangle.giph.ui;


import android.os.Bundle;
import android.support.v4.content.Loader;

import org.entanglemessenger.entangle.giph.model.GiphyImage;
import org.entanglemessenger.entangle.giph.net.GiphyGifLoader;

import java.util.List;

public class GiphyGifFragment extends GiphyFragment {

  @Override
  public Loader<List<GiphyImage>> onCreateLoader(int id, Bundle args) {
    return new GiphyGifLoader(getActivity(), searchString);
  }

}
