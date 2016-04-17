// 
// DS: Decompiled by Procyon v0.5.30
// 

package com.android.internal.telephony;

public class RadioCapability
{
    private static final int RADIO_CAPABILITY_VERSION = 1;
    public static final int RC_PHASE_APPLY = 2;
    public static final int RC_PHASE_CONFIGURED = 0;
    public static final int RC_PHASE_FINISH = 4;
    public static final int RC_PHASE_START = 1;
    public static final int RC_PHASE_UNSOL_RSP = 3;
    public static final int RC_STATUS_FAIL = 2;
    public static final int RC_STATUS_NONE = 0;
    public static final int RC_STATUS_SUCCESS = 1;
    private String mLogicalModemUuid;
    private int mPhase;
    private int mPhoneId;
    private int mRadioAccessFamily;
    private int mSession;
    private int mStatus;
    
    public RadioCapability(int phoneId, int session, int phase, int radioAccessFamily, String logicalModemUuid, int status) {
        mPhoneId = phoneId;
        mSession = session;
        mPhase = phase;
        mRadioAccessFamily = radioAccessFamily;
        mLogicalModemUuid = logicalModemUuid;
        mStatus = status;
    }
    
    public String getLogicalModemUuid() {
        return mLogicalModemUuid;
    }
    
    public int getPhase() {
        return mPhase;
    }
    
    public int getPhoneId() {
        return mPhoneId;
    }
    
    public int getRadioAccessFamily() {
        return mRadioAccessFamily;
    }
    
    public int getSession() {
        return mSession;
    }
    
    public int getStatus() {
        return mStatus;
    }
    
    public int getVersion() {
        return RADIO_CAPABILITY_VERSION;
    }
    
    @Override
    public String toString() {
        return "{mPhoneId = " + mPhoneId + " mVersion=" + getVersion() + " mSession=" + getSession() +
               " mPhase=" + getPhase() + " mRadioAccessFamily=" + this.getRadioAccessFamily() +
               " mLogicModemId=" + this.getLogicalModemUuid() + " mStatus=" + getStatus() + "}";
    }
}
