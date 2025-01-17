/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.biometrics.BiometricSourceType;
import android.os.Handler;
import android.os.IHwBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.R;

import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreen;
import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreenCallback;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import static android.provider.Settings.Secure.DOZE_ALWAYS_ON;
import android.os.UserHandle;
import android.hardware.display.AmbientDisplayConfiguration;
import android.hardware.display.DisplayManager;
import android.view.Display;

public class FODCircleView extends ImageView implements OnTouchListener {
    private final int mPositionX;
    private final int mPositionY;
    private final int mWidth;
    private final int mHeight;
    private final int mDreamingMaxOffset;
    private final boolean mShouldBoostBrightness;
    private final Paint mPaintFingerprint = new Paint();
    private final Paint mPaintShow = new Paint();
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private final WindowManager mWindowManager;

    private IFingerprintInscreen mFingerprintInscreenDaemon;

    private int mDreamingOffsetX;
    private int mDreamingOffsetY;
    private int mNavigationBarSize;

    private final Display mDisplay;

    private boolean mIsBouncer;
    private boolean mIsDreaming;
    private boolean mIsInsideCircle;
    private boolean mIsPressed;
    private boolean mIsPulsing;
    private boolean mIsScreenOn;
    private boolean mIsViewAdded;

    private Context mContext;

    private Handler mHandler;

    private Timer mBurnInProtectionTimer;

    private IFingerprintInscreenCallback mFingerprintInscreenCallback =
            new IFingerprintInscreenCallback.Stub() {
        @Override
        public void onFingerDown() {
            mIsInsideCircle = true;

            mHandler.post(() -> {
                setImageResource(R.drawable.fod_icon_pressed);
                invalidate();
            });
        }

        @Override
        public void onFingerUp() {
            mIsInsideCircle = false;

            mHandler.post(() -> {
                setImageResource(R.drawable.fod_icon_default);
                invalidate();
            });
        }
    };

    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onPhoneStateChanged(int phoneState) {
			Slog.d("FODCircleView", "onPhoneStateChanged phoneState=" + phoneState);
//            if ((phoneState == TelephonyManager.EXTRA_STATE_RINGING) && mIsShowing) {
//                hide();
//            }
        }

        @Override
        public void onUserUnlocked() {
/*			Slog.d("FODCircleView", "onUserUnlocked");
            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            if (daemon != null) {
                try {
                     daemon.onHideFODView();
                } catch (RemoteException e) {
                     // do nothing
                }
            }
*/
         }

        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            super.onDreamingStateChanged(dreaming);
            mIsDreaming = dreaming;
            mIsInsideCircle = false;
            Slog.d("FODCircleView", "dreaming=" + dreaming);
            if (dreaming) {
                mBurnInProtectionTimer = new Timer();
                mBurnInProtectionTimer.schedule(new BurnInProtectionTask(), 0, 60 * 1000);
            } else if (mBurnInProtectionTimer != null) {
                mBurnInProtectionTimer.cancel();
            }
            if (!dreaming) {
                setDim(true);
            }

