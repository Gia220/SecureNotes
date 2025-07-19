package com.example.securenotes.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.securenotes.data.model.Note;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    long insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    LiveData<List<Note>> getAllNotes();             //liveData = osservabile in tempo reale

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    List<Note> getAllNotesListBlocking();

    @Query("SELECT * FROM notes WHERE self_destruct_timestamp > 0 AND self_destruct_timestamp <= :currentTime")
    List<Note> getExpiredNotes(long currentTime);

    // Query per filtrare le note per tag (esempio, useremo in NotesFragment)
    @Query("SELECT * FROM notes WHERE tags LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    LiveData<List<Note>> getNotesByTag(String tag);


    @Query("SELECT * FROM notes WHERE tags LIKE :query || '%' ORDER BY timestamp DESC")
    LiveData<List<Note>> searchNotes(String query); // Restituisce LiveData


    // Metodo di ricerca bloccante (utile per il BackupManager)
    @Query("SELECT * FROM notes WHERE tags LIKE :query || '%' ORDER BY timestamp DESC")
    List<Note> searchNotesListBlocking(String query);

    @Query("SELECT * FROM notes WHERE id = :noteId")
    Note getNoteById(int noteId);
}