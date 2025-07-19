package com.example.securenotes.data.repository;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.securenotes.data.dao.ArchivedFileDao;
import com.example.securenotes.data.database.AppDatabase;
import com.example.securenotes.data.model.ArchivedFile;
import com.example.securenotes.security.SecurityUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec; // <<< NUOVO: Importa GCMParameterSpec
import javax.crypto.spec.IvParameterSpec;


public class FileArchiveRepository {
    private static final String TAG = "FileArchiveRepository";
    private ArchivedFileDao archivedFileDao;
    private LiveData<List<ArchivedFile>> allArchivedFiles;
    private MutableLiveData<List<ArchivedFile>> allDecryptedFilesMetadata;
    private ExecutorService executorService;
    private Context applicationContext;

    private static final String ENCRYPTED_FILES_DIR = "encrypted_files";
    private static final String TEMP_DECRYPTED_FILES_DIR = "temp_decrypted_files";


    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    public FileArchiveRepository(Application application) {
        AppDatabase database = AppDatabase.getDatabase(application);
        archivedFileDao = database.archivedFileDao();
        allArchivedFiles = archivedFileDao.getAllArchivedFiles();
        allDecryptedFilesMetadata = new MutableLiveData<>();
        executorService = Executors.newSingleThreadExecutor();
        this.applicationContext = application.getApplicationContext();

        allArchivedFiles.observeForever(archivedFileList -> {
            executorService.execute(() -> {
                allDecryptedFilesMetadata.postValue(archivedFileList);
            });
        });
    }

    public LiveData<List<ArchivedFile>> getAllArchivedFilesMetadata() {
        return allDecryptedFilesMetadata;
    }

    public void insertFileMetadata(ArchivedFile archivedFile) {
        executorService.execute(() -> archivedFileDao.insert(archivedFile));
    }

    public void updateFileMetadata(ArchivedFile archivedFile) {
        executorService.execute(() -> archivedFileDao.update(archivedFile));
    }

    public void deleteFileMetadata(ArchivedFile archivedFile) {
        executorService.execute(() -> {
            File encryptedFile = new File(applicationContext.getFilesDir(), ENCRYPTED_FILES_DIR + File.separator + archivedFile.getEncryptedFilename());
            if (encryptedFile.exists()) {
                if (encryptedFile.delete()) {
                    Log.d(TAG, "File criptato eliminato con successo: " + encryptedFile.getName());
                } else {
                    Log.e(TAG, "Impossibile eliminare il file criptato: " + encryptedFile.getName());
                }
            }
            archivedFileDao.delete(archivedFile);
        });
    }


