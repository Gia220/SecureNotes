package com.example.securenotes.data.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.securenotes.data.database.AppDatabase;
import com.example.securenotes.data.dao.NoteDao;
import com.example.securenotes.data.model.Note;

public class NoteSelfDestructReceiver extends BroadcastReceiver {

    public static final String ACTION_SELF_DESTRUCT_NOTE = "com.example.securenotes.ACTION_SELF_DESTRUCT_NOTE";
    public static final String EXTRA_NOTE_ID = "note_id";

    private static final String TAG = "SelfDestructReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Ricevuto broadcast! Action: " + (intent != null ? intent.getAction() : "null"));

        if (intent != null && ACTION_SELF_DESTRUCT_NOTE.equals(intent.getAction())) {
            int noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1);
            Log.d(TAG, "onReceive: Tentativo di autodistruzione per nota ID: " + noteId);

            if (noteId != -1) {
                // Esegui l'eliminazione della nota in un thread separato
                new Thread(() -> {
                    NoteDao noteDao = AppDatabase.getDatabase(context).noteDao();
                    Note noteToDelete = noteDao.getNoteById(noteId);

                    if (noteToDelete != null) {
                        Log.i(TAG, "onReceive Thread: Trovata nota con ID " + noteId + ", titolo: '" + noteToDelete.getTitle() + "'. Eliminazione in corso...");
                        noteDao.delete(noteToDelete);
                        Log.i(TAG, "onReceive Thread: Nota ID " + noteId + " eliminata con successo.");
                    } else {
                        Log.w(TAG, "onReceive Thread: Nota con ID " + noteId + " non trovata nel database per l'autodistruzione. Forse è già stata eliminata?");
                    }
                }).start();
            } else {
                Log.e(TAG, "onReceive: ID nota non valido ricevuto nell'intent per l'autodistruzione.");
            }
        } else {
            Log.e(TAG, "onReceive: Azione dell'intent non corrisponde a " + ACTION_SELF_DESTRUCT_NOTE);
        }
    }
}