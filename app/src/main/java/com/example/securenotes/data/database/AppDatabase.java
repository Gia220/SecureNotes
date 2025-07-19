package com.example.securenotes.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.securenotes.data.dao.NoteDao;
import com.example.securenotes.data.model.Note;
import com.example.securenotes.data.dao.ArchivedFileDao;
import com.example.securenotes.data.model.ArchivedFile;


@Database(entities = {Note.class, ArchivedFile.class}, version = 4, exportSchema = false)       //Dichiara le entità (Note e ArchivedFile) che fanno parte di questo database e la sua versione corrente
public abstract class AppDatabase extends RoomDatabase {

    public abstract NoteDao noteDao();
    public abstract ArchivedFileDao archivedFileDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "secure_notes_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}