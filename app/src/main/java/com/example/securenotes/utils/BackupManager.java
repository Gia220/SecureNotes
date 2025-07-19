package com.example.securenotes.utils;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.example.securenotes.data.database.AppDatabase;
import com.example.securenotes.data.model.ArchivedFile;
import com.example.securenotes.data.model.Note;
import com.example.securenotes.security.SecurityUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;


public class BackupManager {

    private static final String TAG = "BackupManager";
    private Context context;
    private AppDatabase db;

    private static final String BACKUP_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int BACKUP_PBKDF2_ITERATIONS = 50000;
    private static final int BACKUP_SALT_SIZE_BYTES = 16;
    private static final int BACKUP_AES_KEY_BITS = 256;
    private static final int BACKUP_GCM_IV_SIZE_BYTES = 12;

    private static final String BACKUP_NOTES_FILE = "notes.enc";
    private static final String BACKUP_FILES_METADATA_FILE = "files_metadata.enc";
    private static final String BACKUP_ARCHIVED_FILES_DIR = "archived_files";

    public BackupManager(Context context) {
        this.context = context;
        this.db = AppDatabase.getDatabase(context);
    }


    public void exportEncryptedBackup(String backupPassword, OutputStream outputStream) throws IOException, GeneralSecurityException {
        // 1. Derivare la chiave AES dalla password di backup dell'utente
        byte[] salt = generateSalt();
        SecretKey backupAesKey = deriveKeyFromPassword(backupPassword, salt);

        // 2. Preparare la cartella temporanea per i dati da zippare
        File tempDir = new File(context.getCacheDir(), "backup_temp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File notesEncryptedTempFile = new File(tempDir, BACKUP_NOTES_FILE);
        File filesMetadataEncryptedTempFile = new File(tempDir, BACKUP_FILES_METADATA_FILE);

        try {
            // 3. Criptare e salvare le note in un file temporaneo
            List<Note> allNotes = db.noteDao().getAllNotesListBlocking();
            StringBuilder notesContent = new StringBuilder();
            for (Note note : allNotes) {
                notesContent.append(note.getId()).append("|")
                        .append(note.getTitle()).append("|")
                        .append(note.getContent()).append("|")
                        .append(note.getTimestamp()).append("\n");
            }
            encryptToFile(notesContent.toString().getBytes(StandardCharsets.UTF_8), notesEncryptedTempFile, backupAesKey);

            // 4. Criptare e salvare i metadati dei file in un file temporaneo
            List<ArchivedFile> allArchivedFiles = db.archivedFileDao().getAllArchivedFilesListBlocking();
            StringBuilder filesMetadataContent = new StringBuilder();
            for (ArchivedFile file : allArchivedFiles) {
                filesMetadataContent.append(file.getId()).append("|")
                        .append(file.getOriginalName()).append("|")
                        .append(file.getEncryptedFilename()).append("|")
                        .append(file.getMimeType()).append("|")
                        .append(file.getTimestamp()).append("\n");
            }
            encryptToFile(filesMetadataContent.toString().getBytes(StandardCharsets.UTF_8), filesMetadataEncryptedTempFile, backupAesKey);

            // 5. Creare il file ZIP criptato e scriverlo nell'OutputStream fornito
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
                // Aggiungi il sale al ZIP (necessario per la decrittografia del backup)
                zos.putNextEntry(new ZipEntry("salt.enc"));
                zos.write(salt);
                zos.closeEntry();

                // Aggiungi le note criptate al ZIP
                addFileToZip(notesEncryptedTempFile, BACKUP_NOTES_FILE, zos);

                // Aggiungi i metadati dei file criptati al ZIP
                addFileToZip(filesMetadataEncryptedTempFile, BACKUP_FILES_METADATA_FILE, zos);

                // Aggiungi i file archiviati (già criptati dall'app) al ZIP
                File archivedFilesDir = new File(context.getFilesDir(), "encrypted_files");
                if (archivedFilesDir.exists() && archivedFilesDir.isDirectory()) {
                    for (File file : archivedFilesDir.listFiles()) {
                        if (file.isFile()) {
                            addFileToZip(file, BACKUP_ARCHIVED_FILES_DIR + File.separator + file.getName(), zos);
                        }
                    }
                }
            }
            Log.d(TAG, "Backup criptato creato con successo nell'OutputStream fornito.");

        } finally {
            // Pulisci i file temporanei
            deleteRecursive(tempDir);
        }
    }


    private SecretKey deriveKeyFromPassword(String password, byte[] salt) throws GeneralSecurityException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, BACKUP_PBKDF2_ITERATIONS, BACKUP_AES_KEY_BITS);
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Errore nella derivazione della chiave di backup.", e);
            throw new GeneralSecurityException("Impossibile derivare la chiave di backup.", e);
        }
    }

    private void encryptToFile(byte[] data, File outputFile, SecretKey secretKey) throws IOException, GeneralSecurityException {
        byte[] iv = generateSalt();
        GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

        Cipher cipher = Cipher.getInstance(BACKUP_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(iv);
            byte[] encryptedBytes = cipher.doFinal(data);
            fos.write(encryptedBytes);
            fos.flush();
        }
    }

    private byte[] decryptFromFile(File inputFile, SecretKey secretKey) throws IOException, GeneralSecurityException {
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            byte[] iv = new byte[BACKUP_GCM_IV_SIZE_BYTES];
            if (fis.read(iv) != BACKUP_GCM_IV_SIZE_BYTES) {
                throw new IOException("IV non letto correttamente.");
            }
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv);

            Cipher cipher = Cipher.getInstance(BACKUP_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] encryptedDataWithTag = new byte[fis.available()];
            fis.read(encryptedDataWithTag);
            return cipher.doFinal(encryptedDataWithTag);
        }
    }

    private void addFileToZip(File fileToZip, String entryName, ZipOutputStream zos) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileToZip))) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
            zos.closeEntry();
            Log.d(TAG, "Aggiunto al ZIP: " + entryName);
        }
    }

    private byte[] generateSalt() throws NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[BACKUP_SALT_SIZE_BYTES];
        random.nextBytes(salt);
        return salt;
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        if (fileOrDirectory.delete()) {
            Log.d(TAG, "Eliminato: " + fileOrDirectory.getName());
        } else {
            Log.w(TAG, "Impossibile eliminare: " + fileOrDirectory.getName());
        }
    }
}