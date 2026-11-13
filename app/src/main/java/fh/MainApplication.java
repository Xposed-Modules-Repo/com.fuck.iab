package fh;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainApplication extends Application {

    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    private static volatile XposedService service;
    private static final CopyOnWriteArrayList<ServiceStateListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);

        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService bound) {
                service = bound;
                for (ServiceStateListener l : listeners) l.onServiceStateChanged(bound);
            }

            @Override
            public void onServiceDied(XposedService dead) {
                service = null;
                for (ServiceStateListener l : listeners) l.onServiceStateChanged(null);
            }
        });
    }

    public static XposedService getService() {
        return service;
    }

    /** @param sticky if true and a service is already bound, fires immediately with it. */
    public static void addServiceStateListener(ServiceStateListener listener, boolean sticky) {
        listeners.add(listener);
        if (sticky && service != null) listener.onServiceStateChanged(service);
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        listeners.remove(listener);
    }
}
