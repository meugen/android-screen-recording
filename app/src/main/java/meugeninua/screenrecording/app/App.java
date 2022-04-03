package meugeninua.screenrecording.app;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ContextSingleton.attach(this);
    }
}
