package org.entanglemessenger.entangle.contactshare;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import org.entanglemessenger.entangle.PassphraseRequiredActionBarActivity;
import org.entanglemessenger.entangle.R;
import org.entanglemessenger.entangle.util.DynamicLanguage;
import org.entanglemessenger.entangle.util.DynamicNoActionBarTheme;
import org.entanglemessenger.entangle.util.DynamicTheme;

import static org.entanglemessenger.entangle.contactshare.Contact.*;

public class ContactNameEditActivity extends PassphraseRequiredActionBarActivity {

  public static final String KEY_NAME          = "name";
  public static final String KEY_CONTACT_INDEX = "contact_index";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private TextView                 displayNameView;
  private ContactNameEditViewModel viewModel;

  static Intent getIntent(@NonNull Context context, @NonNull Name name, int contactPosition) {
    Intent intent = new Intent(context, ContactNameEditActivity.class);
    intent.putExtra(KEY_NAME, name);
    intent.putExtra(KEY_CONTACT_INDEX, contactPosition);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    if (getIntent() == null) {
      throw new IllegalStateException("You must supply extras to this activity. Please use the #getIntent() method.");
    }

    Name name = getIntent().getParcelableExtra(KEY_NAME);
    if (name == null) {
      throw new IllegalStateException("You must supply a name to this activity. Please use the #getIntent() method.");
    }

    setContentView(R.layout.activity_contact_name_edit);

    initializeToolbar();
    initializeViews(name);

    viewModel = ViewModelProviders.of(this).get(ContactNameEditViewModel.class);
    viewModel.setName(name);
    viewModel.getDisplayName().observe(this, displayNameView::setText);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  private void initializeToolbar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    toolbar.setTitle("");
    toolbar.setNavigationIcon(R.drawable.ic_check_white_24dp);
    toolbar.setNavigationOnClickListener(v -> {
      Intent resultIntent = new Intent();
      resultIntent.putExtra(KEY_NAME, viewModel.getName());
      resultIntent.putExtra(KEY_CONTACT_INDEX, getIntent().getIntExtra(KEY_CONTACT_INDEX, -1));
      setResult(RESULT_OK, resultIntent);
      finish();
    });
  }

  private void initializeViews(@NonNull Name name) {
    displayNameView = findViewById(R.id.name_edit_display_name);

    TextView givenName   = findViewById(R.id.name_edit_given_name);
    TextView familyName  = findViewById(R.id.name_edit_family_name);
    TextView middleName  = findViewById(R.id.name_edit_middle_name);
    TextView prefix      = findViewById(R.id.name_edit_prefix);
    TextView suffix      = findViewById(R.id.name_edit_suffix);

    givenName.setText(name.getGivenName());
    familyName.setText(name.getFamilyName());
    middleName.setText(name.getMiddleName());
    prefix.setText(name.getPrefix());
    suffix.setText(name.getSuffix());

    givenName.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      void onTextChanged(String text) {
        viewModel.updateGivenName(text);
      }
    });

    familyName.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      void onTextChanged(String text) {
        viewModel.updateFamilyName(text);
      }
    });

    middleName.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      void onTextChanged(String text) {
        viewModel.updateMiddleName(text);
      }
    });

    prefix.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      void onTextChanged(String text) {
        viewModel.updatePrefix(text);
      }
    });

    suffix.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      void onTextChanged(String text) {
        viewModel.updateSuffix(text);
      }
    });
  }

  private static abstract class SimpleTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
      onTextChanged(s.toString());
    }

    @Override
    public void afterTextChanged(Editable s) { }

    abstract void onTextChanged(String text);
  }
}
