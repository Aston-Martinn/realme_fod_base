/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.server.hdmi;

import static com.android.server.hdmi.Constants.ADDR_AUDIO_SYSTEM;
import static com.android.server.hdmi.Constants.ADDR_BROADCAST;
import static com.android.server.hdmi.Constants.ADDR_TV;
import static com.android.server.hdmi.HdmiControlService.INITIATED_BY_ENABLE_CEC;
import static com.android.server.hdmi.HdmiControlService.STANDBY_SCREEN_OFF;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertEquals;

import android.media.AudioManager;
import android.os.Looper;
import android.os.test.TestLooper;
import android.support.test.filters.SmallTest;

import com.android.server.hdmi.HdmiCecLocalDevice.ActiveSource;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SmallTest
@RunWith(JUnit4.class)
/**
 * Tests for {@link HdmiCecLocalDeviceAudioSystem} class.
 */
public class HdmiCecLocalDeviceAudioSystemTest {

    private static final String TAG = "HdmiCecLocalDeviceAudioSystemTest";
    private HdmiControlService mHdmiControlService;
    private HdmiCecController mHdmiCecController;
    private HdmiCecLocalDeviceAudioSystem mHdmiCecLocalDeviceAudioSystem;
    private FakeNativeWrapper mNativeWrapper;
    private Looper mMyLooper;
    private TestLooper mTestLooper = new TestLooper();
    private ArrayList<HdmiCecLocalDevice> mLocalDevices = new ArrayList<>();
    private int mMusicVolume;
    private int mMusicMaxVolume;
    private boolean mMusicMute;
    private boolean isAwake;

    @Before
    public void SetUp() {
        isAwake = false;
        mHdmiControlService = new HdmiControlService(null) {
            @Override
            AudioManager getAudioManager() {
                return new AudioManager() {
                    @Override
                    public int getStreamVolume(int streamType) {
                        switch (streamType) {
                            case STREAM_MUSIC:
                                return mMusicVolume;
                            default:
                                return 0;
                        }
                    }

                    @Override
                    public boolean isStreamMute(int streamType) {
                        switch (streamType) {
                            case STREAM_MUSIC:
                                return mMusicMute;
                            default:
                                return false;
                        }
                    }

                    @Override
                    public int getStreamMaxVolume(int streamType) {
                        switch (streamType) {
                            case STREAM_MUSIC:
                                return mMusicMaxVolume;
                            default:
                                return 100;
                        }
                    }

                    @Override
                    public void adjustStreamVolume(int streamType, int direction, int flags) {
                        switch (streamType) {
                            case STREAM_MUSIC:
                                if (direction == AudioManager.ADJUST_UNMUTE) {
                                    mMusicMute = false;
                                } else if (direction == AudioManager.ADJUST_MUTE) {
                                    mMusicMute = true;
                                }
                            default:
                        }
                    }
                };
            }

            @Override
            void wakeUp() {
                isAwake = true;
            }
        };
        mMyLooper = mTestLooper.getLooper();
        mHdmiCecLocalDeviceAudioSystem = new HdmiCecLocalDeviceAudioSystem(mHdmiControlService);
        mHdmiCecLocalDeviceAudioSystem.init();
        mHdmiControlService.setIoLooper(mMyLooper);

        mNativeWrapper = new FakeNativeWrapper();
        mHdmiCecController = HdmiCecController.createWithNativeWrapper(
            mHdmiControlService, mNativeWrapper);
        mHdmiControlService.setCecController(mHdmiCecController);
        mHdmiControlService.setHdmiMhlController(HdmiMhlControllerStub.create(mHdmiControlService));
        mHdmiControlService.setMessageValidator(new HdmiCecMessageValidator(mHdmiControlService));

        mLocalDevices.add(mHdmiCecLocalDeviceAudioSystem);
        mHdmiControlService.initPortInfo();
        // No TV device interacts with AVR so system audio control won't be turned on here
        mHdmiControlService.allocateLogicalAddress(mLocalDevices, INITIATED_BY_ENABLE_CEC);

        mTestLooper.dispatchAll();
    }

