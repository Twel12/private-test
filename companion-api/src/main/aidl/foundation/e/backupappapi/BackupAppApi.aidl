package foundation.e.backupappapi;

import foundation.e.backupappapi.BackupKey;
import foundation.e.backupappapi.IBackupKeyCallback;

interface BackupAppApi {

    void getKeyForBackup(IBackupKeyCallback callback);

}
