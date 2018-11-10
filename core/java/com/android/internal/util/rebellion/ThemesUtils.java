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

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.util.Log;

public class ThemesUtils {

    public static final String TAG = "ThemesUtils";

    public static final String[] SOLARIZED_DARK = {
            "com.android.theme.solarizeddark.system",
            "com.android.theme.solarizeddark.systemui",
    };

    public static final String[] BAKED_GREEN = {
            "com.android.theme.bakedgreen.system",
            "com.android.theme.bakedgreen.systemui",
    };

    public static final String[] CHOCO_X = {
            "com.android.theme.chocox.system",
            "com.android.theme.chocox.systemui",
    };

    public static final String[] PITCH_BLACK = {
            "com.android.theme.pitchblack.system",
            "com.android.theme.pitchblack.systemui",
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
}

