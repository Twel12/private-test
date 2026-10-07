package foundation.e.backupappapi;

import foundation.e.backupappapi.CredentialResult;

oneway interface ICredentialCallback {

    void onResult(in CredentialResult result);

}
