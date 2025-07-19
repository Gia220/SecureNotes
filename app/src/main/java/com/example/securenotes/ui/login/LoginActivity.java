package com.example.securenotes.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.securenotes.R;
import com.example.securenotes.security.SecurityUtils;
import com.example.securenotes.ui.dashboard.DashboardActivity;
import com.example.securenotes.SecureNotesApplication;
import com.example.securenotes.security.SessionManager;
import com.example.securenotes.utils.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import static javax.crypto.Cipher.DECRYPT_MODE;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String KEY_USER_PIN = "user_pin";
    private static final String PIN_ENCRYPTION_KEY_ALIAS = "pin_encryption_key";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private Button biometricButton;
    private Button unlockPinButton;
    private EditText pinEditText;
    private Button setPinButton;
    private Button goToSettingsButton;

    private boolean isPinSet = false;
    private int biometricAvailability = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        biometricButton = findViewById(R.id.biometric_button);
        unlockPinButton = findViewById(R.id.unlock_pin_button);
        pinEditText = findViewById(R.id.pin_edit_text);
        setPinButton = findViewById(R.id.set_pin_button);
        goToSettingsButton = findViewById(R.id.go_to_settings_button);

        setupBiometricAuthentication();

        isPinSet = SecurityUtils.containsKey(this, Constants.KEY_USER_PIN);

        BiometricManager biometricManager = BiometricManager.from(this);
        biometricAvailability = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        Log.d(TAG, "BiometricManager.canAuthenticate(STRONG | DEVICE_CREDENTIAL): " + getBiometricStatusString(biometricAvailability));
        Log.d(TAG, "BiometricManager.canAuthenticate(BIOMETRIC_STRONG): " + getBiometricStatusString(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)));
        Log.d(TAG, "BiometricManager.canAuthenticate(DEVICE_CREDENTIAL): " + getBiometricStatusString(biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)));

        SessionManager sessionManager = ((SecureNotesApplication) getApplication()).getSessionManager();

        if (isPinSet) {
            if (savedInstanceState == null) {
                Log.d(TAG, "PIN impostato. Forzo il relogin.");
            } else {
                if (sessionManager.isTimeoutExpired()) {
                    Log.d(TAG, "Timeout di inattività scaduto. Forzo ri-autenticazione.");
                    Toast.makeText(this, "Your session expired due to inactivity. Please log in again..", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "PIN impostato e sessione valida. Reindirizzo alla Dashboard.");
                    navigateToDashboard();
                    return;
                }
            }
        }

        updateUiForPinStatus();

        unlockPinButton.setOnClickListener(view -> {            //conrollo pin len>4
            String pin = pinEditText.getText().toString();
            if (pin.length() >= 4) {
                authenticateWithPIN(pin);
            } else {
                Toast.makeText(LoginActivity.this, "Enter a valid PIN (min. 4 digits)", Toast.LENGTH_SHORT).show();
            }
        });

        setPinButton.setOnClickListener(view -> {              //conrollo pin len>4
            String pin = pinEditText.getText().toString();
            if (pin.length() >= 4) {
                setNewPin(pin);
            } else {
                Toast.makeText(LoginActivity.this, "Enter a valid PIN (min. 4 digits)", Toast.LENGTH_SHORT).show();
            }
        });

        goToSettingsButton.setOnClickListener(v -> {        //Porta l'utente direttamente alle impostazioni di sicurezza di Android per registrare la biometria
            Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            startActivity(enrollIntent);
        });
    }

    private String getBiometricStatusString(int status) {
        switch (status) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return "BIOMETRIC_SUCCESS (biometria disponibile e registrata)";
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "BIOMETRIC_ERROR_NO_HARDWARE (nessun hardware biometrico)";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "BIOMETRIC_ERROR_HW_UNAVAILABLE (hardware biometrico non disponibile)";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "BIOMETRIC_ERROR_NONE_ENROLLED (nessun biometrico registrato)";
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                return "BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED (aggiornamento sicurezza richiesto)";
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                return "BIOMETRIC_ERROR_UNSUPPORTED (tipo di autenticatore non supportato)";
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                return "BIOMETRIC_STATUS_UNKNOWN (stato sconosciuto)";
            default:
                return "Sconosciuto: " + status;
        }
    }

    private void updateUiForPinStatus() {
        if (isPinSet) {         //se il pin è stato registrato
            setPinButton.setVisibility(View.GONE);
            unlockPinButton.setVisibility(View.VISIBLE);
            pinEditText.setHint("Enter PIN to unlock");
            goToSettingsButton.setVisibility(View.GONE);

            if (biometricAvailability == BiometricManager.BIOMETRIC_SUCCESS) {                          //utente con biometria
                Log.d(TAG, "Biometria SUCCESS: Tentativo di autenticazione automatica all'avvio.");
                if (biometricPrompt != null && promptInfo != null) {
                    //biometricPrompt.authenticate(promptInfo);
                    biometricButton.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "BiometricPrompt o PromptInfo non inizializzati correttamente per l'autenticazione automatica.");
                    biometricButton.setVisibility(View.GONE);
                    Toast.makeText(this, "Internal biometrics error. Please use PIN.", Toast.LENGTH_LONG).show();
                }
            } else if (biometricAvailability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {       //utente senza biometria registrata
                Log.d(TAG, "Biometria NONE_ENROLLED: Utente non ha biometria. Guida alla registrazione/usa PIN.");
                biometricButton.setVisibility(View.GONE);
                goToSettingsButton.setVisibility(View.GONE);
                Toast.makeText(this, "To unlock with PIN/Biometrics, you need to register a fingerprint or face.", Toast.LENGTH_LONG).show();
            } else {                                                                                    //utente senza possiblità di inserire biometria
                Log.w(TAG, "Biometria: Hardware non disponibile o altri errori (" + biometricAvailability + "). Solo PIN.");
                biometricButton.setVisibility(View.GONE);
                goToSettingsButton.setVisibility(View.GONE);
                Toast.makeText(this, "Biometrics are not available on this device. Use a PIN.", Toast.LENGTH_LONG).show();
            }

        } else {
            unlockPinButton.setVisibility(View.GONE);
            biometricButton.setVisibility(View.GONE);
            setPinButton.setVisibility(View.VISIBLE);

            if (biometricAvailability == BiometricManager.BIOMETRIC_SUCCESS) {          //con biometria registrata

                Log.d(TAG, "Biometria SUCCESS: Biometria già pronta per nuovo PIN.");
                pinEditText.setHint("Set your new PIN");
                setPinButton.setEnabled(true);
                goToSettingsButton.setVisibility(View.GONE);
            } else if (biometricAvailability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) { //senza biometria registrata
                Log.d(TAG, "Biometria NONE_ENROLLED: Richiede registrazione per PIN sicuro.");
                pinEditText.setHint("Set your new PIN");
                setPinButton.setEnabled(true);
                goToSettingsButton.setVisibility(View.GONE);
                //Toast.makeText(this, "Per impostare il PIN, devi prima registrare almeno un'impronta digitale o un volto NELLE IMPOSTAZIONI DEL TELEFONO.", Toast.LENGTH_LONG).show();
            } else {                                                                      //senza possibilità di biometria
                Log.w(TAG, "Biometria: Stato sconosciuto per PIN non impostato: " + biometricAvailability + ". Permetti impostazione PIN.");
                pinEditText.setHint("Set your new PIN");
                setPinButton.setEnabled(true);
                goToSettingsButton.setVisibility(View.GONE);
            }
        }
    }


    private void setupBiometricAuthentication() {                   //configura il meccanismo biometrico.
        Executor executor = ContextCompat.getMainExecutor(this);            //definisce su quale thread eseguire (il main)

        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {        //metodi che vengono chiamati a seguito di un tentativo di autenticazione biometrica
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);                                  //errore durante l'autenticazione
                Toast.makeText(getApplicationContext(), "Biometric authentication error: " + errString , Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Biometric authentication error: " + errString);
                pinEditText.setText("");
                pinEditText.requestFocus();
                unlockPinButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);                                            //successo
                Toast.makeText(getApplicationContext(), "Biometric authentication successful!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Biometric authentication succeeded.");
                ((SecureNotesApplication) getApplication()).getSessionManager().clearLastActiveTimestamp();
                navigateToDashboard();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();                                                     //failed
                Toast.makeText(getApplicationContext(), "Biometric authentication failed. Try again.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Biometric authentication failed.");
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()                                       //pesonalizza il contenuto
                .setTitle("Unlock SecureNotes")
                .setSubtitle("Authenticate to access your sensitive notes and files")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricButton.setOnClickListener(view -> {                    //quando bottone biometrico viene toccato
            if (biometricPrompt != null && promptInfo != null) {
                BiometricManager biometricManager = BiometricManager.from(this);
                int canAuthenticateResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                if (canAuthenticateResult == BiometricManager.BIOMETRIC_SUCCESS) {
                    biometricPrompt.authenticate(promptInfo);
                } else {
                    String message = "No biometrics registered. Error: " + getBiometricStatusString(canAuthenticateResult);
                    if (canAuthenticateResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        message = "No biometrics registered. Register them in settings.";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Tentativo di avviare biometricPrompt ma BiometricManager.canAuthenticate fallisce: " + canAuthenticateResult);
                }
            } else {
                Toast.makeText(this, "Biometric authentication not available. Use your PIN.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Tentativo di avviare biometricPrompt ma è null.");
            }
        });
    }

    private void authenticateWithPIN(String enteredPin) {
        try {
            String encryptedStoredPinWithIv = SecurityUtils.getSecureString(this, Constants.KEY_USER_PIN, null); //Recupera il PIN crittografato e l'IV (Initialization Vector) dal SecurityUtils
            if (encryptedStoredPinWithIv == null) {
                Toast.makeText(this, "PIN not set. Please set it first.", Toast.LENGTH_SHORT).show();
                return;
            }

            SecretKey pinSecretKey = SecurityUtils.getOrCreatePinAesKey(this);      //Ottiene la SecretKey per la decrittografia del PIN
            if (pinSecretKey == null) {
                Log.e(TAG, "Errore di sicurezza: Chiave PIN non disponibile (PBKDF2 ha fallito).");
                Toast.makeText(this, "Security error", Toast.LENGTH_LONG).show();
                return;
            }

            String decryptedStoredPin = decrypt(encryptedStoredPinWithIv, pinSecretKey);        //decripto con SecretKey

            if (enteredPin.equals(decryptedStoredPin)) {
                Toast.makeText(this, "PIN authentication successful!", Toast.LENGTH_SHORT).show();
                ((SecureNotesApplication) getApplication()).getSessionManager().clearLastActiveTimestamp();
                navigateToDashboard();
            } else {
                Toast.makeText(this, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show();
                pinEditText.setText("");
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Errore durante l'autenticazione del PIN: " + e.getMessage(), e);
            if (e.getCause() instanceof IllegalStateException && e.getCause().getMessage() != null && e.getCause().getMessage().contains("biometric must be enrolled")) {
                Toast.makeText(this, "Per sbloccare con PIN/Biometria, devi registrare un'impronta digitale o un volto. Vai NELLE IMPOSTAZIONI DEL TELEFONO > Sicurezza.", Toast.LENGTH_LONG).show();
                if (goToSettingsButton != null) goToSettingsButton.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Authentication error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setNewPin(String newPin) {
        try {
            SecretKey pinSecretKey = SecurityUtils.getOrCreatePinAesKey(this);          //prendo la key per decriptare
            if (pinSecretKey == null) {
                Log.e(TAG, "Errore di sicurezza: Impossibile generare chiave PIN (PBKDF2 ha fallito).");
                Toast.makeText(this, "Errore di sicurezza critico. Impossibile impostare il PIN.", Toast.LENGTH_LONG).show();
                return;
            }

            String encryptedPinWithIv = encrypt(newPin, pinSecretKey);      //cripto la nuova password
            if (SecurityUtils.saveSecureString(this, Constants.KEY_USER_PIN, encryptedPinWithIv)) {
                Toast.makeText(this, "PIN set successfully!", Toast.LENGTH_SHORT).show();
                isPinSet = true;
                ((SecureNotesApplication) getApplication()).getSessionManager().clearLastActiveTimestamp();
                updateUiForPinStatus();
                pinEditText.setText("");
                navigateToDashboard();
            } else {
                Toast.makeText(this, "Error setting PIN.", Toast.LENGTH_SHORT).show();
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Errore durante l'impostazione del PIN criptato: " + e.getMessage(), e);
            if (e.getCause() instanceof IllegalStateException && e.getCause().getMessage() != null && e.getCause().getMessage().contains("biometric must be enrolled")) {
                Toast.makeText(this, "To set up a secure PIN, you must first register a fingerprint or face IN YOUR PHONE SETTINGS.", Toast.LENGTH_LONG).show();
                if (goToSettingsButton != null) goToSettingsButton.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Security error while setting PIN: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private String encrypt(String data, SecretKey secretKey) throws GeneralSecurityException {
        try {
            byte[] iv = SecurityUtils.generateRandomIv();       //genera un random IV
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);     //creo una GCM con la IV

            Cipher cipher = Cipher.getInstance(Constants.TRANSFORMATION);       //AES/GCM/NoPadding
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);       //inizializzo chiper per criptare

            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));  //cripto

            byte[] combined = new byte[iv.length + encryptedBytes.length];              //combino dati criptati e iv
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);   //byte -> base64
        }
        catch (GeneralSecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Errore di crittografia: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Errore generico durante la crittografia: " + e.getMessage(), e);
            throw new GeneralSecurityException("Errore generico di crittografia", e);
        }
    }

    private String decrypt(String encryptedData, SecretKey secretKey) throws GeneralSecurityException {
        try {
            byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);         //decodifico da base64->byte

            byte[] iv = new byte[SecurityUtils.GCM_IV_SIZE_BYTES];                  //estraggo iv
            System.arraycopy(combined, 0, iv, 0, iv.length);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

            byte[] ciphertext = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(Constants.TRANSFORMATION);
            cipher.init(DECRYPT_MODE, secretKey, gcmSpec);                          //inizializzo chipper in moaita decript

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