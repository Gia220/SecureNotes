package com.example.securenotes.data.repository;

import android.app.Application;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.securenotes.data.dao.NoteDao;
import com.example.securenotes.data.database.AppDatabase;
import com.example.securenotes.data.model.Note;
import com.example.securenotes.security.SecurityUtils;
import com.example.securenotes.utils.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import static javax.crypto.Cipher.DECRYPT_MODE;


public class NoteRepository {
    private static final String TAG = "NoteRepository";
    private NoteDao noteDao;
    private LiveData<List<Note>> allEncryptedNotes;
    private MutableLiveData<List<Note>> allDecryptedNotes;
    private ExecutorService executorService;

    private static final String TRANSFORMATION = Constants.TRANSFORMATION;

    private Context applicationContext;

    public NoteRepository(Application application) {
        AppDatabase database = AppDatabase.getDatabase(application);
        noteDao = database.noteDao();
        allEncryptedNotes = noteDao.getAllNotes(); // LiveData dalle note non filtrate
        allDecryptedNotes = new MutableLiveData<>();
        executorService = Executors.newSingleThreadExecutor();
        this.applicationContext = application.getApplicationContext();

        allEncryptedNotes.observeForever(encryptedList -> {
            executorService.execute(() -> {
                List<Note> decryptedList = new ArrayList<>();
                SecretKey noteSecretKey = null;
                try {
                    noteSecretKey = SecurityUtils.getOrCreateNotesAndFilesAesKey(applicationContext);
                } catch (GeneralSecurityException e) {
                    Log.e(TAG, "Impossibile recuperare la chiave di crittografia per le note (PBKDF2 ha fallito). Le note non verranno decriptate.", e);
                }

                if (noteSecretKey != null) {
                    for (Note encryptedNote : encryptedList) {
                        try {
                            String decryptedContent = decrypt(encryptedNote.getContent(), noteSecretKey);
                            Note decryptedNote = new Note(encryptedNote.getTitle(), decryptedContent, encryptedNote.getTimestamp(), encryptedNote.getSelfDestructTimestamp(), encryptedNote.getTags());
                            decryptedNote.setId(encryptedNote.getId());
                            decryptedList.add(decryptedNote);
                        } catch (GeneralSecurityException e) {
                            Log.e(TAG, "Errore durante la decrittografia della nota con ID: " + encryptedNote.getId() + ". Contenuto non mostrato.", e);
                        }
                    }
                }
                allDecryptedNotes.postValue(decryptedList);
            });
        });
    }

    public LiveData<List<Note>> getAllDecryptedNotes() {
        return allDecryptedNotes;
    }

    // --- NUOVA VERSIONE DEL METODO INSERT CON CALLBACK ---
    public void insert(Note note, OnNoteInsertedCallback callback) {
        executorService.execute(() -> {
            try {
                SecretKey noteSecretKey = SecurityUtils.getOrCreateNotesAndFilesAesKey(applicationContext);
                if (noteSecretKey == null) {
                    Log.e(TAG, "Chiave AES per note non disponibile. Impossibile criptare/salvare la nota.");
                    // Potresti voler notificare un errore anche tramite la callback
                    return;
                }
                String encryptedContent = encrypt(note.getContent(), noteSecretKey);
                note.setContent(encryptedContent);

                Log.d(TAG, "Inserting note with selfDestructTimestamp: " + note.getSelfDestructTimestamp());

                long newId = noteDao.insert(note); // Ottieni l'ID qui!

                // Esegui la callback sul thread principale (UI thread)
                if (callback != null) {
                    // Poiché la callback viene chiamata dal tuo executorService,
                    // devi assicurarti che venga eseguita sull'UI thread se aggiorna la UI.
                    // Il ViewModel si occupa di questo, ma il Repository non lo sa.
                    // Per sicurezza, potresti voler aggiungere un gestore per postare sull'UI thread se la callback lo richiede.
                    // Ad esempio: new Handler(Looper.getMainLooper()).post(() -> callback.onNoteInserted((int) newId));
                    // Tuttavia, se il ViewModel gestirà la callback e la UI, non è strettamente necessario qui.
                    callback.onNoteInserted((int) newId);
                }

            } catch (GeneralSecurityException e) {
                Log.e(TAG, "Errore durante l'inserimento della nota criptata: " + e.getMessage(), e);
            }
        });
    }

