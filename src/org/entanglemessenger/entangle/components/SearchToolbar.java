package org.entanglemessenger.entangle.components;


import android.animation.Animator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.animation.AnimationCompleteListener;

public class SearchToolbar extends LinearLayout {

  private float x, y;
  private MenuItem searchItem;
  private SearchListener listener;

  public SearchToolbar(Context context) {
    super(context);
    initialize();
  }

  public SearchToolbar(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SearchToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.search_toolbar, this);
    setOrientation(VERTICAL);

    Toolbar toolbar = findViewById(R.id.toolbar);

    Drawable drawable = getContext().getResources().getDrawable(R.drawable.ic_arrow_back_white_24dp);
    drawable.mutate();
    drawable.setColorFilter(getContext().getResources().getColor(R.color.grey_700), PorterDuff.Mode.SRC_IN);

    toolbar.setNavigationIcon(drawable);
    toolbar.inflateMenu(R.menu.conversation_list_search);

    this.searchItem = toolbar.getMenu().findItem(R.id.action_filter_search);
    SearchView searchView = (SearchView) searchItem.getActionView();
    EditText   searchText = searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);

    searchView.setSubmitButtonEnabled(false);

    if (searchText != null) searchText.setHint(R.string.SearchToolbar_search);
    else                    searchView.setQueryHint(getResources().getString(R.string.SearchToolbar_search));

    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (listener != null) listener.onSearchTextChange(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
      }
    });

    searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        hide();
        return true;
      }
    });

    toolbar.setNavigationOnClickListener(v -> hide());
  }

  @MainThread
  public void display(float x, float y) {
    if (getVisibility() != View.VISIBLE) {
      this.x = x;
      this.y = y;

      searchItem.expandActionView();

      if (Build.VERSION.SDK_INT >= 21) {
        Animator animator = ViewAnimationUtils.createCircularReveal(this, (int)x, (int)y, 0, getWidth());
        animator.setDuration(400);

        setVisibility(View.VISIBLE);
        animator.start();
      } else {
        setVisibility(View.VISIBLE);
      }
    }
  }

  public void collapse() {
    searchItem.collapseActionView();
  }

  @MainThread
  private void hide() {
    if (getVisibility() == View.VISIBLE) {


      if (listener != null) listener.onSearchClosed();

      if (Build.VERSION.SDK_INT >= 21) {
        Animator animator = ViewAnimationUtils.createCircularReveal(this, (int)x, (int)y, getWidth(), 0);
        animator.setDuration(400);
        animator.addListener(new AnimationCompleteListener() {
          @Override
          public void onAnimationEnd(Animator animation) {
            setVisibility(View.INVISIBLE);
          }
        });
        animator.start();
      } else {
        setVisibility(View.INVISIBLE);
      }
    }
  }

  public boolean isVisible() {
    return getVisibility() == View.VISIBLE;
  }

  @MainThread
  public void setListener(SearchListener listener) {
    this.listener = listener;
  }

  public interface SearchListener {
    void onSearchTextChange(String text);
    void onSearchClosed();
  }

}
