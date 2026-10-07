package foundation.e.backupappapi;

import foundation.e.backupappapi.ICredentialCallback;

interface BackupAppApi {

    void get(String key, ICredentialCallback callback);

    void save(String key, String username, String secret, int flags, ICredentialCallback callback);

}
