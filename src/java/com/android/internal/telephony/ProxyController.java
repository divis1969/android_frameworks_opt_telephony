/*
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyController {
    static final String LOG_TAG = "ProxyController";

    //***** Class Variables
    private static ProxyController sProxyController;

    private PhoneProxy[] mProxyPhones;

    private UiccController mUiccController;

    private CommandsInterface[] mCi;

    private Context mContext;

    private DctController mDctController;

    //UiccPhoneBookController to use proper IccPhoneBookInterfaceManagerProxy object
    private UiccPhoneBookController mUiccPhoneBookController;

    //PhoneSubInfoController to use proper PhoneSubInfoProxy object
    private PhoneSubInfoController mPhoneSubInfoController;

    //UiccSmsController to use proper IccSmsInterfaceManager object
    private UiccSmsController mUiccSmsController;

    // DS: MTK
    private static final int EVENT_NOTIFICATION_RC_CHANGED = 1;
    private static final int EVENT_SET_PHONE_RAT_FAMILY_RESPONSE = 2;
    private static final int EVENT_APPLY_RC_RESPONSE = 3;
    private static final int EVENT_FINISH_RC_RESPONSE = 4;
    private static final int EVENT_PHONE_RAT_FAMILY_CHANGED_NOTIFY = 1;
    private static final int EVENT_START_RC_RESPONSE = 2;
    private static final int SET_PHONE_RAT_FAMILY_STATUS_IDLE = 0;
    private static final int SET_PHONE_RAT_FAMILY_STATUS_CHANGING = 1;
    private static final int SET_PHONE_RAT_FAMILY_STATUS_DONE = 2;
    private static final int SET_RC_STATUS_IDLE = 0;
    private static final int SET_RC_STATUS_STARTING = 1;
    private static final int SET_RC_STATUS_STARTED = 2;
    private static final int SET_RC_STATUS_APPLYING = 3;
    private static final int SET_RC_STATUS_SUCCESS = 4;
    private static final int SET_RC_STATUS_FAIL = 5;
    private static final int SET_RC_TIMEOUT_WAITING_MSEC = 45000;
    private Handler mHandler;
    private int[] mSetRadioAccessFamilyStatus;
    private String[] mLogicalModemIds;
    private int[] mNewRadioAccessFamily;
    private int[] mOldRadioAccessFamily;
    private int mRadioCapabilitySessionId;
    RadioCapabilityRunnable mSetRadioCapabilityRunnable;
    private AtomicInteger mUniqueIdGenerator;
    private boolean mIsCapSwitching;
    PowerManager.WakeLock mWakeLock;
    private int mRadioAccessFamilyStatusCounter;

    //***** Class Methods
    public static ProxyController getInstance(Context context, PhoneProxy[] phoneProxy,
            UiccController uiccController, CommandsInterface[] ci) {
        if (sProxyController == null) {
            sProxyController = new ProxyController(context, phoneProxy, uiccController, ci);
        }
        return sProxyController;
    }

    static public ProxyController getInstance() {
        return sProxyController;
    }

    private ProxyController(Context context, PhoneProxy[] phoneProxy, UiccController uiccController,
            CommandsInterface[] ci) {
        logd("Constructor - Enter");

        mContext = context;
        mProxyPhones = phoneProxy;
        mUiccController = uiccController;
        mCi = ci;

        HandlerThread t = new HandlerThread("DctControllerThread");
        t.start();

        // DS: MTK
        mUniqueIdGenerator = new AtomicInteger(new Random().nextInt());
        mHandler = new Handler() {
            public void handleMessage(Message message) {
                logd("handleMessage msg.what=" + message.what);
                switch (message.what) {
                    case EVENT_START_RC_RESPONSE: {
                        onStartRadioCapabilityResponse(message);
                        break;
                    }
                    case EVENT_APPLY_RC_RESPONSE: {
                        onApplyRadioCapabilityResponse(message);
                        break;
                    }
                    case EVENT_NOTIFICATION_RC_CHANGED: {
                        onNotificationRadioCapabilityChanged(message);
                        break;
                    }
                    case EVENT_FINISH_RC_RESPONSE: {
                        onFinishRadioCapabilityResponse(message);
                        break;
                    }
                }
            }
        };

        mDctController = DctController.makeDctController((PhoneProxy[])phoneProxy, t.getLooper());
        mUiccPhoneBookController = new UiccPhoneBookController(mProxyPhones);
        mPhoneSubInfoController = new PhoneSubInfoController(mProxyPhones);
        mUiccSmsController = new UiccSmsController(mProxyPhones, context);

        mSetRadioAccessFamilyStatus = new int[mProxyPhones.length];
        mNewRadioAccessFamily = new int[mProxyPhones.length];
        mOldRadioAccessFamily = new int[mProxyPhones.length];
        mLogicalModemIds = new String[mProxyPhones.length];
        for (int i = 0; i < mProxyPhones.length; ++i) {
            mLogicalModemIds[i] = Integer.toString(i);
        }
        mSetRadioCapabilityRunnable = new RadioCapabilityRunnable();
        mWakeLock = ((PowerManager)mContext.getSystemService("power")).newWakeLock(1, "ProxyController");
        mWakeLock.setReferenceCounted(false);
        clearTransaction();
        for (int j = 0; j < mProxyPhones.length; ++j) {
            mProxyPhones[j].registerForRadioCapabilityChanged(mHandler, 1, null);
        }

        logd("Constructor - Exit");
    }

    public void updateDataConnectionTracker(int sub) {
        mProxyPhones[sub].updateDataConnectionTracker();
    }

    public void enableDataConnectivity(int sub) {
        mProxyPhones[sub].setInternalDataEnabled(true);
    }

    public void disableDataConnectivity(int sub,
            Message dataCleanedUpMsg) {
        mProxyPhones[sub].setInternalDataEnabled(false, dataCleanedUpMsg);
    }

    public void updateCurrentCarrierInProvider(int sub) {
        mProxyPhones[sub].updateCurrentCarrierInProvider();
    }

    public void registerForAllDataDisconnected(int subId, Handler h, int what, Object obj) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);

        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            ((PhoneProxy) mProxyPhones[phoneId]).registerForAllDataDisconnected(h, what, obj);
        }
    }

    public void unregisterForAllDataDisconnected(int subId, Handler h) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);

        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            ((PhoneProxy) mProxyPhones[phoneId]).unregisterForAllDataDisconnected(h);
        }
    }

    public boolean isDataDisconnected(int subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);

        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            Phone activePhone = ((PhoneProxy) mProxyPhones[phoneId]).getActivePhone();
            return ((PhoneBase) activePhone).mDcTracker.isDisconnected();
        } else {
            return false;
        }
    }

    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        try {
            mDctController.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // DS: MTK
    private class RadioCapabilityRunnable implements Runnable
    {
        private int mSessionId;

        @Override
        public void run() {
            if (mSessionId != mRadioCapabilitySessionId) {
                logd("RadioCapability timeout: Ignore mSessionId=" + mSessionId + "!= mRadioCapabilitySessionId=" + mRadioCapabilitySessionId);
            }
            else {
                synchronized (mSetRadioAccessFamilyStatus) {
                    int i = 0;
                    try {
                        while (i < mProxyPhones.length) {
                            logd("RadioCapability timeout: mSetRadioAccessFamilyStatus[" + i + "]=" + mSetRadioAccessFamilyStatus[i]);
                            ++i;
                        }
                        issueFinish(2, mUniqueIdGenerator.getAndIncrement());
                        completeRadioCapabilityTransaction();
                    }
                    finally {
                    }
                }
            }
        }

        public void setTimeoutState(int sessionId) {
            mSessionId = sessionId;
        }
    }

    public boolean isCapabilitySwitching() {
        return mIsCapSwitching;
    }

    public boolean setRadioCapability(final RadioAccessFamily[] p0) {
        //
        // This method could not be decompiled.
        //
        logd("setRadioCapability is not implemented");
        return false;
    }

    private boolean checkAllRadioCapabilitySuccess() {
        synchronized (mSetRadioAccessFamilyStatus) {
            int i = 0;
            try {
                while (i < this.mProxyPhones.length) {
                    if (this.mSetRadioAccessFamilyStatus[i] == 5) {
                        return false;
                    }
                    ++i;
                }
                return true;
            }
            finally {
            }
        }
    }

    private void clearTransaction() {
        logd("clearTransaction");
        mIsCapSwitching = false;
        synchronized (mSetRadioAccessFamilyStatus) {
            int i = 0;
            try {
                while (i < this.mProxyPhones.length) {
                    this.logd("clearTransaction: phoneId=" + i + " status=IDLE");
                    this.mSetRadioAccessFamilyStatus[i] = 0;
                    this.mOldRadioAccessFamily[i] = 0;
                    this.mNewRadioAccessFamily[i] = 0;
                    ++i;
                }
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
            }
            finally {
            }
        }
    }

    private void completeRadioCapabilityTransaction() {
        boolean checkAllRadioCapabilitySuccess = checkAllRadioCapabilitySuccess();
        logd("onFinishRadioCapabilityResponse: success=" + checkAllRadioCapabilitySuccess);
        Intent intent;
        if (checkAllRadioCapabilitySuccess) {
            ArrayList<RadioAccessFamily> list = new ArrayList<RadioAccessFamily>();
            for (int i = 0; i < mProxyPhones.length; ++i) {
                int radioAccessFamily = mProxyPhones[i].getRadioAccessFamily();
                logd("radioAccessFamily[" + i + "]=" + radioAccessFamily);
                list.add(new RadioAccessFamily(i, radioAccessFamily));
            }
            intent = new Intent("android.intent.action.ACTION_SET_RADIO_CAPABILITY_DONE");
            intent.putParcelableArrayListExtra("rafs", list);
        }
        else {
            intent = new Intent("android.intent.action.ACTION_SET_RADIO_CAPABILITY_FAILED");
        }
        RadioCapabilitySwitchUtil.updateIccid(this.mProxyPhones);
        clearTransaction();
        mContext.sendBroadcast(intent);
    }

    private void sendRadioCapabilityRequest(int phoneId, int session, int phase, int rat, String uuid, int status, int msg) {
        mProxyPhones[phoneId].setRadioCapability(new RadioCapability(phoneId, session, phase, rat, uuid, status), 
                mHandler.obtainMessage(msg));
    }

    private void issueFinish(int n, int session) {
        synchronized (mSetRadioAccessFamilyStatus) {
            int i = 0;
            try {
                while (i < this.mProxyPhones.length) {
                    if (mSetRadioAccessFamilyStatus[i] != SET_RC_STATUS_FAIL) {
                        logd("issueFinish: phoneId=" + i + " sessionId=" + session + " status=" + n);
                        sendRadioCapabilityRequest(i, session, 4, mOldRadioAccessFamily[i], mLogicalModemIds[i], n, EVENT_FINISH_RC_RESPONSE);
                        if (n == 2) {
                            logd("issueFinish: phoneId: " + i + " status: FAIL");
                            mSetRadioAccessFamilyStatus[i] = SET_RC_STATUS_FAIL;
                        }
                    }
                    else {
                        this.logd("issueFinish: Ignore already FAIL, Phone" + i + " sessionId=" + session + " status=" + n);
                    }
                    ++i;
                }
            }
            finally {
            }
        }
    }

    private void resetRadioAccessFamilyStatusCounter() {
        mRadioAccessFamilyStatusCounter = mProxyPhones.length;
    }

    private void onNotificationRadioCapabilityChanged(Message message) {
        final RadioCapability radioCapability = (RadioCapability)((AsyncResult)message.obj).result;
        if (radioCapability == null || radioCapability.getSession() != mRadioCapabilitySessionId) {
            logd("onNotificationRadioCapabilityChanged: Ignore session=" + mRadioCapabilitySessionId + " rc=" + radioCapability);
        }
        else {
            synchronized (mSetRadioAccessFamilyStatus) {
                logd("onNotificationRadioCapabilityChanged: rc=" + radioCapability);
                if (radioCapability.getSession() != mRadioCapabilitySessionId) {
                    logd("onNotificationRadioCapabilityChanged: Ignore session=" + mRadioCapabilitySessionId + " rc=" + radioCapability);
                    return;
                }
            }
            int phoneId = radioCapability.getPhoneId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && phoneId == SubscriptionManager.LTE_DC_PHONE_ID) {
                phoneId = 0;
            }
            if (((AsyncResult)message.obj).exception != null || radioCapability.getStatus() == 2) {
                logd("onNotificationRadioCapabilityChanged: phoneId=" + phoneId + " status=FAIL");
                mSetRadioAccessFamilyStatus[phoneId] = SET_RC_STATUS_FAIL;
            }
            else {
                logd("onNotificationRadioCapabilityChanged: phoneId=" + phoneId + " status=SUCCESS");
                mSetRadioAccessFamilyStatus[phoneId] = SET_RC_STATUS_SUCCESS;
                mProxyPhones[phoneId].setRadioAccessFamily(radioCapability.getRadioAccessFamily());
            }
            --mRadioAccessFamilyStatusCounter;
            if (mRadioAccessFamilyStatusCounter == 0) {
                logd("onNotificationRadioCapabilityChanged: removing callback from handler");
                mHandler.removeCallbacks((Runnable)mSetRadioCapabilityRunnable);
                resetRadioAccessFamilyStatusCounter();
                boolean checkAllRadioCapabilitySuccess = checkAllRadioCapabilitySuccess();
                logd("onNotificationRadioCapabilityChanged: APPLY URC success=" + checkAllRadioCapabilitySuccess);
                int n2;
                if (checkAllRadioCapabilitySuccess) {
                    n2 = 1;
                }
                else {
                    n2 = 2;
                }
                issueFinish(n2, mRadioCapabilitySessionId);
            }
        }
    }

    private void onStartRadioCapabilityResponse(Message message) {
        synchronized (mSetRadioAccessFamilyStatus) {
            int i = 0;
            RadioCapability radioCapability = (RadioCapability)((AsyncResult)message.obj).result;
            if (radioCapability == null || radioCapability.getSession() != mRadioCapabilitySessionId) {
                logd("onStartRadioCapabilityResponse: Ignore session=" + mRadioCapabilitySessionId + " rc=" + radioCapability);
            }
            else {
                --mRadioAccessFamilyStatusCounter;
                int n = i = radioCapability.getPhoneId();
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && (i = n) == SubscriptionManager.LTE_DC_PHONE_ID) {
                    i = 0;
                }
                if (((AsyncResult)message.obj).exception != null) {
                    logd("onStartRadioCapabilityResponse: Error response session=" + radioCapability.getSession());
                    logd("onStartRadioCapabilityResponse: phoneId=" + i + " status=FAIL");
                    mSetRadioAccessFamilyStatus[i] = SET_RC_STATUS_FAIL;
                } else {
                    logd("onStartRadioCapabilityResponse: phoneId=" + i + " status=STARTED");
                    mSetRadioAccessFamilyStatus[i] = SET_RC_STATUS_STARTED;
                }
                if (mRadioAccessFamilyStatusCounter == 0) {
                    resetRadioAccessFamilyStatusCounter();
                    boolean checkAllRadioCapabilitySuccess = checkAllRadioCapabilitySuccess();
                    logd("onStartRadioCapabilityResponse: success=" + checkAllRadioCapabilitySuccess);
                    if (checkAllRadioCapabilitySuccess) {
                        for (int j = 0; j < mProxyPhones.length; ++j) {
                            sendRadioCapabilityRequest(j, mRadioCapabilitySessionId, 2, mNewRadioAccessFamily[j], mLogicalModemIds[j], 0, EVENT_APPLY_RC_RESPONSE);
                            logd("onStartRadioCapabilityResponse: phoneId=" + j + " status=APPLYING");
                            mSetRadioAccessFamilyStatus[j] = SET_RC_STATUS_APPLYING;
                        }
                    } else {
                        issueFinish(2, mRadioCapabilitySessionId);
                    }
                }
            }
        }
    }

    private void onApplyRadioCapabilityResponse(Message message) {
        RadioCapability radioCapability = (RadioCapability)((AsyncResult)message.obj).result;
        if (radioCapability == null || radioCapability.getSession() != mRadioCapabilitySessionId) {
            logd("onApplyRadioCapabilityResponse: Ignore session=" + mRadioCapabilitySessionId + " rc=" + radioCapability);
        }
        else {
            logd("onApplyRadioCapabilityResponse: rc=" + radioCapability);
            if (((AsyncResult)message.obj).exception != null) {
                synchronized (mSetRadioAccessFamilyStatus) {
                    logd("onApplyRadioCapabilityResponse: Error response session=" + radioCapability.getSession());
                    int phoneId = radioCapability.getPhoneId();
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && phoneId == SubscriptionManager.LTE_DC_PHONE_ID) {
                        phoneId = 0;
                    }
                    logd("onApplyRadioCapabilityResponse: phoneId=" + phoneId + " status=FAIL");
                    mSetRadioAccessFamilyStatus[phoneId] = SET_RC_STATUS_FAIL;
                    return;
                }
            }
            logd("onApplyRadioCapabilityResponse: Valid start expecting notification rc=" + radioCapability);
        }
    }

    void onFinishRadioCapabilityResponse(Message message) {
        RadioCapability radioCapability = (RadioCapability)((AsyncResult)message.obj).result;
        if (radioCapability == null || radioCapability.getSession() != mRadioCapabilitySessionId) {
            logd("onFinishRadioCapabilityResponse: Ignore session=" + mRadioCapabilitySessionId + " rc=" + radioCapability);
        }
        else {
            synchronized (mSetRadioAccessFamilyStatus) {
                logd(" onFinishRadioCapabilityResponse mRadioAccessFamilyStatusCounter=" + mRadioAccessFamilyStatusCounter);
                --mRadioAccessFamilyStatusCounter;
                if (mRadioAccessFamilyStatusCounter == 0) {
                    completeRadioCapabilityTransaction();
                }
            }
        }
    }

}
