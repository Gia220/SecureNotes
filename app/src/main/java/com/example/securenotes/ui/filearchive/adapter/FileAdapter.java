package com.example.securenotes.ui.filearchive.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // <<< NUOVO: Importa Button
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securenotes.R;
import com.example.securenotes.data.model.ArchivedFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileHolder> {

    private List<ArchivedFile> files = new ArrayList<>();
    private OnItemClickListener itemClickListener;
    private OnDeleteClickListener deleteClickListener;

    @NonNull
    @Override
    public FileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {             //Viene chiamato quando la RecyclerView ha bisogno di creare una nuova View( mostrare un nuovo elemento)
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_item, parent, false);
        return new FileHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull FileHolder holder, int position) {                    //Viene chiamato dalla RecyclerView ogni volta che deve visualizzare o aggiornare i dati di un elemento in una specifica posizione della lista
        ArchivedFile currentFile = files.get(position);
        holder.textViewFileName.setText(currentFile.getOriginalName());
        holder.textViewMimeType.setText("Type: " + currentFile.getMimeType());

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault());
        holder.textViewTimestamp.setText("Date: " + sdf.format(new java.util.Date(currentFile.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public void setFiles(List<ArchivedFile> files) {
        this.files = files;
        notifyDataSetChanged();                 //notifica adapter che ridisegna
    }

    public ArchivedFile getFileAt(int position) {
        return files.get(position);
    }

    class FileHolder extends RecyclerView.ViewHolder {          //elementi (file_item)
        private TextView textViewFileName;
        private TextView textViewMimeType;
        private TextView textViewTimestamp;
        private Button deleteButton;

        public FileHolder(@NonNull View itemView) {
            super(itemView);
            textViewFileName = itemView.findViewById(R.id.file_name);
            textViewMimeType = itemView.findViewById(R.id.file_mime_type);
            textViewTimestamp = itemView.findViewById(R.id.file_timestamp);
            deleteButton = itemView.findViewById(R.id.file_delete_button);

            // Listener per il click sull'intero elemento (per la visualizzazione)
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (itemClickListener != null && position != RecyclerView.NO_POSITION) {
                        itemClickListener.onItemClick(files.get(position));
                    }
                }
            });

            // Listener per il click sul pulsante elimina
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (deleteClickListener != null && position != RecyclerView.NO_POSITION) {
                        deleteClickListener.onDeleteClick(files.get(position));
                    }
                }
            });

        }
    }

    public interface OnItemClickListener {
        void onItemClick(ArchivedFile file);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    // Interfaccia per il listener del click sul pulsante elimina
    public interface OnDeleteClickListener {
        void onDeleteClick(ArchivedFile file);
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteClickListener = listener;
    }

}