    public ArchivedFile encryptAndSaveFile(Uri uri, String originalFileName, String mimeType) throws IOException, GeneralSecurityException {
        String encryptedFileName = UUID.randomUUID().toString();
        File encryptedFile = new File(applicationContext.getFilesDir(), ENCRYPTED_FILES_DIR + File.separator + encryptedFileName);

        if (!encryptedFile.getParentFile().exists()) {
            encryptedFile.getParentFile().mkdirs();
        }

        SecretKey fileSecretKey = SecurityUtils.getOrCreateNotesAndFilesAesKey(applicationContext);
        if (fileSecretKey == null) {
            throw new GeneralSecurityException("Chiave AES per file non disponibile.");
        }


        byte[] iv = SecurityUtils.generateRandomIv();
        GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv); // Tag length in bits, IV

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, fileSecretKey, gcmSpec);

        try (InputStream is = applicationContext.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(encryptedFile)) {

            // Scrivi prima l'IV all'inizio del file criptato            e poi i dati del file
            os.write(iv);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                byte[] encryptedBytes = cipher.update(buffer, 0, bytesRead);
                if (encryptedBytes != null) {
                    os.write(encryptedBytes);
                }
            }
            byte[] finalEncryptedBytes = cipher.doFinal(); // Completa l'operazione di crittografia (qui viene aggiunto il tag GCM)
            if (finalEncryptedBytes != null) {
                os.write(finalEncryptedBytes);
            }
            os.flush();
            Log.d(TAG, "File criptato salvato: " + encryptedFile.getAbsolutePath());

            ArchivedFile archivedFile = new ArchivedFile(originalFileName, encryptedFileName, mimeType, System.currentTimeMillis());
            insertFileMetadata(archivedFile);
            return archivedFile;

        } catch (IOException | GeneralSecurityException e) {
            Log.e(TAG, "Errore durante la crittografia e il salvataggio del file: " + originalFileName, e);
            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }
            throw e;
        }
    }


    public File decryptFileToTemp(ArchivedFile archivedFile) throws IOException, GeneralSecurityException {
        File encryptedFile = new File(applicationContext.getFilesDir(), ENCRYPTED_FILES_DIR + File.separator + archivedFile.getEncryptedFilename());
        File decryptedTempDir = new File(applicationContext.getCacheDir(), TEMP_DECRYPTED_FILES_DIR);
        if (!decryptedTempDir.exists()) {
            decryptedTempDir.mkdirs();
        }
        File decryptedTempFile = new File(decryptedTempDir, archivedFile.getEncryptedFilename() + "_decrypted." + getFileExtension(archivedFile.getOriginalName()));

        SecretKey fileSecretKey = SecurityUtils.getOrCreateNotesAndFilesAesKey(applicationContext);
        if (fileSecretKey == null) {
            throw new GeneralSecurityException("Chiave AES per file non disponibile.");
        }

        try (InputStream is = new FileInputStream(encryptedFile);
             OutputStream os = new FileOutputStream(decryptedTempFile)) {

            // Leggi prima l'IV (primi 12 byte per AES/GCM raccomandato)
            byte[] iv = new byte[SecurityUtils.GCM_IV_SIZE_BYTES]; // Usa la dimensione IV di GCM
            int bytesReadIv = is.read(iv);
            if (bytesReadIv != SecurityUtils.GCM_IV_SIZE_BYTES) {
                throw new IOException("Errore: IV non letto correttamente o dimensione errata.");
            }
            GCMParameterSpec gcmSpec = new GCMParameterSpec(SecurityUtils.getGcmTagLengthBytes() * 8, iv); // Tag length in bits, IV

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, fileSecretKey, gcmSpec); // Inizializza con GCMParameterSpec

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {           //Legge il file criptato in blocchi
                byte[] decryptedBytes = cipher.update(buffer, 0, bytesRead); //decisfra ogni blocco
                if (decryptedBytes != null) {
                    os.write(decryptedBytes);
                }
            }
            byte[] finalDecryptedBytes = cipher.doFinal(); // doFinal verifica il tag e completa la decrittografia
            if (finalDecryptedBytes != null) {
                os.write(finalDecryptedBytes);
            }
            os.flush();
            Log.d(TAG, "File decriptato temporaneamente in: " + decryptedTempFile.getAbsolutePath());
            return decryptedTempFile;

        } catch (IOException | GeneralSecurityException e) {
            Log.e(TAG, "Errore durante la decrittografia del file (GCM): " + archivedFile.getOriginalName(), e);
            if (decryptedTempFile.exists()) {
                decryptedTempFile.delete();
            }
            throw e;
        }
    }

    public void cleanTempDecryptedFiles() {
        File decryptedTempDir = new File(applicationContext.getCacheDir(), TEMP_DECRYPTED_FILES_DIR);
        if (decryptedTempDir.exists() && decryptedTempDir.isDirectory()) {
            for (File file : decryptedTempDir.listFiles()) {
                if (file.delete()) {
                    Log.d(TAG, "File temporaneo eliminato: " + file.getName());
                } else {
                    Log.w(TAG, "Impossibile eliminare il file temporaneo: " + file.getName());
                }
            }

        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }
}