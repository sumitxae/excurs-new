package choruscoldchain.app;

import android.app.Application;
import android.util.Log;
// Toast
import android.widget.Toast;

import com.kongzue.dialogx.DialogX;

public class App extends Application {
    private static final String TAG = "App";
    private InstallationIdManager installationIdManager;

    @Override
    public void onCreate() {
        super.onCreate();
        DialogX.init(this);
        
        // Initialize installation ID manager
        installationIdManager = InstallationIdManager.getInstance(this);
        
        // Log installation information

        Log.i(TAG, "App started with " + installationIdManager.getInstallationInfo());
        Toast.makeText(this,"App ID: " + installationIdManager.getInstallationId(), Toast.LENGTH_LONG).show();
    }
    
    /**
     * Gets the installation ID manager instance.
     * @return InstallationIdManager instance
     */
    public InstallationIdManager getInstallationIdManager() {
        return installationIdManager;
    }
}
