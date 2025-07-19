# Regole ProGuard per il progetto SecureNotes

# Mantieni classi e membri di AndroidX in generale
-keep class androidx.** { *; }

# Mantieni classi usate dalla Biometric API
-keep class androidx.biometric.** { *; }

# Mantieni le entità Room e i loro getter/setter e i DAO
# Questo è necessario perché Room genera codice a runtime basato sullo schema.
-keep class com.example.securenotes.data.model.** { *; }
-keep class com.example.securenotes.data.dao.** { *; }
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Delete <methods>;
    @androidx.room.Update <methods>;
}

# Mantieni le classi per le preferenze criptate/file (anche se usiamo PBKDF2, queste regole sono per la libreria)
-keep class androidx.security.crypto.** { *; }

# Mantieni i nomi delle classi Application e entry point
-keep class com.example.securenotes.SecureNotesApplication { *; }
-keep class com.example.securenotes.ui.login.LoginActivity { *; }
-keep class com.example.securenotes.ui.dashboard.DashboardActivity { *; }
-keep class com.example.securenotes.ui.noteeditor.NoteEditorActivity { *; }
-keep class com.example.securenotes.ui.filearchive.FileArchiveActivity { *; }
-keep class com.example.securenotes.ui.settings.SettingsActivity { *; }
-keep class com.example.securenotes.ui.pinmanagement.ChangePinActivity { *; }


# Regole generali per evitare problemi con la reflection (mantieni i nomi dei membri annotati)
# Esempio per Gson se lo usassi (non è nel tuo codice attuale, ma utile come esempio)
# -keepnames class * { @com.google.gson.annotations.SerializedName <fields>; } [cite: 99]

# Regole per non offuscare classi che potrebbero essere usate dinamicamente
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static final android.os.Parcelable$Creator CREATOR;
}
-keepnames class * extends android.app.Service
-keepnames class * extends android.content.BroadcastReceiver
-keepnames class * extends android.content.ContentProvider
-keepnames class * extends android.app.Activity
-keepnames class * extends android.app.Fragment
-keepnames class * extends androidx.fragment.app.Fragment

# Offusca tutto il resto
-dontwarn java.lang.Object
-dontwarn sun.misc.Unsafe
-dontwarn org.bouncycastle.**
-dontwarn javax.crypto.**
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy