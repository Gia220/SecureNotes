package com.example.securenotes.utils;

public class Constants {
    public static final String KEY_USER_PIN = "user_pin";
    public static final String TRANSFORMATION = "AES/GCM/NoPadding";

    // Costanti per il timeout
    public static final long DEFAULT_INACTIVITY_TIMEOUT_MINUTES = 3;

    // Chiavi per le impostazioni generali dell'app
    public static final String PREFS_APP_SETTINGS = "app_settings";
    public static final String KEY_NIGHT_MODE_PREFERENCE = "night_mode_preference";

    // Valori per la preferenza della modalità notte
    public static final int NIGHT_MODE_FOLLOW_SYSTEM = 0; // Segui il sistema
    public static final int NIGHT_MODE_LIGHT = 1;         // Modalità chiara
    public static final int NIGHT_MODE_DARK = 2;          // Modalità scura


}