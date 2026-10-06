package foundation.e.backupappapi;

import foundation.e.backupappapi.BackupKey;

oneway interface IBackupKeyCallback {

    void onKeyReady(in BackupKey key);

}