            if (mIsViewAdded) {
                resetPosition();
                invalidate();
            }
        }

        @Override
        public void onScreenTurnedOff() {
            super.onScreenTurnedOff();
            Slog.d("FODCircleView", "onScreenTurnedOff");
            mIsInsideCircle = false;

            int userId = ActivityManager.getCurrentUser();
            if (mUpdateMonitor.isUnlockingWithBiometricsPossible(userId)/* && !isAlwaysOnEnabled(mContext)*/) {
                setDim(true);
                IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
                if (daemon != null) {
                    try {
                        daemon.onShowFODView();
                    } catch (RemoteException e) {
                        // do nothing
                    }
                }
            }
        }

        @Override
        public void onStartedGoingToSleep(int why) {
            super.onStartedGoingToSleep(why);
            int userId = ActivityManager.getCurrentUser();
/*            if (mUpdateMonitor.isUnlockingWithBiometricsPossible(userId)) {
                setDim(true);
            }
*/
            Slog.d("FODCircleView", "onStartedGoingToSleep");
            mIsInsideCircle = false;
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            super.onFinishedGoingToSleep(why);
            Slog.d("FODCircleView", "onFinishedGoingToSleep why=" + why);
        }

        @Override
        public void onStartedWakingUp() {
            super.onStartedWakingUp();
            Slog.d("FODCircleView", "onStartedWakingUp");
        }

        @Override
        public void onScreenTurnedOn() {
            super.onScreenTurnedOn();
            Slog.d("FODCircleView", "onScreenTurnedOn");
            mIsScreenOn = true;
            mIsInsideCircle = false;
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            super.onKeyguardVisibilityChanged(showing);
            Slog.d("FODCircleView", "onKeyguardVisibilityChanged showing=" + showing);
            mIsInsideCircle = false;
        }

        @Override
        public void onKeyguardBouncerChanged(boolean isBouncer) {
            mIsBouncer = isBouncer;
            Slog.d("FODCircleView", "onKeyguardBouncerChanged isBouncer=" + isBouncer);
            if (isBouncer) {
                hide();
            } else if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
        }

        @Override
        public void onStrongAuthStateChanged(int userId) {
            super.onStrongAuthStateChanged(userId);
            Slog.d("FODCircleView", "onStrongAuthStateChanged");
        }

        @Override
        public void onBiometricAuthenticated(int userId, BiometricSourceType biometricSourceType) {
            super.onBiometricAuthenticated(userId, biometricSourceType);
            Slog.d("FODCircleView", "onBiometricAuthenticated");
            mIsInsideCircle = false;

            setDim(false);

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            if (daemon != null) {
                try {
                    daemon.onHideFODView();
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

        @Override
        public void onBiometricRunningStateChanged(boolean running,
                BiometricSourceType biometricSourceType) {
            super.onBiometricRunningStateChanged(running, biometricSourceType);
			Slog.d("FODCircleView", "onBiometricRunningStateChanged running=" + running);
            if (running) {
                show();
            } else {
                hide();
            }
        }
    };

    public FODCircleView(Context context) {
        super(context);
        mContext = context;
        Resources res = context.getResources();

        mPaintFingerprint.setAntiAlias(true);
        mPaintFingerprint.setColor(res.getColor(R.color.config_fodColor));

        setImageResource(R.drawable.fod_icon_default);

        mPaintShow.setAntiAlias(true);
        mPaintShow.setColor(res.getColor(R.color.config_fodColor));

        setOnTouchListener(this);

        mWindowManager = context.getSystemService(WindowManager.class);

        mNavigationBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);

        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        mDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        try {
            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            if (daemon == null) {
                throw new RuntimeException("Unable to get IFingerprintInscreen");
            }
            mPositionX = daemon.getPositionX();
            mPositionY = daemon.getPositionY();
            mWidth = daemon.getSize();
            mHeight = mWidth; // We do not expect mWidth != mHeight

            mShouldBoostBrightness = daemon.shouldBoostBrightness();
        } catch (NoSuchElementException | RemoteException e) {
            throw new RuntimeException(e);
        }

        if (mPositionX < 0 || mPositionY < 0 || mWidth < 0 || mHeight < 0) {
            throw new RuntimeException("Invalid FOD circle position or size.");
        }

        mDreamingMaxOffset = (int) (mWidth * 0.1f);

        mHandler = new Handler(Looper.getMainLooper());

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
        mUpdateMonitor.registerCallback(mMonitorCallback);
    }

    private boolean isAlwaysOnEnabled(Context context) {
        final boolean enabledByDefault = context.getResources()
                .getBoolean(com.android.internal.R.bool.config_dozeAlwaysOnEnabled);

        return Settings.Secure.getIntForUser(context.getContentResolver(),
                DOZE_ALWAYS_ON, alwaysOnDisplayAvailable(context) && enabledByDefault ? 1 : 0,
                UserHandle.USER_CURRENT) != 0;
    }

    private boolean alwaysOnDisplayAvailable(Context context) {
        return new AmbientDisplayConfiguration(context).alwaysOnAvailable();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mIsInsideCircle) {
            setImageResource(R.drawable.fod_icon_pressed);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // onLayout is a good time to call the HAL because dim layer
        // added by setDim() should have come into effect
        // the HAL is expected (if supported) to set the screen brightness
        // to maximum / minimum immediately when called
        if (mIsInsideCircle) {
            if (mIsDreaming) {
                setAlpha(1.0f);
            }
            if (!mIsPressed) {
                IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
                if (daemon != null) {
                    try {
                        daemon.onPress();
                    } catch (RemoteException e) {
                        // do nothing
                    }
                }
                mIsPressed = true;
            }
        } else {
            setAlpha(mIsDreaming ? 0.5f : 1.0f);
            if (mIsPressed) {
                IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
                if (daemon != null) {
                    try {
                        daemon.onRelease();
                    } catch (RemoteException e) {
                        // do nothing
                    }
                }
                mIsPressed = false;
            }

        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newInside = (x > 0 && x < mWidth) && (y > 0 && y < mWidth);

        if (event.getAction() == MotionEvent.ACTION_UP) {
            newInside = false;
            if (mDisplay.getState() == Display.STATE_DOZE) {
                IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
                if (daemon != null) {
                    try {
                        daemon.onHideFODView();
                    } catch (RemoteException e) {
                        // do nothing
                    }
                setDim(false);
                }
            }
            setImageResource(R.drawable.fod_icon_default);
        }

        if (newInside == mIsInsideCircle) {
            return mIsInsideCircle;
        }

        mIsInsideCircle = newInside;

        invalidate();

        if (!mIsInsideCircle) {
            setImageResource(R.drawable.fod_icon_default);
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mDisplay.getState() == Display.STATE_DOZE) {
                IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
                if (daemon != null) {
                    try {
                        daemon.onHideFODView();
                    } catch (RemoteException e) {
                        // do nothing
                    }
                }
                setDim1();
            }
            setImageResource(R.drawable.fod_icon_pressed);
        }

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mIsViewAdded) {
            resetPosition();
            mWindowManager.updateViewLayout(this, mParams);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon != null) {
            try {
                daemon.onHideFODView();
            } catch (RemoteException e) {
                // do nothing
            }
        }
        setDim(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setDim(true);
    }

    public synchronized IFingerprintInscreen getFingerprintInScreenDaemon() {
        if (mFingerprintInscreenDaemon == null) {
            try {
                mFingerprintInscreenDaemon = IFingerprintInscreen.getService();
                if (mFingerprintInscreenDaemon != null) {
                    mFingerprintInscreenDaemon.setCallback(mFingerprintInscreenCallback);
                    mFingerprintInscreenDaemon.asBinder().linkToDeath((cookie) -> {
                        mFingerprintInscreenDaemon = null;
                    }, 0);
                }
            } catch (NoSuchElementException | RemoteException e) {
                // do nothing
            }
        }
        return mFingerprintInscreenDaemon;
    }

    public void show() {
        if (mIsViewAdded) {
            return;
        }

        if (mIsBouncer) {
            return;
        }

        resetPosition();

        mParams.height = mWidth;
        mParams.width = mHeight;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.setTitle("Fingerprint on display");
        mParams.packageName = "android";
        mParams.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        setImageResource(R.drawable.fod_icon_default);

        mWindowManager.addView(this, mParams);
        mIsViewAdded = true;

        mIsPressed = false;
    }

    public void hide() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon != null) {
            try {
                daemon.onHideFODView();
            } catch (RemoteException e) {
                // do nothing
            }
        }

       if (!mIsViewAdded) {
           return;
       }
        mWindowManager.removeView(this);
        mIsInsideCircle = false;
        mIsViewAdded = false;
        setDim(false);
    }

    private void resetPosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                mParams.x = mPositionX;
                mParams.y = mPositionY;
                break;
            case Surface.ROTATION_90:
                mParams.x = mPositionY;
                mParams.y = mPositionX;
                break;
            case Surface.ROTATION_180:
                mParams.x = mPositionX;
                mParams.y = size.y - mPositionY - mHeight;
                break;
            case Surface.ROTATION_270:
                mParams.x = size.x - mPositionY - mWidth - mNavigationBarSize;
                mParams.y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }

        if (mIsDreaming) {
            mParams.x += mDreamingOffsetX;
            mParams.y += mDreamingOffsetY;
        }

        if (mIsViewAdded) {
            mWindowManager.updateViewLayout(this, mParams);
        }
    }

    private void setDim1() {
            int curBrightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
            int dimAmount = 0;

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            if (daemon != null) {
                try {
                    dimAmount = daemon.getDimAmount(curBrightness);
                } catch (RemoteException e) {
                    // do nothing
                }
            }

            mParams.dimAmount = ((float) dimAmount) / 255.0f;

        try {
            mWindowManager.updateViewLayout(this, mParams);
        } catch (IllegalArgumentException e) {
            // do nothing
        }
    }

    private void setDim(boolean dim) {
        Slog.d("FODCircleView","AOD=" + isAlwaysOnEnabled(mContext));
        if (dim) {
//        if (!isAlwaysOnEnabled(mContext)) {
            int curBrightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
            int dimAmount = 0;

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            if (daemon != null) {
                try {
                    dimAmount = daemon.getDimAmount(curBrightness);
                } catch (RemoteException e) {
                    // do nothing
                }
            }

            if (mShouldBoostBrightness) {
                mParams.screenBrightness = 1.0f;
            }
            Slog.d("FODCircleView", "brightness=" + curBrightness);

            Slog.d("FODCircleView", "dim= " + (((float) dimAmount) / 255.0f));

            mParams.dimAmount = (mDisplay.getState() == Display.STATE_DOZE) ? 0.0f : (((float) dimAmount) / 255.0f);
        } else {
            mParams.screenBrightness = 0.0f;
            mParams.dimAmount = 0.0f;
        }

        try {
            mWindowManager.updateViewLayout(this, mParams);
        } catch (IllegalArgumentException e) {
            // do nothing
        }
    }

    private class BurnInProtectionTask extends TimerTask {
        @Override
        public void run() {
            // It is fine to modify the variables here because
            // no other thread will be modifying it
            long now = System.currentTimeMillis() / 1000 / 60;
            mDreamingOffsetX = (int) (now % (mDreamingMaxOffset * 4));
            if (mDreamingOffsetX > mDreamingMaxOffset * 2) {
                mDreamingOffsetX = mDreamingMaxOffset * 4 - mDreamingOffsetX;
            }
            // Let y to be not synchronized with x, so that we get maximum movement
            mDreamingOffsetY = (int) ((now + mDreamingMaxOffset / 3) % (mDreamingMaxOffset * 2));
            if (mDreamingOffsetY > mDreamingMaxOffset * 2) {
                mDreamingOffsetY = mDreamingMaxOffset * 4 - mDreamingOffsetY;
            }
            mDreamingOffsetX -= mDreamingMaxOffset;
            mDreamingOffsetY -= mDreamingMaxOffset;
            if (mIsViewAdded) {
                mHandler.post(() -> resetPosition());
            }
        }
    };
}
