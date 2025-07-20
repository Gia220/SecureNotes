package com.example.securenotes.ui.dashboard.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securenotes.R;
import com.example.securenotes.data.model.Note;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteHolder> {         // Ogni NoteHolder contiene dei riferimenti diretti a tutte le View che compongono un singolo elemento della lista

    private List<Note> notes = new ArrayList<>();
    private OnItemClickListener itemClickListener;
    private OnDeleteClickListener deleteClickListener;

    @NonNull
    @Override
    public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {         //Viene chiamato quando la RecyclerView ha bisogno di creare una nuova View( mostrare un nuovo elemento)
        View itemView = LayoutInflater.from(parent.getContext())                            // Ottiene un LayoutInflater dal contesto del parent
                .inflate(R.layout.note_item, parent, false);                    //crea il layout, ma false non la fa visualizzare
        return new NoteHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteHolder holder, int position) {                //Viene chiamato dalla RecyclerView ogni volta che deve visualizzare o aggiornare i dati di un elemento in una specifica posizione della lista
        Note currentNote = notes.get(position);
        holder.textViewTitle.setText(currentNote.getTitle());
        holder.textViewContentPreview.setText(currentNote.getContent());

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault());
        holder.textViewTimestamp.setText(sdf.format(new java.util.Date(currentNote.getTimestamp())));

        //Logica per visualizzare icona e data di autodistruzione
        if (currentNote.getSelfDestructTimestamp() > 0) {
            holder.autodestructIcon.setVisibility(View.VISIBLE);
            holder.autodestructDateTimeDisplay.setVisibility(View.VISIBLE);

            String autodestructDate = sdf.format(new java.util.Date(currentNote.getSelfDestructTimestamp()));
            holder.autodestructDateTimeDisplay.setText("self-destruction: " + autodestructDate);
        } else {
            holder.autodestructIcon.setVisibility(View.GONE);
            holder.autodestructDateTimeDisplay.setVisibility(View.GONE);
        }

    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
        notifyDataSetChanged();             //dice alla RecyclerView che i dati sono cambiati e che deve ridisegnare la lista
    }

    public Note getNoteAt(int position) {
        return notes.get(position);
    }

    class NoteHolder extends RecyclerView.ViewHolder {
        private TextView textViewTitle;
        private TextView textViewContentPreview;
        private TextView textViewTimestamp;
        private Button deleteButton;
        private ImageView autodestructIcon;
        private TextView autodestructDateTimeDisplay;

        public NoteHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.note_title);
            textViewContentPreview = itemView.findViewById(R.id.note_content_preview);
            textViewTimestamp = itemView.findViewById(R.id.note_timestamp);
            deleteButton = itemView.findViewById(R.id.note_delete_button);
            autodestructIcon = itemView.findViewById(R.id.autodestruct_icon);
            autodestructDateTimeDisplay = itemView.findViewById(R.id.autodestruct_date_time_display);

            itemView.setOnClickListener(new View.OnClickListener() {                    //prendo la posizone
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (itemClickListener != null && position != RecyclerView.NO_POSITION) {
                        itemClickListener.onItemClick(notes.get(position));
                    }
                }
            });

            deleteButton.setOnClickListener(new View.OnClickListener() {                //delete
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (deleteClickListener != null && position != RecyclerView.NO_POSITION) {
                        deleteClickListener.onDeleteClick(notes.get(position));
                    }
                }
            });
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Note note);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Note note);
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteClickListener = listener;
    }
}