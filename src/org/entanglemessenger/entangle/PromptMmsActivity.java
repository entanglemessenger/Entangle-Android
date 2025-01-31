package org.entanglemessenger.entangle;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import org.entanglemessenger.entangle.preferences.MmsPreferencesActivity;

public class PromptMmsActivity extends PassphraseRequiredActionBarActivity {

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.prompt_apn_activity);
    initializeResources();
  }

  private void initializeResources() {
    Button okButton = findViewById(R.id.ok_button);
    Button cancelButton = findViewById(R.id.cancel_button);

    okButton.setOnClickListener(v -> {
      Intent intent = new Intent(PromptMmsActivity.this, MmsPreferencesActivity.class);
      intent.putExtras(PromptMmsActivity.this.getIntent().getExtras());
      startActivity(intent);
      finish();
    });

    cancelButton.setOnClickListener(v -> finish());
  }

}
