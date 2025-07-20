package com.example.securenotes.ui.pinmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.securenotes.R;
import com.example.securenotes.security.SecurityUtils;
import com.example.securenotes.security.SessionManager;
import com.example.securenotes.SecureNotesApplication;
import com.example.securenotes.ui.login.LoginActivity;
import com.example.securenotes.utils.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import static javax.crypto.Cipher.DECRYPT_MODE;

public class ChangePinActivity extends AppCompatActivity {

    private static final String TAG = "ChangePinActivity";

    private EditText oldPinEditText;
    private EditText newPinEditText;
    private EditText confirmNewPinEditText;
    private Button changePinButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pin);

        // Abilita il pulsante Indietro nella ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Change PIN");
        }

        oldPinEditText = findViewById(R.id.old_pin_edit_text);
        newPinEditText = findViewById(R.id.new_pin_edit_text);
        confirmNewPinEditText = findViewById(R.id.confirm_new_pin_edit_text);
        changePinButton = findViewById(R.id.confirm_change_pin_button);

        changePinButton.setOnClickListener(v -> attemptChangePin());
    }

    // Gestisce il click sul pulsante Indietro nella ActionBar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void attemptChangePin() {
        String oldPin = oldPinEditText.getText().toString();
        String newPin = newPinEditText.getText().toString();
        String confirmNewPin = confirmNewPinEditText.getText().toString();

        if (oldPin.isEmpty() || newPin.isEmpty() || confirmNewPin.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPin.length() < 4) {
            Toast.makeText(this, "The new PIN must be at least 4 digits.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPin.equals(confirmNewPin)) {
            Toast.makeText(this, "The new PIN and confirmation do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 1. Recupera il PIN attuale criptato dalle SharedPreferences
            String encryptedStoredPinWithIv = SecurityUtils.getSecureString(this, Constants.KEY_USER_PIN, null);
            if (encryptedStoredPinWithIv == null) {
                Toast.makeText(this, "Error: PIN not set. Restart the app.", Toast.LENGTH_LONG).show();
                return;
            }

            // 2. Ottieni la chiave per decriptare il PIN attuale
            SecretKey pinSecretKey = SecurityUtils.getOrCreatePinAesKey(this);
            if (pinSecretKey == null) {
                Toast.makeText(this, "Errore di sicurezza: Chiave PIN non disponibile.", Toast.LENGTH_LONG).show();
                return;
            }

            // 3. Decripta il PIN attuale e confrontalo
            String decryptedStoredPin = decrypt(encryptedStoredPinWithIv, pinSecretKey);

            if (!oldPin.equals(decryptedStoredPin)) {
                Toast.makeText(this, "PIN attuale errato.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 4. Se il PIN attuale è corretto, cripta e salva il nuovo PIN
            String newEncryptedPinWithIv = encrypt(newPin, pinSecretKey); // Usa la stessa chiave
            if (SecurityUtils.saveSecureString(this, Constants.KEY_USER_PIN, newEncryptedPinWithIv)) {
                Toast.makeText(this, "PIN changed successfully!", Toast.LENGTH_SHORT).show();

                finish(); // Chiudi questa Activity
            } else {
                Toast.makeText(this, "Errore durante il salvataggio del nuovo PIN.", Toast.LENGTH_SHORT).show();
            }

        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Errore durante il cambio PIN: " + e.getMessage(), e);
            Toast.makeText(this, "Security error while changing PIN.", Toast.LENGTH_LONG).show();
        }
    }


    private String encrypt(String data, SecretKey secretKey) throws GeneralSecurityException {
        try {
            byte[] iv = SecurityUtils.generateRandomIv();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

            Cipher cipher = Cipher.getInstance(Constants.TRANSFORMATION); // Usa Constants per TRANSFORMATION
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Errore di crittografia: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Errore generico durante la crittografia: " + e.getMessage(), e);
            throw new GeneralSecurityException("Errore generico di crittografia", e);
        }
    }

    private String decrypt(String encryptedData, SecretKey secretKey) throws GeneralSecurityException {
        try {
            byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);

            byte[] iv = new byte[SecurityUtils.GCM_IV_SIZE_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

            Cipher cipher = Cipher.getInstance(Constants.TRANSFORMATION); // Usa Constants per TRANSFORMATION
            cipher.init(DECRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Errore di decrittografia (GCM): " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Errore generico durante la decrittografia (GCM): " + e.getMessage(), e);
            throw new GeneralSecurityException("Errore generico di decrittografia", e);
        }
    }
}