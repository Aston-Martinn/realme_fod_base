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
package com.android.server.notification;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationManager.IMPORTANCE_UNSPECIFIED;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.internal.util.FastXmlSerializer;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import android.app.Notification;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.TestableContext;
import android.testing.TestableSettingsProvider;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.Xml;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RankingHelperTest extends NotificationTestCase {
    private static final String PKG = "com.android.server.notification";
    private static final int UID = 0;
    private static final UserHandle USER = UserHandle.getUserHandleForUid(UID);
    private static final String UPDATED_PKG = "updatedPkg";
    private static final int UID2 = 1111111;
    private static final UserHandle USER2 = UserHandle.getUserHandleForUid(UID2);
    private static final String TEST_CHANNEL_ID = "test_channel_id";

    @Mock NotificationUsageStats mUsageStats;
    @Mock RankingHandler mHandler;
    @Mock PackageManager mPm;
    @Mock Context mContext;

    private Notification mNotiGroupGSortA;
    private Notification mNotiGroupGSortB;
    private Notification mNotiNoGroup;
    private Notification mNotiNoGroup2;
    private Notification mNotiNoGroupSortA;
    private NotificationRecord mRecordGroupGSortA;
    private NotificationRecord mRecordGroupGSortB;
    private NotificationRecord mRecordNoGroup;
    private NotificationRecord mRecordNoGroup2;
    private NotificationRecord mRecordNoGroupSortA;
    private RankingHelper mHelper;
    private AudioAttributes mAudioAttributes;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        UserHandle user = UserHandle.ALL;

        final ApplicationInfo legacy = new ApplicationInfo();
        legacy.targetSdkVersion = Build.VERSION_CODES.N_MR1;
        final ApplicationInfo upgrade = new ApplicationInfo();
        upgrade.targetSdkVersion = Build.VERSION_CODES.N_MR1 + 1;
        when(mPm.getApplicationInfoAsUser(eq(PKG), anyInt(), anyInt())).thenReturn(legacy);
        when(mPm.getApplicationInfoAsUser(eq(UPDATED_PKG), anyInt(), anyInt())).thenReturn(upgrade);
        when(mPm.getPackageUidAsUser(eq(PKG), anyInt())).thenReturn(UID);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getContext().getResources());
        when(mContext.getPackageManager()).thenReturn(mPm);
        when(mContext.getApplicationInfo()).thenReturn(legacy);
        // most tests assume badging is enabled
        Secure.putIntForUser(getContext().getContentResolver(),
                Secure.NOTIFICATION_BADGING, 1, UserHandle.getUserId(UID));

        mHelper = new RankingHelper(getContext(), mPm, mHandler, mUsageStats,
                new String[] {ImportanceExtractor.class.getName()});

        mNotiGroupGSortA = new Notification.Builder(mContext, TEST_CHANNEL_ID)
                .setContentTitle("A")
                .setGroup("G")
                .setSortKey("A")
                .setWhen(1205)
                .build();
        mRecordGroupGSortA = new NotificationRecord(mContext, new StatusBarNotification(
                PKG, PKG, 1, null, 0, 0, mNotiGroupGSortA, user,
                null, System.currentTimeMillis()), getDefaultChannel());

        mNotiGroupGSortB = new Notification.Builder(mContext, TEST_CHANNEL_ID)
                .setContentTitle("B")
                .setGroup("G")
                .setSortKey("B")
                .setWhen(1200)
                .build();
        mRecordGroupGSortB = new NotificationRecord(mContext, new StatusBarNotification(
                PKG, PKG, 1, null, 0, 0, mNotiGroupGSortB, user,
                null, System.currentTimeMillis()), getDefaultChannel());

        mNotiNoGroup = new Notification.Builder(mContext, TEST_CHANNEL_ID)
                .setContentTitle("C")
                .setWhen(1201)
                .build();
        mRecordNoGroup = new NotificationRecord(mContext, new StatusBarNotification(
                PKG, PKG, 1, null, 0, 0, mNotiNoGroup, user,
                null, System.currentTimeMillis()), getDefaultChannel());

        mNotiNoGroup2 = new Notification.Builder(mContext, TEST_CHANNEL_ID)
                .setContentTitle("D")
                .setWhen(1202)
                .build();
        mRecordNoGroup2 = new NotificationRecord(mContext, new StatusBarNotification(
                PKG, PKG, 1, null, 0, 0, mNotiNoGroup2, user,
                null, System.currentTimeMillis()), getDefaultChannel());

        mNotiNoGroupSortA = new Notification.Builder(mContext, TEST_CHANNEL_ID)
                .setContentTitle("E")
                .setWhen(1201)
                .setSortKey("A")
                .build();
        mRecordNoGroupSortA = new NotificationRecord(mContext, new StatusBarNotification(
                PKG, PKG, 1, null, 0, 0, mNotiNoGroupSortA, user,
                null, System.currentTimeMillis()), getDefaultChannel());

        mAudioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build();
    }

    private NotificationChannel getDefaultChannel() {
        return new NotificationChannel(NotificationChannel.DEFAULT_CHANNEL_ID, "name",
                IMPORTANCE_LOW);
    }

    private ByteArrayOutputStream writeXmlAndPurge(String pkg, int uid, boolean forBackup,
            String... channelIds)
            throws Exception {
        XmlSerializer serializer = new FastXmlSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);
        serializer.startTag(null, "ranking");
        mHelper.writeXml(serializer, forBackup);
        serializer.endTag(null, "ranking");
        serializer.endDocument();
        serializer.flush();

        for (String channelId : channelIds) {
            mHelper.permanentlyDeleteNotificationChannel(pkg, uid, channelId);
        }
        return baos;
    }

    private void loadStreamXml(ByteArrayOutputStream stream) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(stream.toByteArray())),
                null);
        parser.nextTag();
        mHelper.readXml(parser, false);
    }

    private void compareChannels(NotificationChannel expected, NotificationChannel actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.shouldVibrate(), actual.shouldVibrate());
        assertEquals(expected.shouldShowLights(), actual.shouldShowLights());
        assertEquals(expected.getImportance(), actual.getImportance());
        assertEquals(expected.getLockscreenVisibility(), actual.getLockscreenVisibility());
        assertEquals(expected.getSound(), actual.getSound());
        assertEquals(expected.canBypassDnd(), actual.canBypassDnd());
        assertTrue(Arrays.equals(expected.getVibrationPattern(), actual.getVibrationPattern()));
        assertEquals(expected.getGroup(), actual.getGroup());
        assertEquals(expected.getAudioAttributes(), actual.getAudioAttributes());
        assertEquals(expected.getLightColor(), actual.getLightColor());
    }

    private void compareGroups(NotificationChannelGroup expected, NotificationChannelGroup actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
    }

    private NotificationChannel getChannel() {
        return new NotificationChannel("id", "name", IMPORTANCE_LOW);
    }

    @Test
    public void testFindAfterRankingWithASplitGroup() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(3);
        notificationList.add(mRecordGroupGSortA);
        notificationList.add(mRecordGroupGSortB);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroupSortA);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordGroupGSortA) >= 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordGroupGSortB) >= 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) >= 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroupSortA) >= 0);
    }

    @Test
    public void testSortShouldNotThrowWithPlainNotifications() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(2);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroup2);
        mHelper.sort(notificationList);
    }

    @Test
    public void testSortShouldNotThrowOneSorted() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(2);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroupSortA);
        mHelper.sort(notificationList);
    }

    @Test
    public void testSortShouldNotThrowOneNotification() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(1);
        notificationList.add(mRecordNoGroup);
        mHelper.sort(notificationList);
    }

    @Test
    public void testSortShouldNotThrowOneSortKey() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(1);
        notificationList.add(mRecordGroupGSortB);
        mHelper.sort(notificationList);
    }

    @Test
    public void testSortShouldNotThrowOnEmptyList() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>();
        mHelper.sort(notificationList);
    }

    @Test
    public void testChannelXml() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("1", "bye");
        NotificationChannelGroup ncg2 = new NotificationChannelGroup("2", "hello");
        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel2 =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel2.setDescription("descriptions for all");
        channel2.setSound(new Uri.Builder().scheme("test").build(), mAudioAttributes);
        channel2.enableLights(true);
        channel2.setBypassDnd(true);
        channel2.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel2.enableVibration(true);
        channel2.setGroup(ncg.getId());
        channel2.setVibrationPattern(new long[]{100, 67, 145, 156});
        channel2.setLightColor(Color.BLUE);

        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);
        mHelper.createNotificationChannelGroup(PKG, UID, ncg2, true);
        mHelper.createNotificationChannel(PKG, UID, channel1, true);
        mHelper.createNotificationChannel(PKG, UID, channel2, false);

        mHelper.setShowBadge(PKG, UID, true);

        ByteArrayOutputStream baos = writeXmlAndPurge(PKG, UID, false, channel1.getId(),
                channel2.getId(), NotificationChannel.DEFAULT_CHANNEL_ID);
        mHelper.onPackagesChanged(true, UserHandle.myUserId(), new String[]{PKG}, new int[]{UID});

        loadStreamXml(baos);

        assertTrue(mHelper.canShowBadge(PKG, UID));
        assertEquals(channel1, mHelper.getNotificationChannel(PKG, UID, channel1.getId(), false));
        compareChannels(channel2,
                mHelper.getNotificationChannel(PKG, UID, channel2.getId(), false));

        List<NotificationChannelGroup> actualGroups =
                mHelper.getNotificationChannelGroups(PKG, UID, false).getList();
        boolean foundNcg = false;
        for (NotificationChannelGroup actual : actualGroups) {
            if (ncg.getId().equals(actual.getId())) {
                foundNcg = true;
                compareGroups(ncg, actual);
            } else if (ncg2.getId().equals(actual.getId())) {
                compareGroups(ncg2, actual);
            }
        }
        assertTrue(foundNcg);

        boolean foundChannel2Group = false;
        for (NotificationChannelGroup actual : actualGroups) {
            if (channel2.getGroup().equals(actual.getChannels().get(0).getGroup())) {
                foundChannel2Group = true;
                break;
            }
        }
        assertTrue(foundChannel2Group);
    }

    @Test
    public void testChannelXml_backup() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("1", "bye");
        NotificationChannelGroup ncg2 = new NotificationChannelGroup("2", "hello");
        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel2 =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        NotificationChannel channel3 =
                new NotificationChannel("id3", "name3", IMPORTANCE_LOW);
        channel3.setGroup(ncg.getId());

        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);
        mHelper.createNotificationChannelGroup(PKG, UID, ncg2, true);
        mHelper.createNotificationChannel(PKG, UID, channel1, true);
        mHelper.createNotificationChannel(PKG, UID, channel2, false);
        mHelper.createNotificationChannel(PKG, UID, channel3, true);

        mHelper.deleteNotificationChannel(PKG, UID, channel1.getId());
        mHelper.deleteNotificationChannelGroup(PKG, UID, ncg.getId());
        assertEquals(channel2, mHelper.getNotificationChannel(PKG, UID, channel2.getId(), false));

        ByteArrayOutputStream baos = writeXmlAndPurge(PKG, UID, true, channel1.getId(),
                channel2.getId(), channel3.getId(), NotificationChannel.DEFAULT_CHANNEL_ID);
        mHelper.onPackagesChanged(true, UserHandle.myUserId(), new String[]{PKG}, new int[]{UID});

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray())),
                null);
        parser.nextTag();
        mHelper.readXml(parser, true);

        assertNull(mHelper.getNotificationChannel(PKG, UID, channel1.getId(), false));
        assertNull(mHelper.getNotificationChannel(PKG, UID, channel3.getId(), false));
        assertNull(mHelper.getNotificationChannelGroup(ncg.getId(), PKG, UID));
        //assertEquals(ncg2, mHelper.getNotificationChannelGroup(ncg2.getId(), PKG, UID));
        assertEquals(channel2, mHelper.getNotificationChannel(PKG, UID, channel2.getId(), false));
    }

    @Test
    public void testChannelXml_defaultChannelLegacyApp_noUserSettings() throws Exception {
        ByteArrayOutputStream baos = writeXmlAndPurge(PKG, UID, false,
                NotificationChannel.DEFAULT_CHANNEL_ID);

        loadStreamXml(baos);

        final NotificationChannel updated = mHelper.getNotificationChannel(PKG, UID,
                NotificationChannel.DEFAULT_CHANNEL_ID, false);
        assertEquals(NotificationManager.IMPORTANCE_UNSPECIFIED, updated.getImportance());
        assertFalse(updated.canBypassDnd());
        assertEquals(NotificationManager.VISIBILITY_NO_OVERRIDE, updated.getLockscreenVisibility());
        assertEquals(0, updated.getUserLockedFields());
    }

    @Test
    public void testChannelXml_defaultChannelUpdatedApp_userSettings() throws Exception {
        final NotificationChannel defaultChannel = mHelper.getNotificationChannel(PKG, UID,
                NotificationChannel.DEFAULT_CHANNEL_ID, false);
        defaultChannel.setImportance(NotificationManager.IMPORTANCE_LOW);
        mHelper.updateNotificationChannel(PKG, UID, defaultChannel);

        ByteArrayOutputStream baos = writeXmlAndPurge(PKG, UID, false,
                NotificationChannel.DEFAULT_CHANNEL_ID);

        loadStreamXml(baos);

        assertEquals(NotificationManager.IMPORTANCE_LOW, mHelper.getNotificationChannel(
                PKG, UID, NotificationChannel.DEFAULT_CHANNEL_ID, false).getImportance());
    }

    @Test
    public void testChannelXml_upgradeCreateDefaultChannel() throws Exception {
        final String preupgradeXml = "<ranking version=\"1\">\n"
                + "<package name=\"" + PKG
                + "\" importance=\"" + NotificationManager.IMPORTANCE_HIGH
                + "\" priority=\"" + Notification.PRIORITY_MAX + "\" visibility=\""
                + Notification.VISIBILITY_SECRET + "\"" +" uid=\"" + UID + "\" />\n"
                + "<package name=\"" + UPDATED_PKG + "\" uid=\"" + UID2 + "\" visibility=\""
                + Notification.VISIBILITY_PRIVATE + "\" />\n"
                + "</ranking>";
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(preupgradeXml.getBytes())),
                null);
        parser.nextTag();
        mHelper.readXml(parser, false);

        final NotificationChannel updated1 =
            mHelper.getNotificationChannel(PKG, UID, NotificationChannel.DEFAULT_CHANNEL_ID, false);
        assertEquals(NotificationManager.IMPORTANCE_HIGH, updated1.getImportance());
        assertTrue(updated1.canBypassDnd());
        assertEquals(Notification.VISIBILITY_SECRET, updated1.getLockscreenVisibility());
        assertEquals(NotificationChannel.USER_LOCKED_IMPORTANCE
                | NotificationChannel.USER_LOCKED_PRIORITY
                | NotificationChannel.USER_LOCKED_VISIBILITY,
                updated1.getUserLockedFields());

        // No Default Channel created for updated packages
        assertEquals(null, mHelper.getNotificationChannel(UPDATED_PKG, UID2,
                NotificationChannel.DEFAULT_CHANNEL_ID, false));
    }

    @Test
    public void testChannelXml_upgradeDeletesDefaultChannel() throws Exception {
        final NotificationChannel defaultChannel = mHelper.getNotificationChannel(
                PKG, UID, NotificationChannel.DEFAULT_CHANNEL_ID, false);
        assertTrue(defaultChannel != null);
        ByteArrayOutputStream baos =
                writeXmlAndPurge(PKG, UID, false, NotificationChannel.DEFAULT_CHANNEL_ID);
        // Load package at higher sdk.
        final ApplicationInfo upgraded = new ApplicationInfo();
        upgraded.targetSdkVersion = Build.VERSION_CODES.N_MR1 + 1;
        when(mPm.getApplicationInfoAsUser(eq(PKG), anyInt(), anyInt())).thenReturn(upgraded);
        loadStreamXml(baos);

        // Default Channel should be gone.
        assertEquals(null, mHelper.getNotificationChannel(PKG, UID,
                NotificationChannel.DEFAULT_CHANNEL_ID, false));
    }

    @Test
    public void testDeletesDefaultChannelAfterChannelIsCreated() throws Exception {
        mHelper.createNotificationChannel(PKG, UID,
                new NotificationChannel("bananas", "bananas", IMPORTANCE_LOW), true);
        ByteArrayOutputStream baos = writeXmlAndPurge(PKG, UID, false,
                NotificationChannel.DEFAULT_CHANNEL_ID, "bananas");

        // Load package at higher sdk.
        final ApplicationInfo upgraded = new ApplicationInfo();
        upgraded.targetSdkVersion = Build.VERSION_CODES.N_MR1 + 1;
        when(mPm.getApplicationInfoAsUser(eq(PKG), anyInt(), anyInt())).thenReturn(upgraded);
        loadStreamXml(baos);

        // Default Channel should be gone.
        assertEquals(null, mHelper.getNotificationChannel(PKG, UID,
                NotificationChannel.DEFAULT_CHANNEL_ID, false));
    }

    @Test
    public void testLoadingOldChannelsDoesNotDeleteNewlyCreatedChannels() throws Exception {
        ByteArrayOutputStream baos = writeXmlAndPurge(PKG, UID, false,
                NotificationChannel.DEFAULT_CHANNEL_ID, "bananas");
        mHelper.createNotificationChannel(PKG, UID,
                new NotificationChannel("bananas", "bananas", IMPORTANCE_LOW), true);

        loadStreamXml(baos);

        // Should still have the newly created channel that wasn't in the xml.
        assertTrue(mHelper.getNotificationChannel(PKG, UID, "bananas", false) != null);
    }

    @Test
    public void testCreateChannel_blocked() throws Exception {
        mHelper.setImportance(PKG, UID, IMPORTANCE_NONE);

        mHelper.createNotificationChannel(PKG, UID,
                new NotificationChannel("bananas", "bananas", IMPORTANCE_LOW), true);
    }

    @Test
    public void testCreateChannel_ImportanceNone() throws Exception {
        try {
            mHelper.createNotificationChannel(PKG, UID,
                    new NotificationChannel("bananas", "bananas", IMPORTANCE_NONE), true);
            fail("Was allowed to create a blocked channel");
        } catch (IllegalArgumentException e) {
            // yay
        }
    }


    @Test
    public void testUpdate() throws Exception {
        // no fields locked by user
        final NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel.setSound(new Uri.Builder().scheme("test").build(), mAudioAttributes);
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        mHelper.createNotificationChannel(PKG, UID, channel, false);

        // same id, try to update all fields
        final NotificationChannel channel2 =
                new NotificationChannel("id2", "name2", NotificationManager.IMPORTANCE_HIGH);
        channel2.setSound(new Uri.Builder().scheme("test2").build(), mAudioAttributes);
        channel2.enableLights(false);
        channel2.setBypassDnd(false);
        channel2.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        mHelper.updateNotificationChannel(PKG, UID, channel2);

        // all fields should be changed
        assertEquals(channel2, mHelper.getNotificationChannel(PKG, UID, channel.getId(), false));
    }

    @Test
    public void testUpdate_preUpgrade_updatesAppFields() throws Exception {
        mHelper.setImportance(PKG, UID, IMPORTANCE_UNSPECIFIED);
        assertTrue(mHelper.canShowBadge(PKG, UID));
        assertEquals(Notification.PRIORITY_DEFAULT, mHelper.getPackagePriority(PKG, UID));
        assertEquals(NotificationManager.VISIBILITY_NO_OVERRIDE,
                mHelper.getPackageVisibility(PKG, UID));

        NotificationChannel defaultChannel = mHelper.getNotificationChannel(
                PKG, UID, NotificationChannel.DEFAULT_CHANNEL_ID, false);

        defaultChannel.setShowBadge(false);
        defaultChannel.setImportance(IMPORTANCE_NONE);
        defaultChannel.setBypassDnd(true);
        defaultChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        mHelper.updateNotificationChannel(PKG, UID, defaultChannel);

        // ensure app level fields are changed
        assertFalse(mHelper.canShowBadge(PKG, UID));
        assertEquals(Notification.PRIORITY_MAX, mHelper.getPackagePriority(PKG, UID));
        assertEquals(Notification.VISIBILITY_SECRET, mHelper.getPackageVisibility(PKG, UID));
        assertEquals(IMPORTANCE_NONE, mHelper.getImportance(PKG, UID));
    }

    @Test
    public void testUpdate_postUpgrade_noUpdateAppFields() throws Exception {
        final NotificationChannel channel = new NotificationChannel("id2", "name2", IMPORTANCE_LOW);

        mHelper.createNotificationChannel(PKG, UID, channel, false);
        assertTrue(mHelper.canShowBadge(PKG, UID));
        assertEquals(Notification.PRIORITY_DEFAULT, mHelper.getPackagePriority(PKG, UID));
        assertEquals(NotificationManager.VISIBILITY_NO_OVERRIDE,
                mHelper.getPackageVisibility(PKG, UID));

        channel.setShowBadge(false);
        channel.setImportance(IMPORTANCE_NONE);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        mHelper.updateNotificationChannel(PKG, UID, channel);

        // ensure app level fields are not changed
        assertTrue(mHelper.canShowBadge(PKG, UID));
        assertEquals(Notification.PRIORITY_DEFAULT, mHelper.getPackagePriority(PKG, UID));
        assertEquals(NotificationManager.VISIBILITY_NO_OVERRIDE,
                mHelper.getPackageVisibility(PKG, UID));
        assertEquals(NotificationManager.IMPORTANCE_UNSPECIFIED, mHelper.getImportance(PKG, UID));
    }

    @Test
    public void testGetNotificationChannel_ReturnsNullForUnknownChannel() throws Exception {
        assertEquals(null, mHelper.getNotificationChannel(PKG, UID, "garbage", false));
    }

    @Test
    public void testCreateChannel_CannotChangeHiddenFields() throws Exception {
        final NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel.setSound(new Uri.Builder().scheme("test").build(), mAudioAttributes);
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.setShowBadge(true);
        int lockMask = 0;
        for (int i = 0; i < NotificationChannel.LOCKABLE_FIELDS.length; i++) {
            lockMask |= NotificationChannel.LOCKABLE_FIELDS[i];
        }
        channel.lockFields(lockMask);

        mHelper.createNotificationChannel(PKG, UID, channel, true);

        NotificationChannel savedChannel =
                mHelper.getNotificationChannel(PKG, UID, channel.getId(), false);

        assertEquals(channel.getName(), savedChannel.getName());
        assertEquals(channel.shouldShowLights(), savedChannel.shouldShowLights());
        assertFalse(savedChannel.canBypassDnd());
        assertFalse(Notification.VISIBILITY_SECRET == savedChannel.getLockscreenVisibility());
        assertEquals(channel.canShowBadge(), savedChannel.canShowBadge());
    }

    @Test
    public void testCreateChannel_CannotChangeHiddenFieldsAssistant() throws Exception {
        final NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel.setSound(new Uri.Builder().scheme("test").build(), mAudioAttributes);
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.setShowBadge(true);
        int lockMask = 0;
        for (int i = 0; i < NotificationChannel.LOCKABLE_FIELDS.length; i++) {
            lockMask |= NotificationChannel.LOCKABLE_FIELDS[i];
        }
        channel.lockFields(lockMask);

        mHelper.createNotificationChannel(PKG, UID, channel, true);

        NotificationChannel savedChannel =
                mHelper.getNotificationChannel(PKG, UID, channel.getId(), false);

        assertEquals(channel.getName(), savedChannel.getName());
        assertEquals(channel.shouldShowLights(), savedChannel.shouldShowLights());
        assertFalse(savedChannel.canBypassDnd());
        assertFalse(Notification.VISIBILITY_SECRET == savedChannel.getLockscreenVisibility());
        assertEquals(channel.canShowBadge(), savedChannel.canShowBadge());
    }

    @Test
    public void testClearLockedFields() throws Exception {
        final NotificationChannel channel = getChannel();
        mHelper.clearLockedFields(channel);
        assertEquals(0, channel.getUserLockedFields());

        channel.lockFields(NotificationChannel.USER_LOCKED_PRIORITY
                | NotificationChannel.USER_LOCKED_IMPORTANCE);
        mHelper.clearLockedFields(channel);
        assertEquals(0, channel.getUserLockedFields());
    }

    @Test
    public void testLockFields_soundAndVibration() throws Exception {
        mHelper.createNotificationChannel(PKG, UID, getChannel(), true);

        final NotificationChannel update1 = getChannel();
        update1.setSound(new Uri.Builder().scheme("test").build(),
                new AudioAttributes.Builder().build());
        update1.lockFields(NotificationChannel.USER_LOCKED_PRIORITY); // should be ignored
        mHelper.updateNotificationChannel(PKG, UID, update1);
        assertEquals(NotificationChannel.USER_LOCKED_SOUND,
                mHelper.getNotificationChannel(PKG, UID, update1.getId(), false)
                        .getUserLockedFields());

        NotificationChannel update2 = getChannel();
        update2.enableVibration(true);
        mHelper.updateNotificationChannel(PKG, UID, update2);
        assertEquals(NotificationChannel.USER_LOCKED_SOUND
                        | NotificationChannel.USER_LOCKED_VIBRATION,
                mHelper.getNotificationChannel(PKG, UID, update2.getId(), false)
                        .getUserLockedFields());
    }

    @Test
    public void testLockFields_vibrationAndLights() throws Exception {
        mHelper.createNotificationChannel(PKG, UID, getChannel(), true);

        final NotificationChannel update1 = getChannel();
        update1.setVibrationPattern(new long[]{7945, 46 ,246});
        mHelper.updateNotificationChannel(PKG, UID, update1);
        assertEquals(NotificationChannel.USER_LOCKED_VIBRATION,
                mHelper.getNotificationChannel(PKG, UID, update1.getId(), false)
                        .getUserLockedFields());

        final NotificationChannel update2 = getChannel();
        update2.enableLights(true);
        mHelper.updateNotificationChannel(PKG, UID, update2);
        assertEquals(NotificationChannel.USER_LOCKED_VIBRATION
                        | NotificationChannel.USER_LOCKED_LIGHTS,
                mHelper.getNotificationChannel(PKG, UID, update2.getId(), false)
                        .getUserLockedFields());
    }

    @Test
    public void testLockFields_lightsAndImportance() throws Exception {
        mHelper.createNotificationChannel(PKG, UID, getChannel(), true);

        final NotificationChannel update1 = getChannel();
        update1.setLightColor(Color.GREEN);
        mHelper.updateNotificationChannel(PKG, UID, update1);
        assertEquals(NotificationChannel.USER_LOCKED_LIGHTS,
                mHelper.getNotificationChannel(PKG, UID, update1.getId(), false)
                        .getUserLockedFields());

        final NotificationChannel update2 = getChannel();
        update2.setImportance(IMPORTANCE_DEFAULT);
        mHelper.updateNotificationChannel(PKG, UID, update2);
        assertEquals(NotificationChannel.USER_LOCKED_LIGHTS
                        | NotificationChannel.USER_LOCKED_IMPORTANCE,
                mHelper.getNotificationChannel(PKG, UID, update2.getId(), false)
                        .getUserLockedFields());
    }

    @Test
    public void testLockFields_visibilityAndDndAndBadge() throws Exception {
        mHelper.createNotificationChannel(PKG, UID, getChannel(), true);
        assertEquals(0,
                mHelper.getNotificationChannel(PKG, UID, getChannel().getId(), false)
                        .getUserLockedFields());

        final NotificationChannel update1 = getChannel();
        update1.setBypassDnd(true);
        mHelper.updateNotificationChannel(PKG, UID, update1);
        assertEquals(NotificationChannel.USER_LOCKED_PRIORITY,
                mHelper.getNotificationChannel(PKG, UID, update1.getId(), false)
                        .getUserLockedFields());

        final NotificationChannel update2 = getChannel();
        update2.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mHelper.updateNotificationChannel(PKG, UID, update2);
        assertEquals(NotificationChannel.USER_LOCKED_PRIORITY
                        | NotificationChannel.USER_LOCKED_VISIBILITY,
                mHelper.getNotificationChannel(PKG, UID, update2.getId(), false)
                        .getUserLockedFields());

        final NotificationChannel update3 = getChannel();
        update3.setShowBadge(false);
        mHelper.updateNotificationChannel(PKG, UID, update3);
        assertEquals(NotificationChannel.USER_LOCKED_PRIORITY
                        | NotificationChannel.USER_LOCKED_VISIBILITY
                        | NotificationChannel.USER_LOCKED_SHOW_BADGE,
                mHelper.getNotificationChannel(PKG, UID, update3.getId(), false)
                        .getUserLockedFields());
    }

    @Test
    public void testDeleteNonExistentChannel() throws Exception {
        mHelper.deleteNotificationChannelGroup(PKG, UID, "does not exist");
    }

    @Test
    public void testGetDeletedChannel() throws Exception {
        NotificationChannel channel = getChannel();
        channel.setSound(new Uri.Builder().scheme("test").build(), mAudioAttributes);
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{100, 67, 145, 156});

        mHelper.createNotificationChannel(PKG, UID, channel, true);
        mHelper.deleteNotificationChannel(PKG, UID, channel.getId());

        // Does not return deleted channel
        NotificationChannel response =
                mHelper.getNotificationChannel(PKG, UID, channel.getId(), false);
        assertNull(response);

        // Returns deleted channel
        response = mHelper.getNotificationChannel(PKG, UID, channel.getId(), true);
        compareChannels(channel, response);
        assertTrue(response.isDeleted());
    }

    @Test
    public void testGetDeletedChannels() throws Exception {
        Map<String, NotificationChannel> channelMap = new HashMap<>();
        NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel.setSound(new Uri.Builder().scheme("test").build(), mAudioAttributes);
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{100, 67, 145, 156});
        channelMap.put(channel.getId(), channel);
        NotificationChannel channel2 =
                new NotificationChannel("id4", "a", NotificationManager.IMPORTANCE_HIGH);
        channelMap.put(channel2.getId(), channel2);
        mHelper.createNotificationChannel(PKG, UID, channel, true);
        mHelper.createNotificationChannel(PKG, UID, channel2, true);

        mHelper.deleteNotificationChannel(PKG, UID, channel.getId());

        // Returns only non-deleted channels
        List<NotificationChannel> channels =
                mHelper.getNotificationChannels(PKG, UID, false).getList();
        assertEquals(2, channels.size());   // Default channel + non-deleted channel
        for (NotificationChannel nc : channels) {
            if (!NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                compareChannels(channel2, nc);
            }
        }

        // Returns deleted channels too
        channels = mHelper.getNotificationChannels(PKG, UID, true).getList();
        assertEquals(3, channels.size());               // Includes default channel
        for (NotificationChannel nc : channels) {
            if (!NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                compareChannels(channelMap.get(nc.getId()), nc);
            }
        }
    }

    @Test
    public void testGetDeletedChannelCount() throws Exception {
        NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        NotificationChannel channel2 =
                new NotificationChannel("id4", "a", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel("id4", "a", NotificationManager.IMPORTANCE_HIGH);
        mHelper.createNotificationChannel(PKG, UID, channel, true);
        mHelper.createNotificationChannel(PKG, UID, channel2, true);
        mHelper.createNotificationChannel(PKG, UID, channel3, true);

        mHelper.deleteNotificationChannel(PKG, UID, channel.getId());
        mHelper.deleteNotificationChannel(PKG, UID, channel3.getId());

        assertEquals(2, mHelper.getDeletedChannelCount(PKG, UID));
        assertEquals(0, mHelper.getDeletedChannelCount("pkg2", UID2));
    }

    @Test
    public void testCreateDeletedChannel() throws Exception {
        long[] vibration = new long[]{100, 67, 145, 156};
        NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel.setVibrationPattern(vibration);

        mHelper.createNotificationChannel(PKG, UID, channel, true);
        mHelper.deleteNotificationChannel(PKG, UID, channel.getId());

        NotificationChannel newChannel = new NotificationChannel(
                channel.getId(), channel.getName(), NotificationManager.IMPORTANCE_HIGH);
        newChannel.setVibrationPattern(new long[]{100});

        mHelper.createNotificationChannel(PKG, UID, newChannel, true);

        // No long deleted, using old settings
        compareChannels(channel,
                mHelper.getNotificationChannel(PKG, UID, newChannel.getId(), false));
    }

    @Test
    public void testCreateChannel_defaultChannelId() throws Exception {
        try {
            mHelper.createNotificationChannel(PKG, UID, new NotificationChannel(
                    NotificationChannel.DEFAULT_CHANNEL_ID, "ha", IMPORTANCE_HIGH), true);
            fail("Allowed to create default channel");
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    @Test
    public void testCreateChannel_alreadyExists() throws Exception {
        long[] vibration = new long[]{100, 67, 145, 156};
        NotificationChannel channel =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);
        channel.setVibrationPattern(vibration);

        mHelper.createNotificationChannel(PKG, UID, channel, true);

        NotificationChannel newChannel = new NotificationChannel(
                channel.getId(), channel.getName(), NotificationManager.IMPORTANCE_HIGH);
        newChannel.setVibrationPattern(new long[]{100});

        mHelper.createNotificationChannel(PKG, UID, newChannel, true);

        // Old settings not overridden
        compareChannels(channel,
                mHelper.getNotificationChannel(PKG, UID, newChannel.getId(), false));
    }

    @Test
    public void testCreateChannel_noOverrideSound() throws Exception {
        Uri sound = new Uri.Builder().scheme("test").build();
        final NotificationChannel channel = new NotificationChannel("id2", "name2",
                 NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(sound, mAudioAttributes);
        mHelper.createNotificationChannel(PKG, UID, channel, true);
        assertEquals(sound, mHelper.getNotificationChannel(
                PKG, UID, channel.getId(), false).getSound());
    }

    @Test
    public void testPermanentlyDeleteChannels() throws Exception {
        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel2 =
                new NotificationChannel("id2", "name2", IMPORTANCE_LOW);

        mHelper.createNotificationChannel(PKG, UID, channel1, true);
        mHelper.createNotificationChannel(PKG, UID, channel2, false);

        mHelper.permanentlyDeleteNotificationChannels(PKG, UID);

        // Only default channel remains
        assertEquals(1, mHelper.getNotificationChannels(PKG, UID, true).getList().size());
    }

    @Test
    public void testDeleteGroup() throws Exception {
        NotificationChannelGroup notDeleted = new NotificationChannelGroup("not", "deleted");
        NotificationChannelGroup deleted = new NotificationChannelGroup("totally", "deleted");
        NotificationChannel nonGroupedNonDeletedChannel =
                new NotificationChannel("no group", "so not deleted", IMPORTANCE_HIGH);
        NotificationChannel groupedButNotDeleted =
                new NotificationChannel("not deleted", "belongs to notDeleted", IMPORTANCE_DEFAULT);
        groupedButNotDeleted.setGroup("not");
        NotificationChannel groupedAndDeleted =
                new NotificationChannel("deleted", "belongs to deleted", IMPORTANCE_DEFAULT);
        groupedAndDeleted.setGroup("totally");

        mHelper.createNotificationChannelGroup(PKG, UID, notDeleted, true);
        mHelper.createNotificationChannelGroup(PKG, UID, deleted, true);
        mHelper.createNotificationChannel(PKG, UID, nonGroupedNonDeletedChannel, true);
        mHelper.createNotificationChannel(PKG, UID, groupedAndDeleted, true);
        mHelper.createNotificationChannel(PKG, UID, groupedButNotDeleted, true);

        mHelper.deleteNotificationChannelGroup(PKG, UID, deleted.getId());

        assertNull(mHelper.getNotificationChannelGroup(deleted.getId(), PKG, UID));
        assertNotNull(mHelper.getNotificationChannelGroup(notDeleted.getId(), PKG, UID));

        assertNull(mHelper.getNotificationChannel(PKG, UID, groupedAndDeleted.getId(), false));
        compareChannels(groupedAndDeleted,
                mHelper.getNotificationChannel(PKG, UID, groupedAndDeleted.getId(), true));

        compareChannels(groupedButNotDeleted,
                mHelper.getNotificationChannel(PKG, UID, groupedButNotDeleted.getId(), false));
        compareChannels(nonGroupedNonDeletedChannel, mHelper.getNotificationChannel(
                PKG, UID, nonGroupedNonDeletedChannel.getId(), false));

        // notDeleted
        assertEquals(1, mHelper.getNotificationChannelGroups(PKG, UID).size());
    }

    @Test
    public void testOnUserRemoved() throws Exception {
        int[] user0Uids = {98, 235, 16, 3782};
        int[] user1Uids = new int[user0Uids.length];
        for (int i = 0; i < user0Uids.length; i++) {
            user1Uids[i] = UserHandle.PER_USER_RANGE + user0Uids[i];

            final ApplicationInfo legacy = new ApplicationInfo();
            legacy.targetSdkVersion = Build.VERSION_CODES.N_MR1;
            when(mPm.getApplicationInfoAsUser(eq(PKG), anyInt(), anyInt())).thenReturn(legacy);

            // create records with the default channel for all user 0 and user 1 uids
            mHelper.getImportance(PKG, user0Uids[i]);
            mHelper.getImportance(PKG, user1Uids[i]);
        }

        mHelper.onUserRemoved(1);

        // user 0 records remain
        for (int i = 0; i < user0Uids.length; i++) {
            assertEquals(1,
                    mHelper.getNotificationChannels(PKG, user0Uids[i], false).getList().size());
        }
        // user 1 records are gone
        for (int i = 0; i < user1Uids.length; i++) {
            assertEquals(0,
                    mHelper.getNotificationChannels(PKG, user1Uids[i], false).getList().size());
        }
    }

    @Test
    public void testOnPackageChanged_packageRemoval() throws Exception {
        // Deleted
        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        mHelper.createNotificationChannel(PKG, UID, channel1, true);

        mHelper.onPackagesChanged(true, UserHandle.USER_SYSTEM, new String[]{PKG}, new int[]{UID});

        assertEquals(0, mHelper.getNotificationChannels(PKG, UID, true).getList().size());

        // Not deleted
        mHelper.createNotificationChannel(PKG, UID, channel1, true);

        mHelper.onPackagesChanged(false, UserHandle.USER_SYSTEM, new String[]{PKG}, new int[]{UID});
        assertEquals(2, mHelper.getNotificationChannels(PKG, UID, false).getList().size());
    }

    @Test
    public void testOnPackageChanged_packageRemoval_importance() throws Exception {
        mHelper.setImportance(PKG, UID, NotificationManager.IMPORTANCE_HIGH);

        mHelper.onPackagesChanged(true, UserHandle.USER_SYSTEM, new String[]{PKG}, new int[]{UID});

        assertEquals(NotificationManager.IMPORTANCE_UNSPECIFIED, mHelper.getImportance(PKG, UID));
    }

    @Test
    public void testOnPackageChanged_packageRemoval_groups() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("group1", "name1");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);
        NotificationChannelGroup ncg2 = new NotificationChannelGroup("group2", "name2");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg2, true);

        mHelper.onPackagesChanged(true, UserHandle.USER_SYSTEM, new String[]{PKG}, new int[]{UID});

        assertEquals(0, mHelper.getNotificationChannelGroups(PKG, UID, true).getList().size());
    }

    @Test
    public void testRecordDefaults() throws Exception {
        assertEquals(NotificationManager.IMPORTANCE_UNSPECIFIED, mHelper.getImportance(PKG, UID));
        assertEquals(true, mHelper.canShowBadge(PKG, UID));
        assertEquals(1, mHelper.getNotificationChannels(PKG, UID, false).getList().size());
    }

    @Test
    public void testCreateGroup() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("group1", "name1");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);
        assertEquals(ncg, mHelper.getNotificationChannelGroups(PKG, UID).iterator().next());
    }

    @Test
    public void testCannotCreateChannel_badGroup() throws Exception {
        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        channel1.setGroup("garbage");
        try {
            mHelper.createNotificationChannel(PKG, UID, channel1, true);
            fail("Created a channel with a bad group");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testCannotCreateChannel_goodGroup() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("group1", "name1");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);
        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        channel1.setGroup(ncg.getId());
        mHelper.createNotificationChannel(PKG, UID, channel1, true);

        assertEquals(ncg.getId(),
                mHelper.getNotificationChannel(PKG, UID, channel1.getId(), false).getGroup());
    }

    @Test
    public void testGetChannelGroups() throws Exception {
        NotificationChannelGroup unused = new NotificationChannelGroup("unused", "s");
        mHelper.createNotificationChannelGroup(PKG, UID, unused, true);
        NotificationChannelGroup ncg = new NotificationChannelGroup("group1", "name1");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);
        NotificationChannelGroup ncg2 = new NotificationChannelGroup("group2", "name2");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg2, true);

        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        channel1.setGroup(ncg.getId());
        mHelper.createNotificationChannel(PKG, UID, channel1, true);
        NotificationChannel channel1a =
                new NotificationChannel("id1a", "name1", NotificationManager.IMPORTANCE_HIGH);
        channel1a.setGroup(ncg.getId());
        mHelper.createNotificationChannel(PKG, UID, channel1a, true);

        NotificationChannel channel2 =
                new NotificationChannel("id2", "name1", NotificationManager.IMPORTANCE_HIGH);
        channel2.setGroup(ncg2.getId());
        mHelper.createNotificationChannel(PKG, UID, channel2, true);

        NotificationChannel channel3 =
                new NotificationChannel("id3", "name1", NotificationManager.IMPORTANCE_HIGH);
        mHelper.createNotificationChannel(PKG, UID, channel3, true);

        List<NotificationChannelGroup> actual =
                mHelper.getNotificationChannelGroups(PKG, UID, true).getList();
        assertEquals(3, actual.size());
        for (NotificationChannelGroup group : actual) {
            if (group.getId() == null) {
                assertEquals(2, group.getChannels().size()); // misc channel too
                assertTrue(channel3.getId().equals(group.getChannels().get(0).getId())
                        || channel3.getId().equals(group.getChannels().get(1).getId()));
            } else if (group.getId().equals(ncg.getId())) {
                assertEquals(2, group.getChannels().size());
                if (group.getChannels().get(0).getId().equals(channel1.getId())) {
                    assertTrue(group.getChannels().get(1).getId().equals(channel1a.getId()));
                } else if (group.getChannels().get(0).getId().equals(channel1a.getId())) {
                    assertTrue(group.getChannels().get(1).getId().equals(channel1.getId()));
                } else {
                    fail("expected channel not found");
                }
            } else if (group.getId().equals(ncg2.getId())) {
                assertEquals(1, group.getChannels().size());
                assertEquals(channel2.getId(), group.getChannels().get(0).getId());
            }
        }
    }

    @Test
    public void testGetChannelGroups_noSideEffects() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("group1", "name1");
        mHelper.createNotificationChannelGroup(PKG, UID, ncg, true);

        NotificationChannel channel1 =
                new NotificationChannel("id1", "name1", NotificationManager.IMPORTANCE_HIGH);
        channel1.setGroup(ncg.getId());
        mHelper.createNotificationChannel(PKG, UID, channel1, true);
        mHelper.getNotificationChannelGroups(PKG, UID, true).getList();

        channel1.setImportance(IMPORTANCE_LOW);
        mHelper.updateNotificationChannel(PKG, UID, channel1);

        List<NotificationChannelGroup> actual =
                mHelper.getNotificationChannelGroups(PKG, UID, true).getList();

        assertEquals(2, actual.size());
        for (NotificationChannelGroup group : actual) {
            if (Objects.equals(group.getId(), ncg.getId())) {
                assertEquals(1, group.getChannels().size());
            }
        }
    }

    @Test
    public void testCreateChannel_updateName() throws Exception {
        NotificationChannel nc = new NotificationChannel("id", "hello", IMPORTANCE_DEFAULT);
        mHelper.createNotificationChannel(PKG, UID, nc, true);
        NotificationChannel actual = mHelper.getNotificationChannel(PKG, UID, "id", false);
        assertEquals("hello", actual.getName());

        nc = new NotificationChannel("id", "goodbye", IMPORTANCE_HIGH);
        mHelper.createNotificationChannel(PKG, UID, nc, true);

        actual = mHelper.getNotificationChannel(PKG, UID, "id", false);
        assertEquals("goodbye", actual.getName());
        assertEquals(IMPORTANCE_DEFAULT, actual.getImportance());
    }

    @Test
    public void testDumpChannelsJson() throws Exception {
        final ApplicationInfo upgrade = new ApplicationInfo();
        upgrade.targetSdkVersion = Build.VERSION_CODES.O;
        try {
            when(mPm.getApplicationInfoAsUser(
                    anyString(), anyInt(), anyInt())).thenReturn(upgrade);
        } catch (PackageManager.NameNotFoundException e) {
        }
        ArrayMap<String, Integer> expectedChannels = new ArrayMap<>();
        int numPackages = ThreadLocalRandom.current().nextInt(1, 5);
        for (int i = 0; i < numPackages; i++) {
            String pkgName = "pkg" + i;
            int numChannels = ThreadLocalRandom.current().nextInt(1, 10);
            for (int j = 0; j < numChannels; j++) {
                mHelper.createNotificationChannel(pkgName, UID,
                        new NotificationChannel("" + j, "a", IMPORTANCE_HIGH), true);
            }
            expectedChannels.put(pkgName, numChannels);
        }

        // delete the first channel of the first package
        String pkg = expectedChannels.keyAt(0);
        mHelper.deleteNotificationChannel("pkg" + 0, UID, "0");
        // dump should not include deleted channels
        int count = expectedChannels.get(pkg);
        expectedChannels.put(pkg, count - 1);

        JSONArray actual = mHelper.dumpChannelsJson(new NotificationManagerService.DumpFilter());
        assertEquals(numPackages, actual.length());
        for (int i = 0; i < numPackages; i++) {
            JSONObject object = actual.getJSONObject(i);
            assertTrue(expectedChannels.containsKey(object.get("packageName")));
            assertEquals(expectedChannels.get(object.get("packageName")).intValue(),
                    object.getInt("channelCount"));
        }
    }

    @Test
    public void testBadgingOverrideTrue() throws Exception {
        Secure.putIntForUser(getContext().getContentResolver(),
                Secure.NOTIFICATION_BADGING, 1,
                USER.getIdentifier());
        mHelper.updateBadgingEnabled(); // would be called by settings observer
        assertTrue(mHelper.badgingEnabled(USER));
    }

    @Test
    public void testBadgingOverrideFalse() throws Exception {
        Secure.putIntForUser(getContext().getContentResolver(),
                Secure.NOTIFICATION_BADGING, 0,
                USER.getIdentifier());
        mHelper.updateBadgingEnabled(); // would be called by settings observer
        assertFalse(mHelper.badgingEnabled(USER));
    }

    @Test
    public void testBadgingOverrideUserIsolation() throws Exception {
        Secure.putIntForUser(getContext().getContentResolver(),
                Secure.NOTIFICATION_BADGING, 0,
                USER.getIdentifier());
        Secure.putIntForUser(getContext().getContentResolver(),
                Secure.NOTIFICATION_BADGING, 1,
                USER2.getIdentifier());
        mHelper.updateBadgingEnabled(); // would be called by settings observer
        assertFalse(mHelper.badgingEnabled(USER));
        assertTrue(mHelper.badgingEnabled(USER2));
    }

}
