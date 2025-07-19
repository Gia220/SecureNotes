package com.example.securenotes.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.securenotes.data.model.ArchivedFile;

import java.util.List;

@Dao
public interface ArchivedFileDao {
    @Insert
    long insert(ArchivedFile archivedFile);

    @Update
    void update(ArchivedFile archivedFile);

    @Delete
    void delete(ArchivedFile archivedFile);

    @Query("SELECT * FROM archived_files ORDER BY timestamp DESC")
    LiveData<List<ArchivedFile>> getAllArchivedFiles();


    @Query("SELECT * FROM archived_files ORDER BY timestamp DESC")
    List<ArchivedFile> getAllArchivedFilesListBlocking();


    @Query("SELECT * FROM archived_files WHERE id = :fileId LIMIT 1")
    ArchivedFile getArchivedFileById(int fileId);
}