package choruscoldchain.app;

import android.app.Application;

import com.kongzue.dialogx.DialogX;

public class App extends Application {


    @Override
    public void onCreate() {
        super.onCreate();
        DialogX.init(this);
    }
}
