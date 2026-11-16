# No special rules needed yet.

# Storage.directoryOf reads StorageVolume.getPath() reflectively on API < 30.
-keep class android.os.storage.StorageVolume { *; }
