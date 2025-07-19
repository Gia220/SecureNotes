package com.example.securenotes.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Definisce nome della tabella
@Entity(tableName = "archived_files")
public class ArchivedFile {
    // Chiave primaria auto-generata per ogni file
    @PrimaryKey(autoGenerate = true)
    public int id;

    // Nome originale del file (non criptato)
    @ColumnInfo(name = "original_name")
    public String originalName;

    // Nome del file criptato sul disco (es. un UUID)
    @ColumnInfo(name = "encrypted_filename")
    public String encryptedFilename;

    // MIME type del file originale (es. "application/pdf", "image/jpeg")
    @ColumnInfo(name = "mime_type")
    public String mimeType;

    // Timestamp di quando il file è stato archiviato
    @ColumnInfo(name = "timestamp")
    public long timestamp;

    // Costruttore per creare un oggetto ArchivedFile
    public ArchivedFile(String originalName, String encryptedFilename, String mimeType, long timestamp) {
        this.originalName = originalName;
        this.encryptedFilename = encryptedFilename;
        this.mimeType = mimeType;
        this.timestamp = timestamp;
    }

    // Metodi getter e setter (Room li usa)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getEncryptedFilename() {
        return encryptedFilename;
    }

    public void setEncryptedFilename(String encryptedFilename) {
        this.encryptedFilename = encryptedFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}