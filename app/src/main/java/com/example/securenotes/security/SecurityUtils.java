package com.example.securenotes.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtils {

    private static final String TAG = "SecurityUtils";
    private static final String STANDARD_PREFS_FILE = "secure_notes_prefs";
    private static final String KEY_PIN_SECRET_SEED = "pin_secret_seed"; // Seed per la chiave del PIN (PBKDF2)
    private static final String KEY_NOTE_SECRET_SEED = "note_secret_seed"; // Seed per la chiave delle note/file (PBKDF2)
    private static final String KEY_FILE_MASTER_SEED = "file_master_seed"; // Seed per la MasterKey dei file (PBKDF2)

    private static final int AES_KEY_SIZE_BITS = 256;

    public static final int GCM_IV_SIZE_BYTES = 12; // Modificato per GCM raccomandato (96 bit)
    private static final int GCM_TAG_LENGTH_BITS = 128; // Lunghezza del tag di autenticazione GCM (128 bit)


    // Per PBKDF2
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int PBKDF2_KEY_LENGTH_BITS = 256;

    public static SharedPreferences getStandardSharedPreferences(Context context) {
        return context.getSharedPreferences(STANDARD_PREFS_FILE, Context.MODE_PRIVATE);             //inizializzo
    }

    //Salva una stringa (value) associata a una key nelle SharedPreferences
    public static boolean saveSecureString(Context context, String key, String value) {
        SharedPreferences sharedPrefs = getStandardSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(key, value);
        return editor.commit();
    }

    public static String getSecureString(Context context, String key, String defaultValue) {
        SharedPreferences sharedPrefs = getStandardSharedPreferences(context);
        return sharedPrefs.getString(key, defaultValue);
    }

    //Verifica se una specifica chiave esiste nelle SharedPreferences
    public static boolean containsKey(Context context, String key) {
        SharedPreferences sharedPrefs = getStandardSharedPreferences(context);
        return sharedPrefs.contains(key);
    }

    //Metodo per generare le key
    private static SecretKey getOrCreateAesKeyFromPBKDF2(Context context, String seedKey) throws GeneralSecurityException {
        String seedString = getSecureString(context, seedKey, null);            //cerca il seed da sharedPreferences
        if (seedString == null) {
            Log.d(TAG, "Generazione nuovo seed per la chiave PBKDF2 per alias: " + seedKey);
            byte[] newSeed = new byte[16];
            new SecureRandom().nextBytes(newSeed);
            seedString = Base64.encodeToString(newSeed, Base64.DEFAULT);
            saveSecureString(context, seedKey, seedString);
        }

        byte[] seedBytes = Base64.decode(seedString, Base64.DEFAULT);       //seedString viene decodificato da Base64 a seedBytes per essere usato dall'algoritmo PBKDF2
        char[] password = Base64.encodeToString(seedBytes, Base64.DEFAULT).toCharArray();

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");            //Ottiene un'istanza di un algoritmo di derivazione della chiave PBKDF2
            KeySpec spec = new PBEKeySpec(password, seedBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS);      //Crea una specifica per la chiave PBKDF2 (con passwor e seedBytes)
            SecretKey secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");  //crea la key
            return secretKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Errore nella derivazione della chiave PBKDF2 per alias: " + seedKey, e);
            throw new GeneralSecurityException("Errore nella generazione della chiave: " + seedKey, e);
        }
    }

    //metodi public per accedere a getOrCreateAesKeyFromPBKDF2
    public static SecretKey getOrCreatePinAesKey(Context context) throws GeneralSecurityException {
        return getOrCreateAesKeyFromPBKDF2(context, KEY_PIN_SECRET_SEED);
    }

    //metodi public per accedere a getOrCreateAesKeyFromPBKDF2
    public static SecretKey getOrCreateNotesAndFilesAesKey(Context context) throws GeneralSecurityException {
        return getOrCreateAesKeyFromPBKDF2(context, KEY_NOTE_SECRET_SEED);
    }

    // Generatore Initialization Vector
    public static byte[] generateRandomIv() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[GCM_IV_SIZE_BYTES]; // Utilizza la nuova dimensione IV
        secureRandom.nextBytes(iv);
        return iv;
    }

    // ottenere la lunghezza del tag GCM
    public static int getGcmTagLengthBytes() {
        return GCM_TAG_LENGTH_BITS / 8; // Converte bit in byte
    }
}