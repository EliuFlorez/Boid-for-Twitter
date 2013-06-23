package com.teamboid.twitter.utilities;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import uk.co.senab.bitmapcache.BitmapLruCache;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Application class. Handles low-memory
 * <p/>
 * Uses parts of
 * https://github.com/cyrilmottier/GreenDroid/blob/master/GreenDroid/src/greendroid/app/GDApplication.java
 *
 * @author kennydude
 */
public class BoidApplication extends Application {

    public BoidApplication() {
        mLowMemoryListeners = new ArrayList<WeakReference<OnLowMemoryListener>>();
    }

    private BitmapLruCache mCache;

    @Override
    public void onCreate() {
        super.onCreate();

        File cacheLocation;

        // If we have external storage use it for the disk cache. Otherwise we use
        // the cache dir
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            cacheLocation = getExternalCacheDir();
        } else {
            cacheLocation = getCacheDir();
        }
        cacheLocation.mkdirs();

        BitmapLruCache.Builder builder = new BitmapLruCache.Builder(this);
        builder.setMemoryCacheEnabled(true).setMemoryCacheMaxSizeUsingHeapSize();
        builder.setDiskCacheEnabled(true).setDiskCacheLocation(cacheLocation);

        mCache = builder.build();
    }

    public BitmapLruCache getBitmapCache() {
        return mCache;
    }

    public static BoidApplication get(Context context) {
        return (BoidApplication) context.getApplicationContext();
    }

    /**
     * Used for receiving low memory system notification. You should definitely
     * use it in order to clear caches and not important data every time the
     * system needs memory.
     *
     * @author Cyril Mottier
     */
    public static interface OnLowMemoryListener {

        /**
         * Callback to be invoked when the system needs memory.
         */
        public void onLowMemoryReceived();
    }

    private ArrayList<WeakReference<OnLowMemoryListener>> mLowMemoryListeners;

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d("boid", "low memory");
        int i = 0;
        while (i < mLowMemoryListeners.size()) {
            final OnLowMemoryListener listener = mLowMemoryListeners.get(i).get();
            if (listener == null) {
                mLowMemoryListeners.remove(i);
            } else {
                listener.onLowMemoryReceived();
                i++;
            }
        }
    }


}
