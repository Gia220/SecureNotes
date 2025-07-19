package com.example.securenotes;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;



import com.example.securenotes.security.SessionManager;
import com.example.securenotes.ui.login.LoginActivity;
import com.example.securenotes.utils.Constants;

import java.util.concurrent.TimeUnit;



public class SecureNotesApplication extends Application {

    private static final String TAG = "SecureNotesApplication";
    private SessionManager sessionManager;

    private static final String NOTE_CLEANUP_WORK_NAME = "NoteCleanupWorker";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SecureNotesApplication onCreate: Inizializzazione SessionManager.");

        applyNightModePreference();

        sessionManager = new SessionManager(this, () -> {
            Log.d(TAG, "Sessione scaduta! Reindirizzamento a LoginActivity.");
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });




    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    private void applyNightModePreference() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_APP_SETTINGS, Context.MODE_PRIVATE);
        int nightMode = prefs.getInt(Constants.KEY_NIGHT_MODE_PREFERENCE, Constants.NIGHT_MODE_FOLLOW_SYSTEM);              //default system

        switch (nightMode) {
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
        Log.d(TAG, "Modalità notte applicata: " + nightMode);
    }



}