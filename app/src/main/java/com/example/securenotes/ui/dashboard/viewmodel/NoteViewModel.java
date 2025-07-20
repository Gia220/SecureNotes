package com.example.securenotes.ui.dashboard.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.securenotes.data.model.Note;
import com.example.securenotes.data.repository.NoteRepository;

import java.util.List;

public class NoteViewModel extends AndroidViewModel {
    private NoteRepository repository;
    private LiveData<List<Note>> allNotes;
    private LiveData<List<Note>> filteredNotes; // Per gestire i filtri dei tag

    public NoteViewModel(Application application) {
        super(application);
        repository = new NoteRepository(application);
        allNotes = repository.getAllDecryptedNotes();
        // Inizialmente i filteredNotes sono uguali a allNotes
        filteredNotes = allNotes;
    }

    public LiveData<List<Note>> getAllNotes() {
        return filteredNotes; // Restituisce i note filtrati o tutti i note se nessun filtro
    }


    public void insert(Note note, NoteRepository.OnNoteInsertedCallback callback) {
        repository.insert(note, callback);
    }


    public void insert(Note note) {
        repository.insert(note); // Chiama il metodo senza callback se non ti interessa l'ID
    }


    public void update(Note note) {
        repository.update(note);
    }

    public void delete(Note note) {
        repository.delete(note);
    }

    public void filterByTag(String tag) {
        if (tag == null) {
            filteredNotes = allNotes; // Mostra tutte le note se il tag è vuoto o "all"
        } else {
            // Esegue la ricerca tramite il repository, che gestisce la decrittografia
            filteredNotes = repository.searchNotes(tag);
        }
    }

    public LiveData<List<Note>> searchNotes(String query) {
        return repository.searchNotes(query);
    }

    public void cleanupExpiredNotes(long currentTime) {

        repository.cleanupExpiredNotes(currentTime);
    }
}