package com.example.securenotes.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore; // Importa @Ignore
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "self_destruct_timestamp")
    public long selfDestructTimestamp;

    @ColumnInfo(name = "tags")
    public String tags;

    // Costruttore originale
    @Ignore
    public Note(String title, String content, long timestamp) {
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.selfDestructTimestamp = 0;
        this.tags = "";
    }

    // Costruttore con selfDestructTimestamp
    @Ignore
    public Note(String title, String content, long timestamp, long selfDestructTimestamp) {
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.selfDestructTimestamp = selfDestructTimestamp;
        this.tags = ""; // Default vuoto
    }

    // Questo è il costruttore PRINCIPALE che Room userà per ricostruire gli oggetti dal database
    public Note(String title, String content, long timestamp, long selfDestructTimestamp, String tags) {
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.selfDestructTimestamp = selfDestructTimestamp;
        this.tags = tags;
    }


    // Metodi getter e setter
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSelfDestructTimestamp() {
        return selfDestructTimestamp;
    }

    public void setSelfDestructTimestamp(long selfDestructTimestamp) {
        this.selfDestructTimestamp = selfDestructTimestamp;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}