package foundation.e.passwords.companion;

import android.os.Bundle;
import foundation.e.passwords.companion.ICompanionCallback;

interface ICompanionCredentialService {
    int getVersion();
    void get(in Bundle request, ICompanionCallback callback);
    void save(in Bundle request, ICompanionCallback callback);
    void delete(in Bundle request, ICompanionCallback callback);
}
