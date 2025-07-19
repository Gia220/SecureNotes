package com.example.securenotes.ui.filearchive;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.example.securenotes.R;
import com.example.securenotes.data.model.ArchivedFile;
import com.example.securenotes.ui.filearchive.adapter.FileAdapter;
import com.example.securenotes.ui.filearchive.viewmodel.FileArchiveViewModel;

import java.io.File;
import java.util.List;

public class FileArchiveFragment extends Fragment {

    private static final String TAG = "FileArchiveFragment";
    private FileArchiveViewModel fileArchiveViewModel;
    private FileAdapter fileAdapter;
    private FloatingActionButton addFileFab;

    // Launcher per selezionare un file dal sistema
    private ActivityResultLauncher<Intent> pickFileLauncher = registerForActivityResult(        //apre il file( avvia un altra activity)
            new ActivityResultContracts.StartActivityForResult(),                                //eseguita quando l'utente seleziona un file
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            String originalFileName = getFileName(uri);
                            String mimeType = getContext().getContentResolver().getType(uri);

                            Toast.makeText(getContext(), "Uploading and Encrypting " + originalFileName + "...", Toast.LENGTH_SHORT).show();

                            fileArchiveViewModel.uploadAndEncryptFile(uri, originalFileName, mimeType);     //apre file

                        } catch (Exception e) {
                            Log.e(TAG, "Errore durante la selezione o l'upload del file", e);
                            Toast.makeText(getContext(), "Error uploading file: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_archive, container, false);

        // Inizializzazione della RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.files_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);

        fileAdapter = new FileAdapter();
        recyclerView.setAdapter(fileAdapter);

        // Inizializzazione del ViewModel
        fileArchiveViewModel = new ViewModelProvider(this).get(FileArchiveViewModel.class);

        // Osserva i cambiamenti nei metadati dei file e aggiorna la UI
        fileArchiveViewModel.getAllArchivedFiles().observe(getViewLifecycleOwner(), new Observer<List<ArchivedFile>>() {
            @Override
            public void onChanged(List<ArchivedFile> archivedFiles) {
                fileAdapter.setFiles(archivedFiles);
            }
        });


        addFileFab = view.findViewById(R.id.add_file_fab);
        addFileFab.setOnClickListener(v -> openFilePicker());       //Crea e lancia un Intent con Intent.ACTION_OPEN_DOCUMENT per aprire il selettore di file del sistema

        // Gestione del click su un elemento della lista (per visualizzare il file)
        fileAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ArchivedFile file) {
                viewArchivedFile(file);
            }
        });

        fileAdapter.setOnDeleteClickListener(new FileAdapter.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(ArchivedFile file) {
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Delete File")
                        .setMessage("Are you sure you want to permanently delete this file?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            fileArchiveViewModel.deleteArchivedFile(file);
                            Toast.makeText(getContext(), "File deleted: " + file.getOriginalName(), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        fileArchiveViewModel.cleanTempFiles();
    }


    // Apre il picker di file del sistema
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickFileLauncher.launch(intent);
    }

    // Recupera il nome del file dall'URI
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void viewArchivedFile(ArchivedFile file) {
        Toast.makeText(getContext(), "Decrittografia di " + file.getOriginalName() + "...", Toast.LENGTH_SHORT).show();
        fileArchiveViewModel.decryptFileForViewing(file).observe(getViewLifecycleOwner(), new Observer<File>() {        //decriptare il file
            @Override
            public void onChanged(File decryptedFile) {
                if (decryptedFile != null && decryptedFile.exists()) {
                    try {
                        Uri contentUri = FileProvider.getUriForFile(                            //ottengo URI
                                getContext(),
                                getContext().getApplicationContext().getPackageName() + ".fileprovider",
                                decryptedFile
                        );
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setDataAndType(contentUri, file.getMimeType());
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(viewIntent);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "FileProvider error or URI creation failed: " + e.getMessage());
                        Toast.makeText(getContext(), "Unable to open file: internal error.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Errore nell'apertura del file con app esterna: " + file.getOriginalName(), e);
                        Toast.makeText(getContext(), "No app found to open the file or error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Decryption error or file not found.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}