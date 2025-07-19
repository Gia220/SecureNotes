package com.example.securenotes.ui.dashboard;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.example.securenotes.R;
import com.example.securenotes.data.model.Note;
import com.example.securenotes.ui.dashboard.adapter.NoteAdapter;
import com.example.securenotes.ui.dashboard.viewmodel.NoteViewModel;
import com.example.securenotes.ui.noteeditor.NoteEditorActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class NotesFragment extends Fragment {

    private static final String TAG = "NotesFragment";
    private NoteViewModel noteViewModel;
    private NoteAdapter adapter;
    private FloatingActionButton addNoteFab;
    private EditText searchNotesEditText;

    private LiveData<List<Note>> currentSearchLiveData;

    // Launcher for requesting SCHEDULE_EXACT_ALARM permission
    private ActivityResultLauncher<Intent> requestScheduleExactAlarmPermissionLauncher;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the launcher here, within the fragment's lifecycle
        requestScheduleExactAlarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                        if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                            Log.d(TAG, "Permesso SCHEDULE_EXACT_ALARM concesso dopo la richiesta. Avvio NoteEditorActivity.");
                            // Permission granted, now launch NoteEditorActivity
                            startNoteEditorActivity();
                        } else {
                            Log.w(TAG, "Permesso SCHEDULE_EXACT_ALARM negato dopo la richiesta.");
                            Toast.makeText(requireContext(), "Permission to set exact alarms was denied. Auto-delete notes may not work precisely.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.notes_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);

        adapter = new NoteAdapter();
        recyclerView.setAdapter(adapter);

        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        performExpiredNotesCleanup();
        noteViewModel.getAllNotes().observe(getViewLifecycleOwner(), new Observer<List<Note>>() {
            @Override
            public void onChanged(List<Note> notes) {
                Log.d(TAG, "Note caricate nell'Observer. Numero di note: " + notes.size());
                for (Note note : notes) {
                    Log.d(TAG, "Nota ID: " + note.getId() +
                            ", Titolo: " + note.getTitle() +
                            ", Autodistruzione: " + note.getSelfDestructTimestamp() +
                            ", Tags: " + note.getTags());
                }
                adapter.setNotes(notes);
            }
        });

        addNoteFab = view.findViewById(R.id.add_note_fab);
        addNoteFab.setOnClickListener(v -> {

            checkAndStartNoteEditorActivity();
        });

        adapter.setOnItemClickListener(new NoteAdapter.OnItemClickListener() {              //se premi la nota ti porta in edit
            @Override
            public void onItemClick(Note note) {
                Intent intent = new Intent(getContext(), NoteEditorActivity.class);
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.getId());
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_TITLE, note.getTitle());
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_CONTENT, note.getContent());
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_TIMESTAMP, note.getTimestamp());
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_SELF_DESTRUCT_TIMESTAMP, note.getSelfDestructTimestamp());
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_TAGS, note.getTags());
                startActivity(intent);
            }
        });

        adapter.setOnDeleteClickListener(new NoteAdapter.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(Note note) {
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Delete Note")
                        .setMessage("Are you sure you want to permanently delete this note?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            noteViewModel.delete(note);
                            Toast.makeText(getContext(), "Note delete: " + note.getTitle(), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        searchNotesEditText = view.findViewById(R.id.search_notes_edit_text);
        searchNotesEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                if (currentSearchLiveData != null) {
                    currentSearchLiveData.removeObservers(getViewLifecycleOwner());
                }

                if (query.isEmpty()) {
                    currentSearchLiveData = noteViewModel.getAllNotes();
                } else {
                    currentSearchLiveData = noteViewModel.searchNotes(query);
                }

                currentSearchLiveData.observe(getViewLifecycleOwner(), new Observer<List<Note>>() {
                    @Override
                    public void onChanged(List<Note> notes) {
                        Log.d(TAG, "Note di ricerca caricate. Numero di note: " + notes.size());
                        adapter.setNotes(notes);
                    }
                });
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        currentSearchLiveData = noteViewModel.getAllNotes();
        currentSearchLiveData.observe(getViewLifecycleOwner(), new Observer<List<Note>>() {
            @Override
            public void onChanged(List<Note> notes) {
                Log.d(TAG, "Note iniziali caricate. Numero di note: " + notes.size());
                adapter.setNotes(notes);
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentSearchLiveData != null) {
            currentSearchLiveData.removeObservers(getViewLifecycleOwner());
        }
    }

    /**
     * Checks if SCHEDULE_EXACT_ALARM permission is granted and launches NoteEditorActivity.
     * Requests permission if not granted on Android 12+.
     */
    private void checkAndStartNoteEditorActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API 31) and above
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Permesso SCHEDULE_EXACT_ALARM non concesso. Richiedo all'utente prima di avviare l'editor.");
                Toast.makeText(requireContext(), "Please enable 'Alarms & reminders' permission for SecureNotes to auto-delete notes precisely.", Toast.LENGTH_LONG).show();
                Intent permissionIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                requestScheduleExactAlarmPermissionLauncher.launch(permissionIntent);
                return; // Do not launch editor now, wait for permission result
            }
        }
        // If permission is already granted or not needed (pre-Android 12), launch editor directly
        startNoteEditorActivity();
    }

    private void startNoteEditorActivity() {
        Intent intent = new Intent(getContext(), NoteEditorActivity.class);
        startActivity(intent);
    }

    private void performExpiredNotesCleanup() {
        Log.d(TAG, "Avvio pulizia note scadute all'avvio del NotesFragment.");
        // Chiama il ViewModel per avviare l'operazione di pulizia.
        // Il ViewModel delegherà al Repository, che eseguirà l'operazione sul database in un thread separato.
        noteViewModel.cleanupExpiredNotes(System.currentTimeMillis());
    }
}