    @Test
    public void handleGiveAudioStatus_volume_10_mute_true() {
        mMusicVolume = 10;
        mMusicMute = true;
        mMusicMaxVolume = 20;
        int scaledVolume = VolumeControlAction.scaleToCecVolume(10, mMusicMaxVolume);
        HdmiCecMessage expectMessage = HdmiCecMessageBuilder.buildReportAudioStatus(
            ADDR_AUDIO_SYSTEM, ADDR_TV, scaledVolume, true);
        HdmiCecMessage messageGive = HdmiCecMessageBuilder.buildGiveAudioStatus(
            ADDR_TV, ADDR_AUDIO_SYSTEM);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleGiveAudioStatus(messageGive));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
    }

    @Test
    public void handleGiveSystemAudioModeStatus_originalOff() {
        HdmiCecMessage expectMessage = HdmiCecMessageBuilder
            .buildReportSystemAudioMode(ADDR_AUDIO_SYSTEM, ADDR_TV, false);
        HdmiCecMessage messageGive = HdmiCecMessageBuilder
            .buildGiveSystemAudioModeStatus(ADDR_TV, ADDR_AUDIO_SYSTEM);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleGiveSystemAudioModeStatus(messageGive));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
    }

    @Test
    public void handleRequestArcInitiate() {
        // TODO(b/80296911): Add tests when finishing handler impl.
        HdmiCecMessage expectMessage = HdmiCecMessageBuilder
            .buildInitiateArc(ADDR_AUDIO_SYSTEM, ADDR_TV);
        HdmiCecMessage message = HdmiCecMessageBuilder
            .buildRequestArcInitiation(ADDR_TV, ADDR_AUDIO_SYSTEM);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleRequestArcInitiate(message));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
    }

    @Test
    public void handleRequestArcTermination() {
        // TODO(b/80297105): Add tests when finishing handler impl.
        HdmiCecMessage expectMessage = HdmiCecMessageBuilder
            .buildTerminateArc(ADDR_AUDIO_SYSTEM, ADDR_TV);
        HdmiCecMessage messageRequestOff = HdmiCecMessageBuilder
            .buildRequestArcTermination(ADDR_TV, ADDR_AUDIO_SYSTEM);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleRequestArcTermination(messageRequestOff));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
    }

    @Test
    public void handleSetSystemAudioMode_setOn_orignalOff() {
        mMusicMute = true;
        HdmiCecMessage messageSet = HdmiCecMessageBuilder
            .buildSetSystemAudioMode(ADDR_TV, ADDR_AUDIO_SYSTEM, true);
        HdmiCecMessage messageGive = HdmiCecMessageBuilder
            .buildGiveSystemAudioModeStatus(ADDR_TV, ADDR_AUDIO_SYSTEM);

        // Check if originally off
        HdmiCecMessage expectMessage = HdmiCecMessageBuilder
            .buildReportSystemAudioMode(ADDR_AUDIO_SYSTEM, ADDR_TV, false);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleGiveSystemAudioModeStatus(messageGive));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());

        // Check if correctly turned on
        expectMessage = HdmiCecMessageBuilder
            .buildReportSystemAudioMode(ADDR_AUDIO_SYSTEM, ADDR_TV, true);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleSetSystemAudioMode(messageSet));
        mTestLooper.dispatchAll();
        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleGiveSystemAudioModeStatus(messageGive));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
        assertFalse(mMusicMute);
    }

    @Test
    public void handleSystemAudioModeRequest_turnOffByTv() {
        assertFalse(mMusicMute);

        // Check if feature correctly turned off
        HdmiCecMessage messageGive = HdmiCecMessageBuilder
            .buildGiveSystemAudioModeStatus(ADDR_TV, ADDR_AUDIO_SYSTEM);
        HdmiCecMessage messageRequestOff = HdmiCecMessageBuilder
            .buildSystemAudioModeRequest(ADDR_TV, ADDR_AUDIO_SYSTEM, 2, false);

        HdmiCecMessage expectMessage = HdmiCecMessageBuilder
            .buildSetSystemAudioMode(ADDR_AUDIO_SYSTEM, ADDR_BROADCAST, false);
        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleSystemAudioModeRequest(messageRequestOff));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());

        expectMessage = HdmiCecMessageBuilder
            .buildReportSystemAudioMode(ADDR_AUDIO_SYSTEM, ADDR_TV, false);
        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleGiveSystemAudioModeStatus(messageGive));
        mTestLooper.dispatchAll();
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
        assertTrue(mMusicMute);
    }

    @Test
    public void onStandbyAudioSystem_currentSystemAudioControlOn() {
        // Set system audio control on first
        mHdmiCecLocalDeviceAudioSystem.setSystemAudioMode(true);

        // Check if standby correctly turns off the feature
        mHdmiCecLocalDeviceAudioSystem.onStandby(false, STANDBY_SCREEN_OFF);
        mTestLooper.dispatchAll();
        HdmiCecMessage expectMessage = HdmiCecMessageBuilder
            .buildSetSystemAudioMode(ADDR_AUDIO_SYSTEM, ADDR_BROADCAST, false);
        assertEquals(expectMessage, mNativeWrapper.getResultMessage());
        assertTrue(mMusicMute);
    }

    @Test
    public void systemAudioControlOnPowerOn_alwaysOn() {
        mHdmiCecLocalDeviceAudioSystem.removeAction(
            SystemAudioInitiationActionFromAvr.class);
        mHdmiCecLocalDeviceAudioSystem.systemAudioControlOnPowerOn(
            Constants.ALWAYS_SYSTEM_AUDIO_CONTROL_ON_POWER_ON, true);

        assertThat(mHdmiCecLocalDeviceAudioSystem
            .getActions(SystemAudioInitiationActionFromAvr.class)).isNotEmpty();
    }

    @Test
    public void systemAudioControlOnPowerOn_neverOn() {
        mHdmiCecLocalDeviceAudioSystem.removeAction(
            SystemAudioInitiationActionFromAvr.class);
        mHdmiCecLocalDeviceAudioSystem.systemAudioControlOnPowerOn(
            Constants.NEVER_SYSTEM_AUDIO_CONTROL_ON_POWER_ON, false);

        assertThat(mHdmiCecLocalDeviceAudioSystem
            .getActions(SystemAudioInitiationActionFromAvr.class)).isEmpty();
    }

    @Test
    public void systemAudioControlOnPowerOn_useLastState_off() {
        mHdmiCecLocalDeviceAudioSystem.removeAction(
            SystemAudioInitiationActionFromAvr.class);
        mHdmiCecLocalDeviceAudioSystem.systemAudioControlOnPowerOn(
            Constants.USE_LAST_STATE_SYSTEM_AUDIO_CONTROL_ON_POWER_ON, false);

        assertThat(mHdmiCecLocalDeviceAudioSystem
            .getActions(SystemAudioInitiationActionFromAvr.class)).isEmpty();
    }

    @Test
    public void systemAudioControlOnPowerOn_useLastState_on() {
        mHdmiCecLocalDeviceAudioSystem.removeAction(
            SystemAudioInitiationActionFromAvr.class);
        mHdmiCecLocalDeviceAudioSystem.systemAudioControlOnPowerOn(
            Constants.USE_LAST_STATE_SYSTEM_AUDIO_CONTROL_ON_POWER_ON, true);

        assertThat(mHdmiCecLocalDeviceAudioSystem
            .getActions(SystemAudioInitiationActionFromAvr.class)).isNotEmpty();
    }

    @Test
    public void handleActiveSource_updateActiveSource() {
        HdmiCecMessage message = HdmiCecMessageBuilder
            .buildActiveSource(ADDR_TV, 0x0000);
        ActiveSource expectedActiveSource = new ActiveSource(ADDR_TV, 0x0000);

        assertTrue(mHdmiCecLocalDeviceAudioSystem.handleActiveSource(message));
        mTestLooper.dispatchAll();
        assertTrue(mHdmiCecLocalDeviceAudioSystem.getActiveSource().equals(expectedActiveSource));
    }
}
