package com.example.securenotes.ui.noteeditor;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.securenotes.R;
import com.example.securenotes.data.model.Note;
import com.example.securenotes.data.receiver.NoteSelfDestructReceiver;
import com.example.securenotes.data.repository.NoteRepository;
import com.example.securenotes.ui.dashboard.viewmodel.NoteViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class NoteEditorActivity extends AppCompatActivity {

    private static final String TAG = "NoteEditorActivity";

    public static final String EXTRA_NOTE_ID = "com.example.securenotes.EXTRA_NOTE_ID";
    public static final String EXTRA_NOTE_TITLE = "com.example.securenotes.EXTRA_NOTE_TITLE";
    public static final String EXTRA_NOTE_CONTENT = "com.example.securenotes.EXTRA_NOTE_CONTENT";
    public static final String EXTRA_NOTE_TIMESTAMP = "com.example.securenotes.EXTRA_NOTE_TIMESTAMP";
    public static final String EXTRA_NOTE_SELF_DESTRUCT_TIMESTAMP = "com.example.securenotes.EXTRA_NOTE_SELF_DESTRUCT_TIMESTAMP";
    public static final String EXTRA_NOTE_TAGS = "com.example.securenotes.EXTRA_NOTE_TAGS";

    private EditText editTextTitle;
    private EditText editTextContent;
    private Button buttonSaveNote;

    private EditText editTextTags;

    private TextView selfDestructDateTimeText;
    private Button setAutodestructButton;
    private Button clearAutodestructButton;
    private Calendar selfDestructCalendar;

    private boolean isSelfDestructSetByUser = false;

    private NoteViewModel noteViewModel;
    private int noteId = -1;
    private long originalTimestamp;
    private long originalSelfDestructTimestamp;

    private static final long MIN_AUTODESTRUCT_MILLIS = TimeUnit.MINUTES.toMillis(1);

    // Variabili per memorizzare lo stato dell'allarme in attesa del permesso
    private int pendingNoteId = -1;
    private long pendingSelfDestructTimestamp = 0;

    // Launcher per richiedere il permesso SCHEDULE_EXACT_ALARM
    private ActivityResultLauncher<Intent> requestScheduleExactAlarmPermissionLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);


        requestScheduleExactAlarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                            Log.d(TAG, "Permesso SCHEDULE_EXACT_ALARM concesso dopo la richiesta.");
                            // Se il permesso è stato concesso e c'è un allarme in sospeso, impostalo
                            if (pendingNoteId != -1 && pendingSelfDestructTimestamp > 0) {
                                setSelfDestructAlarmInternal(pendingNoteId, pendingSelfDestructTimestamp);
                                // Resetta le variabili in sospeso
                                pendingNoteId = -1;
                                pendingSelfDestructTimestamp = 0;
                            }
                        } else {
                            Log.w(TAG, "Permesso SCHEDULE_EXACT_ALARM negato dopo la richiesta.");
                            Toast.makeText(this, "Permission to set exact alarms was denied. Auto-delete notes may not work precisely.", Toast.LENGTH_LONG).show();

                            pendingNoteId = -1;
                            pendingSelfDestructTimestamp = 0;
                        }
                    }
                }
        );

        editTextTitle = findViewById(R.id.edit_text_title);
        editTextContent = findViewById(R.id.edit_text_content);
        buttonSaveNote = findViewById(R.id.button_save_note);

        editTextTags = findViewById(R.id.edit_text_tags);


        selfDestructDateTimeText = findViewById(R.id.self_destruct_date_time_text);
        setAutodestructButton = findViewById(R.id.set_autodestruct_button);
        clearAutodestructButton = findViewById(R.id.clear_autodestruct_button);


        selfDestructCalendar = Calendar.getInstance();

        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_NOTE_ID)) {                   //se è stato passsato l'id di una nota
            setTitle("Modify note");
            noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1);
            editTextTitle.setText(intent.getStringExtra(EXTRA_NOTE_TITLE));
            editTextContent.setText(intent.getStringExtra(EXTRA_NOTE_CONTENT));
            originalTimestamp = intent.getLongExtra(EXTRA_NOTE_TIMESTAMP, System.currentTimeMillis());

            editTextTags.setText(intent.getStringExtra(EXTRA_NOTE_TAGS));

            long existingSelfDestructTimestamp = intent.getLongExtra(EXTRA_NOTE_SELF_DESTRUCT_TIMESTAMP, 0);
            originalSelfDestructTimestamp = existingSelfDestructTimestamp;
            if (existingSelfDestructTimestamp > 0) {
                selfDestructCalendar.setTimeInMillis(existingSelfDestructTimestamp);
                updateSelfDestructDisplay();
                isSelfDestructSetByUser = true;
            }

        } else {
            setTitle("New note");
            originalTimestamp = System.currentTimeMillis();
            originalSelfDestructTimestamp = 0;
        }

        buttonSaveNote.setOnClickListener(view -> saveNote());


        if (setAutodestructButton != null) {
            setAutodestructButton.setOnClickListener(v -> showDateTimePicker());
        } else {
            Log.e(TAG, "setAutodestructButton è NULL! Controlla activity_note_editor.xml");
        }

        if (clearAutodestructButton != null) {
            clearAutodestructButton.setOnClickListener(v -> clearAutodestruct());
        } else {
            Log.e(TAG, "clearAutodestructButton è NULL! Controlla activity_note_editor.xml");
        }

    }

    private void saveNote() {
        String title = editTextTitle.getText().toString().trim();
        String content = editTextContent.getText().toString().trim();
        String tags = editTextTags.getText().toString().trim();

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "The note cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) {
            title = "Empty Note";
        }

        long timestamp = System.currentTimeMillis();
        long selfDestructTimestamp = 0;

        if (isSelfDestructSetByUser) {
            selfDestructTimestamp = selfDestructCalendar.getTimeInMillis();

            if (selfDestructTimestamp > 0 && selfDestructTimestamp < System.currentTimeMillis()) {
                selfDestructTimestamp = 0;
                Toast.makeText(this, "The self-destruct date cannot be in the past. Not set..", Toast.LENGTH_SHORT).show();
            } else if (selfDestructTimestamp > 0 && selfDestructTimestamp - System.currentTimeMillis() < MIN_AUTODESTRUCT_MILLIS) {
                selfDestructTimestamp = System.currentTimeMillis() + MIN_AUTODESTRUCT_MILLIS;
                Toast.makeText(this, "The self-destruction time has been adjusted to a minimum of 1 minute (for precise execution).", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Tempo autodistruzione adattato a minimo 1 minuto.");
            }
        } else {
            Log.d(TAG, "Autodistruzione non impostata dall'utente. selfDestructTimestamp rimane 0.");
            selfDestructTimestamp = 0;
        }

        if (noteId == -1) {             //non ho id (crazione nuova nota)

            Note newNote = new Note(title, content, timestamp, selfDestructTimestamp, tags);
            long finalSelfDestructTimestamp = selfDestructTimestamp;
            noteViewModel.insert(newNote, new NoteRepository.OnNoteInsertedCallback() {
                @Override
                public void onNoteInserted(int newNoteId) {                                         //IMPLEMENTAZIONE del metodo di callback
                    runOnUiThread(() -> {
                        if (finalSelfDestructTimestamp > 0) {
                            Log.d(TAG, "SaveNote Callback: ID nota generato: " + newNoteId + ". Tentativo di impostare allarme.");
                            handleSelfDestructAlarm(newNoteId, finalSelfDestructTimestamp);
                        } else {
                            Log.d(TAG, "SaveNote Callback: ID nota generato: " + newNoteId + ". Nessun allarme da impostare.");
                        }
                        Toast.makeText(NoteEditorActivity.this, "Note saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        } else {
            // Modifica nota esistente
            if (originalSelfDestructTimestamp > 0) {
                Log.d(TAG, "SaveNote: Allarme originale esistente per ID " + noteId + ". Tentativo di cancellazione.");
                cancelSelfDestructAlarm(noteId);
            }

            Note existingNote = new Note(title, content, originalTimestamp, selfDestructTimestamp, tags);
            existingNote.setId(noteId);
            noteViewModel.update(existingNote);

            if (selfDestructTimestamp > 0) {
                Log.d(TAG, "SaveNote: Allarme da impostare/riprogrammare per nota ID " + noteId);
                // Chiamiamo il wrapper che gestisce il permesso
                handleSelfDestructAlarm(noteId, selfDestructTimestamp);
            } else {
                Log.d(TAG, "SaveNote: Allarme rimosso per nota ID " + noteId + " (selfDestructTimestamp è 0).");
            }
            Toast.makeText(this, "Updated note!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showDateTimePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selfDestructCalendar.set(Calendar.YEAR, year);
                    selfDestructCalendar.set(Calendar.MONTH, month);
                    selfDestructCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    showTimePicker();
                },
                selfDestructCalendar.get(Calendar.YEAR),
                selfDestructCalendar.get(Calendar.MONTH),
                selfDestructCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selfDestructCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selfDestructCalendar.set(Calendar.MINUTE, minute);
                    selfDestructCalendar.set(Calendar.SECOND, 0);
                    selfDestructCalendar.set(Calendar.MILLISECOND, 0);

                    long selectedTimeMillis = selfDestructCalendar.getTimeInMillis();
                    long currentTimeMillis = System.currentTimeMillis();

                    if (selectedTimeMillis < currentTimeMillis) {
                        Toast.makeText(this, "The self-destruction time cannot be in the past.", Toast.LENGTH_SHORT).show();
                        selfDestructCalendar.setTimeInMillis(0);
                        isSelfDestructSetByUser = false;
                    } else if (selectedTimeMillis - currentTimeMillis < MIN_AUTODESTRUCT_MILLIS) {
                        selfDestructCalendar.setTimeInMillis(currentTimeMillis + MIN_AUTODESTRUCT_MILLIS);
                        Toast.makeText(this, "The self-destruction time has been adjusted to a minimum of 1 minute (for precise execution).", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "Tempo autodistruzione adattato a minimo 1 minuto.");
                    }
                    updateSelfDestructDisplay();
                    isSelfDestructSetByUser = true;
                },
                selfDestructCalendar.get(Calendar.HOUR_OF_DAY),
                selfDestructCalendar.get(Calendar.MINUTE),
                true
        );
        timePickerDialog.show();
    }

    private void updateSelfDestructDisplay() {
        if (selfDestructCalendar.getTimeInMillis() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            selfDestructDateTimeText.setText(sdf.format(selfDestructCalendar.getTime()));
            clearAutodestructButton.setVisibility(View.VISIBLE);
        } else {
            selfDestructDateTimeText.setText("Not set");
            clearAutodestructButton.setVisibility(View.GONE);
        }
    }

    private void clearAutodestruct() {
        if (noteId != -1) {
            Log.d(TAG, "clearAutodestruct: Richiesta di rimozione autodistruzione per nota ID " + noteId + ". Tentativo di cancellare allarme.");
            cancelSelfDestructAlarm(noteId);
        }
        selfDestructCalendar.setTimeInMillis(0);
        updateSelfDestructDisplay();
        Toast.makeText(this, "Self-destruct removed.", Toast.LENGTH_SHORT).show();
        isSelfDestructSetByUser = false;
    }


    private void handleSelfDestructAlarm(int noteId, long selfDestructTimestamp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "handleSelfDestructAlarm: Permesso SCHEDULE_EXACT_ALARM non concesso. Richiedo all'utente.");
                Toast.makeText(this, "Please enable 'Alarms & reminders' permission for SecureNotes to auto-delete notes precisely.", Toast.LENGTH_LONG).show();

                // Salva l'ID e il timestamp per reimpostare l'allarme dopo che il permesso è stato concesso
                pendingNoteId = noteId;
                pendingSelfDestructTimestamp = selfDestructTimestamp;

                Intent permissionIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                requestScheduleExactAlarmPermissionLauncher.launch(permissionIntent);
                return;
            }
        }
        // Se il permesso è già concesso o non è necessario (pre-Android 12), imposta l'allarme direttamente
        setSelfDestructAlarmInternal(noteId, selfDestructTimestamp);
    }



    private void setSelfDestructAlarmInternal(int noteId, long selfDestructTimestamp) {
        if (noteId == -1 || selfDestructTimestamp <= System.currentTimeMillis()) {
            Log.w(TAG, "setSelfDestructAlarmInternal: Impossibile impostare allarme: ID nota non valido o timestamp nel passato. ID: " + noteId + ", Timestamp: " + selfDestructTimestamp + " (Current Time: " + System.currentTimeMillis() + ")");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, NoteSelfDestructReceiver.class);
        intent.setAction(NoteSelfDestructReceiver.ACTION_SELF_DESTRUCT_NOTE);
        intent.putExtra(NoteSelfDestructReceiver.EXTRA_NOTE_ID, noteId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, noteId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, selfDestructTimestamp, pendingIntent);
            Log.i(TAG, "Allarme autodistruzione IMPOSTATO per nota ID " + noteId + " a " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(selfDestructTimestamp));
        } else {
            Log.e(TAG, "setSelfDestructAlarmInternal: AlarmManager è nullo. Impossibile impostare l'allarme.");
        }
    }

    private void cancelSelfDestructAlarm(int noteId) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, NoteSelfDestructReceiver.class);
        intent.setAction(NoteSelfDestructReceiver.ACTION_SELF_DESTRUCT_NOTE);
        intent.putExtra(NoteSelfDestructReceiver.EXTRA_NOTE_ID, noteId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, noteId, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.i(TAG, "Allarme autodistruzione CANCELLATO per nota ID: " + noteId);
        } else {
            Log.d(TAG, "Nessun allarme da cancellare per nota ID: " + noteId + ". PendingIntent non trovato o AlarmManager nullo.");
        }
    }
}