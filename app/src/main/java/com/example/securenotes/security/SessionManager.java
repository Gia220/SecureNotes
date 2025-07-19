//. Il Looper è il meccanismo interno di un thread che gli permette di processare una coda di messaggi e Runnable, io aggiungo dopo X min un rannable tramite l'heandler


package com.example.securenotes.security;





import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.securenotes.ui.login.LoginActivity;
import com.example.securenotes.utils.Constants;


public class SessionManager implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "SessionManager";
    private Application application;
    private Runnable logoutRunnable;
    private Handler handler;

    private long inactivityTimeoutMillis; // Timeout per l'inattività complessiva

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_INACTIVITY_TIMEOUT_DURATION = "inactivity_timeout_duration";
    private static final String KEY_LAST_ACTIVE_TIMESTAMP = "last_active_timestamp"; // Per il caso di "processo killato"

    private int activityReferences = 0; // Contatore delle Activity in stato STARTED o superiore
    private boolean isActivityChangingConfigurations = false;

    public SessionManager(Application application, Runnable logoutRunnable) {
        this.application = application;
        this.logoutRunnable = logoutRunnable;
        this.handler = new Handler(Looper.getMainLooper());                     //loop del ciclo dell main thread
        this.application.registerActivityLifecycleCallbacks(this);

        SharedPreferences prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.inactivityTimeoutMillis = prefs.getLong(KEY_INACTIVITY_TIMEOUT_DURATION, Constants.DEFAULT_INACTIVITY_TIMEOUT_MINUTES * 60 * 1000);
        if (this.inactivityTimeoutMillis == 0) this.inactivityTimeoutMillis = 1; // Assicura minimo 1ms

        Log.d(TAG, "SessionManager inizializzato. Inactivity Timeout: " + inactivityTimeoutMillis + " ms.");
    }


    private void startLogoutTimer() {           // Avvia il timer di logout.
        stopLogoutTimer();
        handler.postDelayed(logoutRunnable, inactivityTimeoutMillis);                   //handler esegue logoutRunnable solo dopo inactivityTimeoutMillis
        Log.d(TAG, "Timer di logout avviato per " + inactivityTimeoutMillis + " ms (inattività complessiva).");
    }


    private void stopLogoutTimer() {          // Ferma il timer di logout
        handler.removeCallbacks(logoutRunnable);
        Log.d(TAG, "Timer di logout fermato.");
    }


    public void resetLogoutTimer() {        // Resetta il timer (chiamato ad ogni attività dell'utente o ripresa di Activity)
        Log.d(TAG, "Timer di logout resettato.");
        startLogoutTimer();
    }

    // Salva la nuova durata del timeout (per inattività, in minuti)
    public void setInactivityTimeoutDuration(long minutes) {
        this.inactivityTimeoutMillis = minutes * 60 * 1000;
        if (this.inactivityTimeoutMillis == 0) this.inactivityTimeoutMillis = 1;
        SharedPreferences prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_INACTIVITY_TIMEOUT_DURATION, this.inactivityTimeoutMillis).apply();
        Log.d(TAG, "Nuovo timeout inattività impostato e salvato: " + minutes + " minuti.");
        // Resetta il timer solo se l'app è in foreground
        if (isAppInForeground()) {
            resetLogoutTimer();
        }
    }

    // Ottiene la durata attuale del timeout in minuti
    public long getInactivityTimeoutDurationMinutes() {
        if (inactivityTimeoutMillis == 1) return 0;
        return inactivityTimeoutMillis / (60 * 1000);
    }

    // Salva l'ultimo timestamp di attività (per il controllo su "processo killato")
    private void saveLastActiveTimestamp() {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIMESTAMP, System.currentTimeMillis()).apply();
        Log.d(TAG, "Ultimo timestamp di attività salvato per persistenza.");
    }

    // Recupera l'ultimo timestamp di attività
    public long getLastActiveTimestamp() {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_ACTIVE_TIMESTAMP, 0L);
    }

    // Verifica se il timeout è scaduto in base al timestamp persistente
    public boolean isTimeoutExpired() {
        long lastActive = getLastActiveTimestamp();
        if (lastActive == 0L) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        boolean expired = (currentTime - lastActive) > inactivityTimeoutMillis;
        if (expired) {
            Log.d(TAG, "Timeout scaduto. Tempo trascorso: " + (currentTime - lastActive) + " ms.");
        } else {
            Log.d(TAG, "Timeout NON scaduto. Tempo rimanente: " + (inactivityTimeoutMillis - (currentTime - lastActive)) + " ms.");
        }
        return expired;
    }

    //cancella l'ultimo timestamp
    public void clearLastActiveTimestamp() {
        SharedPreferences prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LAST_ACTIVE_TIMESTAMP).apply();
        Log.d(TAG, "Timestamp di ultima attività cancellato.");
    }

    private boolean isAppInForeground() {
        return activityReferences > 0;
    }


    // --- ActivityLifecycleCallbacks ---
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onActivityCreated: " + activity.getClass().getSimpleName());
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {                            // L'app è tornata in foreground (da background o kill del processo)
        Log.d(TAG, "onActivityStarted: " + activity.getClass().getSimpleName());
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {

            Log.d(TAG, "App è tornata in foreground.");
            stopLogoutTimer(); // Ferma qualsiasi timer attivo (di inattività)
            clearLastActiveTimestamp(); // Pulisci il timestamp persistente
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Log.d(TAG, "onActivityResumed: " + activity.getClass().getSimpleName());
        // Resetta il timer di inattività ogni volta che un'Activity di contenuto riprende

        if (!activity.getClass().getSimpleName().equals("LoginActivity")) {
            resetLogoutTimer(); // Resetta il timer di inattività (foreground o background)
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {                          // L'app è in background
        Log.d(TAG, "onActivityPaused: " + activity.getClass().getSimpleName());
        isActivityChangingConfigurations = activity.isChangingConfigurations();
        // Avvia il timer di logout solo se l'Activity che va in pausa non è LoginActivity e non è un cambio di configurazione.
        if (!activity.getClass().getSimpleName().equals("LoginActivity") && !isActivityChangingConfigurations) {
            startLogoutTimer(); // Avvia il timer di inattività
            saveLastActiveTimestamp(); // Salva il timestamp (per il controllo del "processo killato")
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        Log.d(TAG, "onActivityStopped: " + activity.getClass().getSimpleName());
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // Se l'ultima Activity è stoppata (l'app è andata completamente in background)
            // Il timer è già stato avviato in onActivityPaused.
            Log.d(TAG, "App è andata in background completo (onActivityStopped).");
            saveLastActiveTimestamp(); // Assicurati che l'ultimo timestamp sia salvato
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        Log.d(TAG, "onActivitySaveInstanceState: " + activity.getClass().getSimpleName());
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        Log.d(TAG, "onActivityDestroyed: " + activity.getClass().getSimpleName());
    }
}