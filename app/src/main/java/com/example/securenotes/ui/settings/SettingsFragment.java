package com.example.securenotes.ui.settings;

import android.app.AlertDialog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.securenotes.R;
import com.example.securenotes.SecureNotesApplication;
import com.example.securenotes.security.SessionManager;
import com.example.securenotes.utils.BackupManager;
import com.example.securenotes.utils.Constants;


import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private EditText timeoutMinutesEditText;
    private Button saveTimeoutButton;
    private Button exportBackupButton;
    private Button changePinButton;
    private TextView currentTimeoutTextView;
    private RadioGroup nightModeRadioGroup;


    private SessionManager sessionManager;
    private BackupManager backupManager;

    // Launcher per selezionare la directory di output del backup
    private ActivityResultLauncher<String> manualBackupFileLauncher;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        timeoutMinutesEditText = view.findViewById(R.id.timeout_minutes_edit_text);
        saveTimeoutButton = view.findViewById(R.id.save_timeout_button);
        exportBackupButton = view.findViewById(R.id.export_backup_button);
        changePinButton = view.findViewById(R.id.change_pin_button);
        currentTimeoutTextView = view.findViewById(R.id.current_timeout_text_view);
        nightModeRadioGroup = view.findViewById(R.id.night_mode_radio_group);


        //scopo specifico è quello di permettere all'utente di scegliere una posizione e un nome per il file ZIP del backup
        manualBackupFileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),              // creare un documento zip
                uri -> {
                    if (uri != null) {
                        new Thread(() -> {
                            OutputStream outputStream = null;
                            try {
                                outputStream = requireContext().getContentResolver().openOutputStream(uri); //per scrivere
                                if (outputStream == null) {
                                    throw new IOException("Unable to open output stream for URI:" + uri.toString());
                                }
                                String backupPassword = (String) view.getTag(R.id.tag_manual_backup_password);
                                if (backupPassword == null || backupPassword.isEmpty()) {
                                    throw new GeneralSecurityException("Password di backup non disponibile nella callback del launcher.");
                                }

                                backupManager.exportEncryptedBackup(backupPassword, outputStream);              //fa backup
                                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "backup created successfully!", Toast.LENGTH_LONG).show());  //NEL THREAD PRINCIPALE
                            } catch (IOException | GeneralSecurityException e) {
                                android.util.Log.e(TAG, "Errore durante l'esportazione del backup", e);
                                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Error during backup: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
                            } finally {
                                if (outputStream != null) {
                                    try {
                                        outputStream.close();
                                    } catch (IOException e) {
                                        android.util.Log.e(TAG, "Errore durante la chiusura dell'output stream", e);
                                    }
                                }
                            }
                        }).start();
                    } else {
                        Toast.makeText(getContext(), "Backup creation cancelled by user.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        sessionManager = ((SecureNotesApplication) requireActivity().getApplication()).getSessionManager();
        backupManager = new BackupManager(getContext());

        updateCurrentTimeoutDisplay();

        saveTimeoutButton.setOnClickListener(v -> saveTimeout());
        exportBackupButton.setOnClickListener(v -> showManualBackupPasswordDialog(view));
        changePinButton.setOnClickListener(v -> changePin());

        loadAndSetNightModePreference();
        nightModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = Constants.NIGHT_MODE_FOLLOW_SYSTEM;
            if (checkedId == R.id.radio_light_mode) {
                mode = Constants.NIGHT_MODE_LIGHT;
            } else if (checkedId == R.id.radio_dark_mode) {
                mode = Constants.NIGHT_MODE_DARK;
            }
            saveNightModePreference(mode);
            applyNightModePreference(mode);
        });

        return view;
    }

    private void updateCurrentTimeoutDisplay() {
        long currentTimeout = sessionManager.getInactivityTimeoutDurationMinutes();
        currentTimeoutTextView.setText("Current timeout: " + currentTimeout + " min");
        timeoutMinutesEditText.setText(String.valueOf(currentTimeout));
    }

    private void saveTimeout() {
        String input = timeoutMinutesEditText.getText().toString();
        if (input.isEmpty()) {
            Toast.makeText(getContext(), "Enter a timeout value.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            long minutes = Long.parseLong(input);
            if (minutes < 0) {
                Toast.makeText(getContext(), "Timeout cannot be negative.", Toast.LENGTH_SHORT).show();
                return;
            }
            sessionManager.setInactivityTimeoutDuration(minutes);
            updateCurrentTimeoutDisplay();
            Toast.makeText(getContext(), "Timeout saved successfully!", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter a valid number for the timeout.", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportBackup() {
        showManualBackupPasswordDialog(getView());
    }

    private void changePin() {
        Intent intent = new Intent(getContext(), com.example.securenotes.ui.pinmanagement.ChangePinActivity.class);
        startActivity(intent);
    }

    private void loadAndSetNightModePreference() {
        SharedPreferences prefs = requireContext().getSharedPreferences(Constants.PREFS_APP_SETTINGS, Context.MODE_PRIVATE);
        int nightMode = prefs.getInt(Constants.KEY_NIGHT_MODE_PREFERENCE, Constants.NIGHT_MODE_FOLLOW_SYSTEM);

        switch (nightMode) {
            case Constants.NIGHT_MODE_LIGHT:
                nightModeRadioGroup.check(R.id.radio_light_mode);
                break;
            case Constants.NIGHT_MODE_DARK:
                nightModeRadioGroup.check(R.id.radio_dark_mode);
                break;
            case Constants.NIGHT_MODE_FOLLOW_SYSTEM:
            default:
                nightModeRadioGroup.check(R.id.radio_system_mode);
                break;
        }
    }

    private void saveNightModePreference(int mode) {
        SharedPreferences prefs = requireContext().getSharedPreferences(Constants.PREFS_APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putInt(Constants.KEY_NIGHT_MODE_PREFERENCE, mode).apply();
    }

    private void applyNightModePreference(int mode) {
        switch (mode) {
            case Constants.NIGHT_MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Constants.NIGHT_MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case Constants.NIGHT_MODE_FOLLOW_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    // Metodo per mostrare il dialogo della password per backup manuale
    private void showManualBackupPasswordDialog(View parentView) {
        final EditText passwordEditText = new EditText(getContext());
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEditText.setHint("Password for backup");

        new AlertDialog.Builder(getContext())
                .setTitle("Password for backup")
                .setMessage("Enter a password to encrypt your backup.")
                .setView(passwordEditText)
                .setPositiveButton("Export", (dialog, which) -> {
                    String backupPassword = passwordEditText.getText().toString();
                    if (backupPassword.isEmpty()) {
                        Toast.makeText(getContext(), "The password cannot be empty.", Toast.LENGTH_SHORT).show();
                    } else {
                        // Memorizza temporaneamente la password per la callback del launcher usando un tag sulla view
                        parentView.setTag(R.id.tag_manual_backup_password, backupPassword);

                        // Lancia il launcher per creare il file
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                        String timestamp = sdf.format(new Date());
                        String suggestedFileName = "SecureNotes_manual_backup_" + timestamp + ".zip";
                        manualBackupFileLauncher.launch(suggestedFileName);
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }
}