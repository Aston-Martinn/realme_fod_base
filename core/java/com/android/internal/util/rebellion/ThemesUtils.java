/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.util.rebellion;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.os.RemoteException;
import android.util.Log;

public class ThemesUtils {

    private Context mContext;
    private UiModeManager mUiModeManager;
    private IOverlayManager overlayManager;

    public ThemesUtils(Context context) {
        mContext = context;
        mUiModeManager = context.getSystemService(UiModeManager.class);
        overlayManager = IOverlayManager.Stub
                .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
    }

    public static final int DEVICE_THEME_LIGHT = 1; // Light
    public static final int DEVICE_THEME_DARK = 2; // Dark Af
    public static final int DEVICE_THEME_BLACK = 3; // Black Af
    public static final int DEVICE_THEME_REBELLION = 4; // Transparent
    public static final int DEVICE_THEME_SOLARIZED_DARK = 5;
    public static final int DEVICE_THEME_CHOCO_X = 6;
    public static final int DEVICE_THEME_PITCH_BLACK = 7;
    public static final int DEVICE_THEME_BAKED_GREEN = 8;

    public static final String TAG = "ThemesUtils";

    public static final String[] SOLARIZED_DARK = {
            "com.android.theme.solarizeddark.system",
            "com.android.theme.solarizeddark.systemui",
    };

    private static final String[] BAKED_GREEN = {
            "com.android.theme.bakedgreen.system",
            "com.android.theme.bakedgreen.systemui",
    };

    private static final String[] CHOCO_X = {
            "com.android.theme.chocox.system",
            "com.android.theme.chocox.systemui",
    };

    private static final String[] PITCH_BLACK = {
            "com.android.theme.pitchblack.system",
            "com.android.theme.pitchblack.systemui",
    };

    // Black Theme
    private static final String[] BLACK_THEMES = {
        "com.android.system.theme.black", // 0
        "com.android.settings.theme.black", // 1
        "com.android.systemui.theme.black", // 2
        "com.android.documentsui.theme.black", //4
    };

    // Rebellion Theme
    private static final String[] REBELLION_THEMES = {
        "com.android.system.theme.rebellion", // 0
        "com.android.settings.theme.rebellion", // 1
        "com.android.systemui.theme.rebellion", // 2
        "com.android.documentsui.theme.rebellion", //4
    };

    // Switch themes
    private static final String[] SWITCH_THEMES = {
        "com.android.system.switch.stock", // 0
        "com.android.system.switch.md2", // 1
        "com.android.system.switch.oneplus", // 2
    };

    // Notification themes
    private static final String[] NOTIFICATION_THEMES = {
        "com.android.system.notification.light", // 0
        "com.android.system.notification.dark", // 1
        "com.android.system.notification.black", // 2
    };

    // QS header themes
    private static final String[] QS_HEADER_THEMES = {
        "com.android.systemui.qsheader.black", // 0
        "com.android.systemui.qsheader.grey", // 1
        "com.android.systemui.qsheader.lightgrey", // 2
        "com.android.systemui.qsheader.accent", // 3
        "com.android.systemui.qsheader.transparent", // 4
    };

    public static void updateSwitchStyle(IOverlayManager om, int userId, int switchStyle) {
        if (switchStyle == 2) {
            stockSwitchStyle(om, userId);
        } else {
            try {
                om.setEnabled(SWITCH_THEMES[switchStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change switch theme", e);
            }
        }
    }

    public static void stockSwitchStyle(IOverlayManager om, int userId) {
        for (int i = 0; i < SWITCH_THEMES.length; i++) {
            String switchtheme = SWITCH_THEMES[i];
            try {
                om.setEnabled(switchtheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Switches notification style to user selected.
    public static void updateNotificationStyle(IOverlayManager om, int userId, int notificationStyle) {
        if (notificationStyle == 0) {
            stockNotificationStyle(om, userId);
        } else {
            try {
                om.setEnabled(NOTIFICATION_THEMES[notificationStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change notification theme", e);
            }
        }
    }

    // Switches notification style back to stock.
    public static void stockNotificationStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < NOTIFICATION_THEMES.length; i++) {
            String notificationtheme = NOTIFICATION_THEMES[i];
            try {
                om.setEnabled(notificationtheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Switches qs header style to user selected.
    public static void updateQSHeaderStyle(IOverlayManager om, int userId, int qsHeaderStyle) {
        if (qsHeaderStyle == 0) {
            stockQSHeaderStyle(om, userId);
        } else {
            try {
                om.setEnabled(QS_HEADER_THEMES[qsHeaderStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs header theme", e);
            }
        }
    }

    // Switches qs header style back to stock.
    public static void stockQSHeaderStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_HEADER_THEMES.length; i++) {
            String qsheadertheme = QS_HEADER_THEMES[i];
            try {
                om.setEnabled(qsheadertheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public String[] getTheme(int theme) {
        switch (theme) {
            case DEVICE_THEME_LIGHT:
            case DEVICE_THEME_DARK:
                break;
            case DEVICE_THEME_BLACK:
                return BLACK_THEMES;
            case DEVICE_THEME_REBELLION:
                return REBELLION_THEMES;
            case DEVICE_THEME_SOLARIZED_DARK:
                return SOLARIZED_DARK;
            case DEVICE_THEME_CHOCO_X:
                return CHOCO_X;
            case DEVICE_THEME_PITCH_BLACK:
                return PITCH_BLACK;
            case DEVICE_THEME_BAKED_GREEN:
                return BAKED_GREEN;
        }
        return null;
    }

    public void setTheme(int theme) {
	int mCurrentTheme = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SYSTEM_THEME_STYLE, 0, USER_SYSTEM);

        if (theme != mCurrentTheme) {
            setEnabled(getTheme(mCurrentTheme), false);
        } else if (theme == mCurrentTheme) {
            return;
        }

        if (theme == DEVICE_THEME_LIGHT) {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
        }
        else if(theme == DEVICE_THEME_DARK) {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
        }
        else {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
            setEnabled(getTheme(theme), true);
        }

        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SYSTEM_THEME_STYLE, theme);
    }

    public void setEnabled(String[] themes, boolean enabled) {

        if (themes == null)
            return;

        for (String theme : themes) {
            try {
                overlayManager.setEnabled(theme, enabled, USER_SYSTEM);
            } catch (RemoteException e) {
                Log.e(TAG, "Can't change theme", e);
            }
        }
    }
}

