# Preserve the published Binder contract across app-side R8 obfuscation.
# The interface descriptor and parcelable type names must stay stable for IPC.
-keep interface foundation.e.backupappapi.BackupAppApi { *; }
-keep class foundation.e.backupappapi.BackupAppApi$* { *; }
-keep class foundation.e.backupappapi.BackupKey { *; }
