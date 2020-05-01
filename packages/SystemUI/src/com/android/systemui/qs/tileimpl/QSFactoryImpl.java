/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use mHost file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tileimpl;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.ContextThemeWrapper;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSFactory;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.qs.external.CustomTile;
import com.android.systemui.qs.tiles.AdbOverNetworkTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.AODTile;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.CPUInfoTile;
import com.android.systemui.qs.tiles.DataSaverTile;
import com.android.systemui.qs.tiles.DataSwitchTile;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.FPSInfoTile;
import com.android.systemui.qs.tiles.GamingModeTile;
import com.android.systemui.qs.tiles.HeadsUpTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.LteTile;
import com.android.systemui.qs.tiles.MusicTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.NightDisplayTile;
import com.android.systemui.qs.tiles.RebootTile;
import com.android.systemui.qs.tiles.OnTheGoTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.SyncTile;
import com.android.systemui.qs.tiles.SmartPixelsTile;
import com.android.systemui.qs.tiles.SoundSearchTile;
import com.android.systemui.qs.tiles.UiModeNightTile;
import com.android.systemui.qs.tiles.SoundTile;
import com.android.systemui.qs.tiles.ScreenshotTile;
import com.android.systemui.qs.tiles.UserTile;
import com.android.systemui.qs.tiles.WeatherTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.qs.tiles.WorkModeTile;
import com.android.systemui.util.leak.GarbageMonitor;
import com.android.systemui.qs.tiles.CaffeineTile;
import com.android.systemui.qs.tiles.AmbientDisplayTile;
import com.android.systemui.qs.tiles.UsbTetherTile;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class QSFactoryImpl implements QSFactory {

    private static final String TAG = "QSFactory";

    private final Provider<WifiTile> mWifiTileProvider;
    private final Provider<BluetoothTile> mBluetoothTileProvider;
    private final Provider<CellularTile> mCellularTileProvider;
    private final Provider<DndTile> mDndTileProvider;
    private final Provider<ColorInversionTile> mColorInversionTileProvider;
    private final Provider<AirplaneModeTile> mAirplaneModeTileProvider;
    private final Provider<WorkModeTile> mWorkModeTileProvider;
    private final Provider<RotationLockTile> mRotationLockTileProvider;
    private final Provider<FlashlightTile> mFlashlightTileProvider;
    private final Provider<LocationTile> mLocationTileProvider;
    private final Provider<CastTile> mCastTileProvider;
    private final Provider<HotspotTile> mHotspotTileProvider;
    private final Provider<UserTile> mUserTileProvider;
    private final Provider<BatterySaverTile> mBatterySaverTileProvider;
    private final Provider<DataSaverTile> mDataSaverTileProvider;
    private final Provider<NightDisplayTile> mNightDisplayTileProvider;
    private final Provider<NfcTile> mNfcTileProvider;
    private final Provider<GarbageMonitor.MemoryTile> mMemoryTileProvider;
    private final Provider<UiModeNightTile> mUiModeNightTileProvider;
    private final Provider<CaffeineTile> mCaffeineTileProvider;
    private final Provider<AmbientDisplayTile> mAmbientDisplayTileProvider;
    private final Provider<UsbTetherTile> mUsbTetherTileProvider;
    private final Provider<SyncTile> mSyncTileProvider;
    private final Provider<SmartPixelsTile> mSmartPixelsTileProvider;
    private final Provider<GamingModeTile> mGamingModeTileProvider;
    private final Provider<LteTile> mLteTileProvider;
    private final Provider<CompassTile> mCompassTileProvider;
    private final Provider<MusicTile> mMusicTileProvider;
    private final Provider<RebootTile> mRebootTileProvider;
    private final Provider<SoundSearchTile> mSoundSearchTileProvider;
    private final Provider<DataSwitchTile> mDataSwitchTileProvider;
    private final Provider<AdbOverNetworkTile> mAdbOverNetworkProvider;
    private final Provider<FPSInfoTile> mFPSInfoTileProvider;
    private final Provider<AODTile> mAODTileProvider;
    private final Provider<CPUInfoTile> mCPUInfoTileProvider;

    private QSTileHost mHost;

    @Inject
    public QSFactoryImpl(Provider<WifiTile> wifiTileProvider,
            Provider<BluetoothTile> bluetoothTileProvider,
            Provider<CellularTile> cellularTileProvider,
            Provider<DndTile> dndTileProvider,
            Provider<ColorInversionTile> colorInversionTileProvider,
            Provider<AirplaneModeTile> airplaneModeTileProvider,
            Provider<WorkModeTile> workModeTileProvider,
            Provider<RotationLockTile> rotationLockTileProvider,
            Provider<FlashlightTile> flashlightTileProvider,
            Provider<LocationTile> locationTileProvider,
            Provider<CastTile> castTileProvider,
            Provider<HotspotTile> hotspotTileProvider,
            Provider<UserTile> userTileProvider,
            Provider<BatterySaverTile> batterySaverTileProvider,
            Provider<DataSaverTile> dataSaverTileProvider,
            Provider<NightDisplayTile> nightDisplayTileProvider,
            Provider<NfcTile> nfcTileProvider,
            Provider<GarbageMonitor.MemoryTile> memoryTileProvider,
            Provider<UiModeNightTile> uiModeNightTileProvider,
            Provider<CaffeineTile> caffeineTileProvider,
            Provider<AmbientDisplayTile> ambientDisplayTileProvider,
            Provider<UsbTetherTile> usbTetherTileProvider,
            Provider<SyncTile> syncTileProvider,
            Provider<SmartPixelsTile> smartPixelsTileProvider,
            Provider<GamingModeTile> gamingModeTileProvider,
            Provider<LteTile> lteTileProvider,
            Provider<CompassTile> compassTileProvider,
            Provider<MusicTile> musicTileProvider,
            Provider<RebootTile> rebootTileProvider,
            Provider<SoundSearchTile> soundSearchTileProvider,
            Provider<DataSwitchTile> dataSwitchTileProvider,
            Provider<AdbOverNetworkTile> adbOverNetworkProvider,
            Provider<AODTile> aodTileProvider,
            Provider<FPSInfoTile> fpsInfoTileProvider,
            Provider<CPUInfoTile> cpuInfoTileProvider) {
        mWifiTileProvider = wifiTileProvider;
        mBluetoothTileProvider = bluetoothTileProvider;
        mCellularTileProvider = cellularTileProvider;
        mDndTileProvider = dndTileProvider;
        mColorInversionTileProvider = colorInversionTileProvider;
        mAirplaneModeTileProvider = airplaneModeTileProvider;
        mWorkModeTileProvider = workModeTileProvider;
        mRotationLockTileProvider = rotationLockTileProvider;
        mFlashlightTileProvider = flashlightTileProvider;
        mLocationTileProvider = locationTileProvider;
        mCastTileProvider = castTileProvider;
        mHotspotTileProvider = hotspotTileProvider;
        mUserTileProvider = userTileProvider;
        mBatterySaverTileProvider = batterySaverTileProvider;
        mDataSaverTileProvider = dataSaverTileProvider;
        mNightDisplayTileProvider = nightDisplayTileProvider;
        mNfcTileProvider = nfcTileProvider;
        mMemoryTileProvider = memoryTileProvider;
        mUiModeNightTileProvider = uiModeNightTileProvider;
        mCaffeineTileProvider = caffeineTileProvider;
        mAmbientDisplayTileProvider = ambientDisplayTileProvider;
        mUsbTetherTileProvider = usbTetherTileProvider;
        mSyncTileProvider = syncTileProvider;
        mSmartPixelsTileProvider = smartPixelsTileProvider;
        mGamingModeTileProvider = gamingModeTileProvider;
        mLteTileProvider = lteTileProvider;
        mCompassTileProvider = compassTileProvider;
        mMusicTileProvider = musicTileProvider;
        mRebootTileProvider = rebootTileProvider;
        mSoundSearchTileProvider = soundSearchTileProvider;
        mDataSwitchTileProvider = dataSwitchTileProvider;
        mAdbOverNetworkProvider = adbOverNetworkProvider;
        mAODTileProvider = aodTileProvider;
        mFPSInfoTileProvider = fpsInfoTileProvider;
        mCPUInfoTileProvider = cpuInfoTileProvider;
    }

    public void setHost(QSTileHost host) {
        mHost = host;
    }

    public QSTile createTile(String tileSpec) {
        QSTileImpl tile = createTileInternal(tileSpec);
        if (tile != null) {
            tile.handleStale(); // Tile was just created, must be stale.
        }
        return tile;
    }

    private QSTileImpl createTileInternal(String tileSpec) {
        // Stock tiles.
        switch (tileSpec) {
            case "wifi":
                return mWifiTileProvider.get();
            case "bt":
                return mBluetoothTileProvider.get();
            case "cell":
                return mCellularTileProvider.get();
            case "dnd":
                return mDndTileProvider.get();
            case "inversion":
                return mColorInversionTileProvider.get();
            case "airplane":
                return mAirplaneModeTileProvider.get();
            case "work":
                return mWorkModeTileProvider.get();
            case "rotation":
                return mRotationLockTileProvider.get();
            case "flashlight":
                return mFlashlightTileProvider.get();
            case "location":
                return mLocationTileProvider.get();
            case "cast":
                return mCastTileProvider.get();
            case "hotspot":
                return mHotspotTileProvider.get();
            case "user":
                return mUserTileProvider.get();
            case "battery":
                return mBatterySaverTileProvider.get();
            case "saver":
                return mDataSaverTileProvider.get();
            case "night":
                return mNightDisplayTileProvider.get();
            case "nfc":
                return mNfcTileProvider.get();
            case "dark":
                return mUiModeNightTileProvider.get();
            case "caffeine":
                return mCaffeineTileProvider.get();
            case "ambient_display":
                return mAmbientDisplayTileProvider.get();
            case "usb_tether":
                return mUsbTetherTileProvider.get();
            case "sync":
                return mSyncTileProvider.get();
            case "sound":
                return new SoundTile(mHost);
            case "screenshot":
                return new ScreenshotTile(mHost);
            case "heads_up":
                return new HeadsUpTile(mHost);
            case "weather":
                return new WeatherTile(mHost);
            case "smartpixels":
                return mSmartPixelsTileProvider.get();
            case "gaming":
                return mGamingModeTileProvider.get();
            case "lte":
                return mLteTileProvider.get();
            case "compass":
                return mCompassTileProvider.get();
            case "music":
                return mMusicTileProvider.get();
            case "reboot":
                return mRebootTileProvider.get();
            case "soundsearch":
                return mSoundSearchTileProvider.get();
            case "dataswitch":
                return mDataSwitchTileProvider.get();
            case "adb_network":
                return mAdbOverNetworkProvider.get();
            case "aod":
                return mAODTileProvider.get();
            case "fpsinfo":
                return mFPSInfoTileProvider.get();
            case "onthego":
                return new OnTheGoTile(mHost);
            case "cpuinfo":
                return mCPUInfoTileProvider.get();
        }

        // Intent tiles.
        if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(mHost, tileSpec);
        if (tileSpec.startsWith(CustomTile.PREFIX)) return CustomTile.create(mHost, tileSpec);

        // Debug tiles.
        /*if (Build.IS_DEBUGGABLE) {
            if (tileSpec.equals(GarbageMonitor.MemoryTile.TILE_SPEC)) {
                return mMemoryTileProvider.get();
            }
        }*/

        // Broken tiles.
        Log.w(TAG, "No stock tile spec: " + tileSpec);
        return null;
    }

    @Override
    public QSTileView createTileView(QSTile tile, boolean collapsedView) {
        Context context = new ContextThemeWrapper(mHost.getContext(), R.style.qs_theme);
        QSIconView icon = tile.createTileView(context);
        if (collapsedView) {
            return new QSTileBaseView(context, icon, collapsedView);
        } else {
            return new com.android.systemui.qs.tileimpl.QSTileView(context, icon);
        }
    }
}
