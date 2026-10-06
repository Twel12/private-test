# Preserve the published Binder contract across app-side R8 obfuscation.
# AIDL depends on stable type names for the interface descriptor, generated
# Stub/Proxy classes, callbacks, and parcelables exchanged over Binder.
-keep class foundation.e.backupappapi.** { *; }
-keep interface foundation.e.backupappapi.** { *; }