    // --- METODO INSERT PRECEDENTE (DA RIMUOVERE O MODIFICARE SE LO USI ALTROVE) ---
    // Se hai altri punti nel codice che chiamano `insert(Note note)` senza callback,
    // dovrai decidere se modificarli per usare la callback o se mantenere un overload.
    // Per completezza, potresti avere anche:
    public void insert(Note note) {
        insert(note, null); // Chiama il metodo con callback, passando null se non ti interessa l'ID
    }


    public void update(Note note) {
        executorService.execute(() -> {
            try {
                SecretKey noteSecretKey = SecurityUtils.getOrCreateNotesAndFilesAesKey(applicationContext);
                if (noteSecretKey == null) {
                    Log.e(TAG, "Chiave AES per note non disponibile. Impossibile criptare/aggiornare la nota.");
                    return;
                }
                String encryptedContent = encrypt(note.getContent(), noteSecretKey);
                note.setContent(encryptedContent);

                Log.d(TAG, "Updating note with selfDestructTimestamp: " + note.getSelfDestructTimestamp());

                noteDao.update(note);
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "Errore durante l'aggiornamento della nota criptata: " + e.getMessage(), e);
            }
        });
    }

    public void delete(Note note) {
        executorService.execute(() -> noteDao.delete(note));
    }

    public LiveData<List<Note>> searchNotes(String query) {
        return Transformations.map(noteDao.searchNotes(query), encryptedNotesList -> {
            List<Note> decryptedList = new ArrayList<>();
            SecretKey noteSecretKey = null;
            try {
                noteSecretKey = SecurityUtils.getOrCreateNotesAndFilesAesKey(applicationContext);
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "Impossibile recuperare la chiave di crittografia per la ricerca (PBKDF2 ha fallito).", e);
                return decryptedList; // Restituisce lista vuota in caso di errore chiave
            }

            if (noteSecretKey != null) {
                for (Note encryptedNote : encryptedNotesList) {
                    try {
                        String decryptedContent = decrypt(encryptedNote.getContent(), noteSecretKey);
                        Note decryptedNote = new Note(encryptedNote.getTitle(), decryptedContent, encryptedNote.getTimestamp(), encryptedNote.getSelfDestructTimestamp(), encryptedNote.getTags());
                        decryptedNote.setId(encryptedNote.getId());
                        decryptedList.add(decryptedNote);
                    } catch (GeneralSecurityException e) {
                        Log.e(TAG, "Errore durante la decrittografia del risultato di ricerca per nota ID: " + encryptedNote.getId(), e);
                    }
                }
            }
            return decryptedList;
        });
    }

    private String encrypt(String data, SecretKey secretKey) throws GeneralSecurityException, NoSuchAlgorithmException {
        try {
            byte[] iv = SecurityUtils.generateRandomIv();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Errore di sicurezza durante la crittografia (GCM)", e);
            throw e;
        }
    }

    private String decrypt(String encryptedData, SecretKey secretKey) throws GeneralSecurityException {
        try {
            byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);

            byte[] iv = new byte[SecurityUtils.GCM_IV_SIZE_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

            byte[] ciphertext = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Errore di decrittografia (GCM): " + e.getMessage(), e);
            throw e;
        }
    }

    // --- INTERFACCIA DI CALLBACK PER L'INSERIMENTO DI NOTE ---
    public interface OnNoteInsertedCallback {
        void onNoteInserted(int noteId);
    }

    public void cleanupExpiredNotes(long currentTime) {
        executorService.execute(() -> {
            Log.d(TAG, "Esecuzione pulizia note scadute nel Repository.");
            try {
                // Recupera tutte le note che sono scadute
                List<Note> expiredNotes = noteDao.getExpiredNotes(currentTime);
                if (expiredNotes != null && !expiredNotes.isEmpty()) {
                    Log.i(TAG, "Trovate " + expiredNotes.size() + " note scadute durante la pulizia. Eliminazione...");
                    // Elimina ogni nota scaduta
                    for (Note note : expiredNotes) {
                        noteDao.delete(note);
                        Log.d(TAG, "Nota eliminata tramite pulizia: ID=" + note.getId() + ", Titolo='" + note.getTitle() + "'");
                    }
                } else {
                    Log.d(TAG, "Nessuna nota scaduta trovata durante la pulizia.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Errore durante la pulizia delle note scadute: " + e.getMessage(), e);
            }
        });
    }
}