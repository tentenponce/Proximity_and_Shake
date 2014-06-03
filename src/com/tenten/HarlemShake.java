package com.tenten;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.View;
import android.widget.Toast;

import java.io.RandomAccessFile;
import java.util.List;

public class HarlemShake extends View implements SensorEventListener {
    ActivityManager am;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    public Context cContext;
    private static final float SHAKE_THRESHOLD_GRAVITY = 2.7F;
    private static final int SHAKE_SLOP_TIME_MS = 500;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;

    ShakeDetector.OnShakeListener mListener;
    private long mShakeTimestamp;
    private int mShakeCount;
    double totalMemory;
    double availableMemory;
    public Handler mHandler;

    public HarlemShake(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        cContext = context;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        totalMemory = 0;
        availableMemory = 0;

        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {

            @Override
            public void onShake(int count) {
                if(Settings.System.getInt(cContext.getContentResolver(), "advance", 0) == 1)
                {
                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    cContext.startActivity(startMain);
                }
                ActivityManager am = (ActivityManager) cContext.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> runningapptasks = am.getRunningAppProcesses();
                List<ActivityManager.RecentTaskInfo> recent_tasks = am.getRecentTasks(Integer.MAX_VALUE, ActivityManager.RECENT_WITH_EXCLUDED);
                List<ActivityManager.RunningTaskInfo> runningtaskss =am.getRunningTasks(Integer.MAX_VALUE);
                for (ActivityManager.RunningTaskInfo runningtasks : runningtaskss) {
                    String packageName = runningtasks.baseActivity.getPackageName();
                    am.killBackgroundProcesses(packageName);
                    am.restartPackage(packageName);
                }

                for (ActivityManager.RunningAppProcessInfo runningapptask : runningapptasks) {
                    am.restartPackage(runningapptask.processName);
                }
                am.restartPackage("com.android.music");
                for (ActivityManager.RecentTaskInfo recent_task : recent_tasks) {
                    String LocalApp = recent_task.baseIntent + "";
                    int indexPackageNameBegin = LocalApp.indexOf("cmp=") + 4;
                    int indexPackageNameEnd = LocalApp.indexOf("/", indexPackageNameBegin);
                    String PackageName = LocalApp.substring(indexPackageNameBegin, indexPackageNameEnd);
                    am.killBackgroundProcesses(PackageName);
                    am.restartPackage(PackageName);
                }

                MemoryInfo();
            }
        });
    }

    private void MemoryInfo() {
        if(totalMemory == 0) {
            try {
                RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r");
                String load = reader.readLine();
                String[] memInfo = load.split(" ");
                totalMemory = Double.parseDouble(memInfo[9])/1024;

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        availableMemory = mi.availMem / 1048576L;
        int usedmemory = (int) (totalMemory - availableMemory);
        int totmem = (int) totalMemory;
        Toast.makeText(cContext, "RAM Cleared! " +usedmemory +" MB/" +totmem +" MB", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (mListener != null) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float gX = x / SensorManager.GRAVITY_EARTH;
            float gY = y / SensorManager.GRAVITY_EARTH;
            float gZ = z / SensorManager.GRAVITY_EARTH;

            // gForce will be close to 1 when there is no movement.
            float gForce = FloatMath.sqrt(gX * gX + gY * gY + gZ * gZ);

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                final long now = System.currentTimeMillis();

                // ignore shake events too close to each other (500ms)
                if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return;
                }

                // reset the shake count after 3 seconds of no shakes
                if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    mShakeCount = 0;
                }

                mShakeTimestamp = now;
                mShakeCount++;

                mListener.onShake(mShakeCount);
            }
        }
    }
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    "advance"), false, this);
        }

        public void onChange(boolean selfChange) {
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSensorManager.unregisterListener(mShakeDetector);
    }

    static class ShakeDetector implements SensorEventListener {

        /*
         * The gForce that is necessary to register as shake.
         * Must be greater than 1G (one earth gravity unit).
         * You can install "G-Force", by Blake La Pierre
         * from the Google Play Store and run it to see how
         *  many G's it takes to register a shake
         */
        private static final float SHAKE_THRESHOLD_GRAVITY = 2.7F;
        private static final int SHAKE_SLOP_TIME_MS = 500;
        private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;

        private OnShakeListener mListener;
        private long mShakeTimestamp;
        private int mShakeCount;

        public void setOnShakeListener(OnShakeListener listener) {
            this.mListener = listener;
        }

        public interface OnShakeListener {
            public void onShake(int count);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // ignore
        }

        @Override
        public void onSensorChanged(SensorEvent event) {

            if (mListener != null) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float gX = x / SensorManager.GRAVITY_EARTH;
                float gY = y / SensorManager.GRAVITY_EARTH;
                float gZ = z / SensorManager.GRAVITY_EARTH;

                // gForce will be close to 1 when there is no movement.
                float gForce = FloatMath.sqrt(gX * gX + gY * gY + gZ * gZ);

                if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                    final long now = System.currentTimeMillis();
                    // ignore shake events too close to each other (500ms)
                    if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                        return;
                    }

                    // reset the shake count after 3 seconds of no shakes
                    if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                        mShakeCount = 0;
                    }

                    mShakeTimestamp = now;
                    mShakeCount++;

                    mListener.onShake(mShakeCount);
                }
            }
        }
    }
}
