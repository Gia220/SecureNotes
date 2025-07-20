package com.example.securenotes.ui.filearchive.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.securenotes.data.model.ArchivedFile;
import com.example.securenotes.data.repository.FileArchiveRepository;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileArchiveViewModel extends AndroidViewModel {
    private static final String TAG = "FileArchiveViewModel";
    private FileArchiveRepository repository;
    private LiveData<List<ArchivedFile>> allArchivedFiles;
    private ExecutorService viewModelExecutor; // Executor per operazioni ViewModel asincrone

    public FileArchiveViewModel(@NonNull Application application) {
        super(application);
        repository = new FileArchiveRepository(application);
        allArchivedFiles = repository.getAllArchivedFilesMetadata();
        viewModelExecutor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ArchivedFile>> getAllArchivedFiles() {
        return allArchivedFiles;
    }

    // Metodo per caricare e criptare un file
    public void uploadAndEncryptFile(Uri uri, String originalFileName, String mimeType) {
        viewModelExecutor.execute(() -> {
            try {
                ArchivedFile newFile = repository.encryptAndSaveFile(uri, originalFileName, mimeType);
                if (newFile != null) {
                    Log.d(TAG, "File " + originalFileName + " criptato e salvato con ID: " + newFile.getId());
                } else {
                    Log.e(TAG, "Fallimento nella crittografia e salvataggio del file: " + originalFileName);
                }
            } catch (IOException | GeneralSecurityException e) {
                Log.e(TAG, "Errore durante l'upload e la crittografia del file: " + originalFileName, e);

            }
        });
    }


    public LiveData<File> decryptFileForViewing(ArchivedFile archivedFile) {
        MutableLiveData<File> decryptedFileLiveData = new MutableLiveData<>();
        viewModelExecutor.execute(() -> {
            try {
                File decryptedTempFile = repository.decryptFileToTemp(archivedFile);
                decryptedFileLiveData.postValue(decryptedTempFile);
                Log.d(TAG, "File " + archivedFile.getOriginalName() + " decriptato per visualizzazione.");
            } catch (IOException | GeneralSecurityException e) {
                Log.e(TAG, "Errore durante la decrittografia del file per la visualizzazione: " + archivedFile.getOriginalName(), e);
                decryptedFileLiveData.postValue(null); // Segnala errore alla UI
                // Puoi anche usare un LiveData per notificare specifici errori
            }
        });
        return decryptedFileLiveData;
    }

    // Metodo per eliminare un file
    public void deleteArchivedFile(ArchivedFile archivedFile) {
        viewModelExecutor.execute(() -> repository.deleteFileMetadata(archivedFile));
    }

    // Metodo per pulire i file temporanei decriptati (chiamato ad esempio all'uscita dall'Activity)
    public void cleanTempFiles() {
        viewModelExecutor.execute(() -> repository.cleanTempDecryptedFiles());
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        viewModelExecutor.shutdownNow();

    }
}