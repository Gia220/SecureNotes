package com.example.securenotes.ui.dashboard;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.securenotes.R;
import com.example.securenotes.ui.filearchive.FileArchiveFragment;
import com.example.securenotes.ui.settings.SettingsFragment;

public class DashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this::onNavigationItemSelected);     //mposta un listener che viene attivato ogni volta che l'utente seleziona un item nella barra inferiore

        // Carica il Fragment delle Note come schermata iniziale all'avvio
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new NotesFragment())
                    .commit();
        }
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        int itemId = item.getItemId();

        if (itemId == R.id.navigation_notes) {
            selectedFragment = new NotesFragment();
            setTitle("Note"); // Imposta il titolo dell'ActionBar
        } else if (itemId == R.id.navigation_files) {
            selectedFragment = new FileArchiveFragment();
            setTitle("Files"); // Imposta il titolo dell'ActionBar
        } else if (itemId == R.id.navigation_settings) {
            selectedFragment = new SettingsFragment();
            setTitle("Settings"); // Imposta il titolo dell'ActionBar
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
            return true;
        }
        return false;
    }
}