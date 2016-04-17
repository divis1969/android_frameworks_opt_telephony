// 
// DS: Decompiled by Procyon v0.5.30
// 

package com.mediatek.internal.telephony.cdma;

import android.os.SystemProperties;

public class CdmaFeatureOptionUtils
{
    public static final String EVDO_DT_SUPPORT = "ril.evdo.dtsupport";
    public static final String MTK_C2K_SUPPORT = "ro.mtk_c2k_support";
    public static final String MTK_IRAT_SUPPORT = "ro.c2k.irat.support";
    public static final String MTK_MD_IRAT_SUPPORT = "ro.c2k.md.irat.support";
    public static final String MTK_SVLTE_SUPPORT = "ro.mtk_svlte_support";
    public static final String SUPPORT_YES = "1";
    
    public static int getExternalModemSlot() {
        return SystemProperties.getInt("ril.external.md", 0) - 1;
    }
    
    public static boolean isCdmaApIratSupport() {
        return isCdmaIratSupport() && !isCdmaMdIratSupport();
    }
    
    public static boolean isCdmaIratSupport() {
        return SystemProperties.get(MTK_IRAT_SUPPORT).equals("1");
    }
    
    public static boolean isCdmaLteDcSupport() {
        return SystemProperties.get(MTK_SVLTE_SUPPORT).equals("1");
    }
    
    public static boolean isCdmaMdIratSupport() {
        return SystemProperties.get(MTK_MD_IRAT_SUPPORT).equals("1");
    }
    
    public static boolean isEvdoDTSupport() {
        return SystemProperties.get(EVDO_DT_SUPPORT).equals("1");
    }
    
    public static boolean isMtkC2KSupport() {
        return SystemProperties.get(MTK_C2K_SUPPORT).equals("1");
    }
}
