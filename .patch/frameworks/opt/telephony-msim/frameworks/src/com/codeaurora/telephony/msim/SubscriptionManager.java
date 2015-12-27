/*
 * Copyright (c) 2010-2013, The Linux Foundation. All rights reserved.
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
 *
 */

package com.codeaurora.telephony.msim;

import java.util.HashMap;
import java.util.regex.PatternSyntaxException;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.MSimConstants.CardUnavailableReason;
import com.android.internal.telephony.RIL;

import com.codeaurora.telephony.msim.Subscription.SubscriptionStatus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;
import android.text.TextUtils;


/**
 * Keep track of all the subscription related informations.
 * Includes:
 *  - User Preferred Subscriptions
 *  - Current Active Subscriptions
 *  - Subscription Readiness - based on the UNSOL SUB STATUS
 *  - Current DDS
 *  - SetSubscription Mode to be set only once
 * Provides the functionalities
 *  - Activate or deactivate Subscriptions
 *  - Set DDS (Designated Data Subscription)
 * Handles
 *  - UNSOL SUB STATUS changes from modem
 */
public class SubscriptionManager extends Handler {
    static final String LOG_TAG = "SubscriptionManager";

    private class SetUiccSubsParams {
        public SetUiccSubsParams(int sub, SubscriptionStatus status, String appIndexType,
                int app3gppIndexId,int app3gpp2IndexId, String app3gppStatus,
                String app3gpp2Status) {
            subId = sub;
            subStatus = status;
            appType = appIndexType;
            app3gppId = app3gppIndexId;
            app3gpp2Id = app3gpp2IndexId;
            app3gppAppStatus = app3gppStatus;
            app3gpp2AppStatus = app3gpp2Status;
        }
        public int subId;      // sub id
        public SubscriptionStatus subStatus;  // Activation status - activate or deactivate?
        public String appType; // App type
        public int app3gppId; // 3gpp App id
        public int app3gpp2Id; // 3gpp2 App id
        public String app3gppAppStatus;
        public String app3gpp2AppStatus;
    }
    /**
     * Class to maintain the current subscription info in SubscriptionManager.
     */
    private class PhoneSubscriptionInfo {
        public Subscription sub;  // subscription
        public boolean subReady;  // subscription readiness
        //public SubscriptionStatus subStatus;  // status
        public String cause;   // Set in case of subStatus is DEACTIVATED

        PhoneSubscriptionInfo() {
            sub = new Subscription();
            subReady = false;
            cause = null;
        }
    }

    //***** Class Variables
    private static SubscriptionManager sSubscriptionManager;
    private int mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();

    public static int NUM_SUBSCRIPTIONS = MSimTelephonyManager.getDefault().getPhoneCount();

    // Number of fields in the user preferred subscription property
    private static int USER_PREF_SUB_FIELDS = 6;

    //***** Events
    private static final int EVENT_CARD_INFO_AVAILABLE = 0;
    private static final int EVENT_CARD_INFO_NOT_AVAILABLE = 1;
    private static final int EVENT_ALL_CARD_INFO_AVAILABLE = 2;
    private static final int EVENT_SET_UICC_SUBSCRIPTION_DONE = 3;
    private static final int EVENT_SUBSCRIPTION_STATUS_CHANGED = 4;
    private static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 5;
    private static final int EVENT_CLEANUP_DATA_CONNECTION_DONE = 6;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 7;
    private static final int EVENT_RADIO_ON = 8;
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 9;
    private static final int EVENT_PROCESS_AVAILABLE_CARDS = 10;
    private static final int EVENT_SET_PRIORITY_SUBSCRIPTION_DONE = 11;
    private static final int EVENT_SET_DEFAULT_VOICE_SUBSCRIPTION_DONE = 12;
    private static final int EVENT_SHUTDOWN_ACTION_RECEIVED = 13;

    // Set Subscription Return status
    public static final String SUB_ACTIVATE_SUCCESS = "ACTIVATE SUCCESS";
    public static final String SUB_ACTIVATE_FAILED = "ACTIVATE FAILED";
    public static final String SUB_GLOBAL_ACTIVATE_FAILED = "GLOBAL ACTIVATE FAILED";
    public static final String SUB_GLOBAL_DEACTIVATE_FAILED = "GLOBAL DEACTIVATE FAILED";
    public static final String SUB_ACTIVATE_NOT_SUPPORTED = "ACTIVATE NOT SUPPORTED";
    public static final String SUB_DEACTIVATE_SUCCESS = "DEACTIVATE SUCCESS";
    public static final String SUB_DEACTIVATE_FAILED = "DEACTIVATE FAILED";
    public static final String SUB_DEACTIVATE_NOT_SUPPORTED = "DEACTIVATE NOT SUPPORTED";
    public static final String SUB_NOT_CHANGED = "NO CHANGE IN SUBSCRIPTION";

    // Sub status from RIL
    private static final int SUB_STATUS_DEACTIVATED = 0;
    private static final int SUB_STATUS_ACTIVATED = 1;

    private RegistrantList[] mSubDeactivatedRegistrants;
    private RegistrantList[] mSubActivatedRegistrants;
    private RegistrantList mDdsSwitchRegistrants;

    private Context mContext;
    private CommandsInterface[] mCi;

    // The User preferred subscription information
    private SubscriptionData mUserPrefSubs = null;
    private CardSubscriptionManager mCardSubMgr;

    private boolean[] mCardInfoAvailable = new boolean[mNumPhones];
    private boolean[] mIsNewCard = new boolean[mNumPhones];
	private boolean[] mRadioOn = new boolean[mNumPhones];

    private HashMap<SubscriptionId, Subscription> mActivatePending;
    private HashMap<SubscriptionId, Subscription> mDeactivatePending;

    private HashMap<SubscriptionId, PhoneSubscriptionInfo> mCurrentSubscriptions;

    private boolean mAllCardsStatusAvailable = false;

    private boolean mSetDdsRequired = true;

    private int mCurrentDds;
    private int mQueuedDds;
    private int mCurrentAppId;
    private boolean mDisableDdsInProgress = false;

    private boolean mSetSubscriptionInProgress = false;

    //private MSimProxyManager mMSimProxyManager;

    private boolean mDataActive = false;

    private Message mSetDdsCompleteMsg;

    private RegistrantList mSetSubscriptionRegistrants = new RegistrantList();

    private String[] mSubResult = new String [NUM_SUBSCRIPTIONS];

    /**
     * Subscription Id
     */
    private enum SubscriptionId {
        SUB0,
        SUB1,
        SUB2
    }

    private boolean mIsShutDownInProgress = false;

    private BroadcastReceiver mShutDownReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SHUTDOWN.equals(intent.getAction()) &&
                        !intent.getBooleanExtra(Intent.EXTRA_SHUTDOWN_USERSPACE_ONLY, false)) {
                    logd("ACTION_SHUTDOWN Received");
                    sendEmptyMessage(EVENT_SHUTDOWN_ACTION_RECEIVED);
                }
            }
        };

    /**
     * Get singleton instance of SubscriptionManager.
     * @param context
     * @param uiccMgr
     * @param ci
     * @return
     */
    public static SubscriptionManager getInstance(Context context,
            MSimUiccController uiccController, CommandsInterface[] ci)
    {
        Rlog.d(LOG_TAG, "getInstance");
        if (sSubscriptionManager == null) {
            sSubscriptionManager = new SubscriptionManager(context, uiccController, ci);
        }
        return sSubscriptionManager;
    }

    /**
     * Get singleton instance of SubscriptionManager.
     * @return
     */
    public static SubscriptionManager getInstance() {
        return sSubscriptionManager;
    }

    /**
     * Constructor.
     * @param context
     * @param uiccManager
     * @param ci
     */
    private SubscriptionManager(Context context, MSimUiccController uiccController,
            CommandsInterface[] ci) {
        logd("Constructor - Enter");

        mContext = context;

        // Read the user preferred subscriptions from the system property
        getUserPreferredSubs();

        mCardSubMgr = CardSubscriptionManager.getInstance(context, uiccController, ci);
        for (int i=0; i < mNumPhones; i++) {
            mCardSubMgr.registerForCardInfoAvailable(i, this,
                    EVENT_CARD_INFO_AVAILABLE, new Integer(i));
            mCardSubMgr.registerForCardInfoUnavailable(i, this,
                    EVENT_CARD_INFO_NOT_AVAILABLE, new Integer(i));
        }
        mCardSubMgr.registerForAllCardsInfoAvailable(this, EVENT_ALL_CARD_INFO_AVAILABLE, null);

        mCi = ci;
        for (int i = 0; i < mCi.length; i++) {
            mCi[i].registerForSubscriptionStatusChanged(this,
                    EVENT_SUBSCRIPTION_STATUS_CHANGED, new Integer(i));
            mCi[i].registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE,
                    new Integer(i));
            mCi[i].registerForOn(this, EVENT_RADIO_ON, new Integer(i));

            mCardInfoAvailable[i] = false;
            mIsNewCard[i] = false;
			mRadioOn[i] = false;
        }

        mSubDeactivatedRegistrants = new RegistrantList[mNumPhones];
        mSubActivatedRegistrants = new RegistrantList[mNumPhones];
        mDdsSwitchRegistrants = new RegistrantList();
        for (int i = 0; i < mNumPhones; i++) {
            mSubDeactivatedRegistrants[i] = new RegistrantList();
            mSubActivatedRegistrants[i] = new RegistrantList();
        }
        mActivatePending = new HashMap<SubscriptionId, Subscription>();
        mDeactivatePending = new HashMap<SubscriptionId, Subscription>();
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            mActivatePending.put(sub, null);
            mDeactivatePending.put(sub, null);
        }

        //mMSimProxyManager = MSimProxyManager.getInstance();
        // Get the current active dds and default dds settings.
        mCurrentDds =  MSimPhoneFactory.getDataSubscription();
        int defaultDds =  MSimPhoneFactory.getDefaultDataSubscription();
        logd("In MSimProxyManager constructor current active dds is:" + mCurrentDds
                +  " default Dds = " + defaultDds);
        if (mCurrentDds != defaultDds) {
            /* There was a temporary dds switch and phone power cycled or phone process
               restarted before the dds was switched back to original setting.
               Switch back to default dds. */
            MSimPhoneFactory.setDataSubscription(defaultDds);
            mCurrentDds = defaultDds;
        }

        mCurrentSubscriptions = new HashMap<SubscriptionId, PhoneSubscriptionInfo>();
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            mCurrentSubscriptions.put(sub, new PhoneSubscriptionInfo());
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_SHUTDOWN);
        mContext.registerReceiver(mShutDownReceiver, filter);
        logd("Constructor - Exit");
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        Integer subId;
        switch(msg.what) {
            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                ar = (AsyncResult)msg.obj;
                subId = (Integer)ar.userObj;
                logd("EVENT_RADIO_OFF_OR_NOT_AVAILABLE on SUB: " + subId);
                 mRadioOn[subId] = false;
                if (!isAllRadioOn()) {
                    mSetSubscriptionInProgress = false;
                    mSetDdsRequired = true;
                }
                break;

            case EVENT_RADIO_ON:
                ar = (AsyncResult)msg.obj;
                subId = (Integer)ar.userObj;
                logd("EVENT_RADIO_ON on SUB: " + subId);
                 mRadioOn[subId] = true;
                if (isAllRadioOn()) {
                   sendDefaultSubsInfo();
                }
                break;

            case EVENT_CARD_INFO_AVAILABLE:
                logd("EVENT_CARD_INFO_AVAILABLE");
                processCardInfoAvailable((AsyncResult)msg.obj);
                break;

            case EVENT_CARD_INFO_NOT_AVAILABLE:
                logd("EVENT_CARD_INFO_NOT_AVAILABLE");
                processCardInfoNotAvailable((AsyncResult)msg.obj);
                break;

            case EVENT_ALL_CARD_INFO_AVAILABLE:
                logd("EVENT_ALL_CARD_INFO_AVAILABLE");
                processAllCardsInfoAvailable();
                break;

            case EVENT_SET_UICC_SUBSCRIPTION_DONE:
                logd("EVENT_SET_UICC_SUBSCRIPTION_DONE");
                processSetUiccSubscriptionDone((AsyncResult)msg.obj);
                break;

            case EVENT_SUBSCRIPTION_STATUS_CHANGED:
                logd("EVENT_SUBSCRIPTION_STATUS_CHANGED");
                processSubscriptionStatusChanged((AsyncResult)msg.obj);
                break;

            case EVENT_CLEANUP_DATA_CONNECTION_DONE:
                logd("EVENT_CLEANUP_DATA_CONNECTION_DONE");
                processCleanupDataConnectionDone((Integer)msg.obj);
                break;

            case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                logd("EVENT_SET_DATA_SUBSCRIPTION_DONE");
                processSetDataSubscriptionDone((AsyncResult)msg.obj);
                break;

            case EVENT_ALL_DATA_DISCONNECTED:
                Rlog.d(LOG_TAG, "EVENT_ALL_DATA_DISCONNECTED");
                processAllDataDisconnected((AsyncResult)msg.obj);
                break;

            case EVENT_PROCESS_AVAILABLE_CARDS:
                Rlog.d(LOG_TAG, "EVENT_PROCESS_AVAILABLE_CARDS");
                processAvailableCards();
                break;

            case EVENT_SET_PRIORITY_SUBSCRIPTION_DONE:
                Rlog.d(LOG_TAG, "EVENT_SET_PRIORITY_SUBSCRIPTION_DONE");
                processSetPrioritySubscriptionDone((AsyncResult)msg.obj);
                break;
            case EVENT_SET_DEFAULT_VOICE_SUBSCRIPTION_DONE:
                Rlog.d(LOG_TAG, "EVENT_SET_DEFAULT_VOICE_SUBSCRIPTION_DONE");
                processSetDefaultVoiceSubscriptionDone((AsyncResult)msg.obj);
                break;

            case EVENT_SHUTDOWN_ACTION_RECEIVED:
                logd("EVENT_SHUTDOWN_ACTION_RECEIVED");
                mIsShutDownInProgress = true;
                break;

            default:
                break;
        }
    }

    /**
     * set the APP of the SIM card
     *
     * @param cardIndex card index
     * @return
     */
    public Subscription setDefaultApp(int cardIndex) {
        SubscriptionData cardSub = mCardSubMgr.getCardSubscriptions(cardIndex);
        // app info is not available
        if (cardSub == null || cardSub.getLength() == 0) {
            return null;
        }

        // try to find the index of the highest priority APP Global > (RUIM/USIM) > (CSIM/SIM)
        int appIndex = Subscription.SUBSCRIPTION_INDEX_INVALID;
        for (int i = 0; i < cardSub.getLength(); i++) {

            String appType = cardSub.subscription[i].appType;
            logd("find the " + appType + " appIndex " + i);

            if ("GLOBAL".equals(appType)) {
                appIndex = i;
                break;
            } else if ("RUIM".equals(appType) || "USIM".equals(appType)) {
                //if there are multiple apps of USIM/RUIM, will set the appIndex to first USIM/RUIM
                if (appIndex != Subscription.SUBSCRIPTION_INDEX_INVALID) {
                    String lastAppType = cardSub.subscription[appIndex].appType;
                    if (("RUIM".equals(lastAppType) || "USIM".equals(lastAppType)))
                        continue;
                }
                appIndex = i;
            } else if (("CSIM".equals(appType) || "SIM".equals(appType))
                    && appIndex == Subscription.SUBSCRIPTION_INDEX_INVALID) {
                appIndex = i;
            }
        }

        // can not find RUIM/USIM/CSIM/SIM/GLOBAL APP
        if (appIndex == Subscription.SUBSCRIPTION_INDEX_INVALID) {
            return null;
        }

        Subscription subInCard = cardSub.subscription[appIndex];
        Subscription sub = new Subscription();
        sub.copyFrom(subInCard);
        sub.slotId = cardIndex;
        sub.subId = cardIndex;
        sub.subStatus = SubscriptionStatus.SUB_ACTIVATE;
        return sub;
    }

    /**
     * Handles EVENT_ALL_DATA_DISCONNECTED.
     * This method invoked in case of modem initiated subscription deactivation.
     * Subscription deactivated notification already received and all the data
     * connections are cleaned up.  Now mark the subscription as DEACTIVATED and
     * set the DDS to the available subscription.
     *
     * @param ar
     */
    private void processAllDataDisconnected(AsyncResult ar) {
        if (ar == null) {
            logd("processAllDataDisconnected: ar is null!! return!!");
            return;
        }

        Integer sub = null;
        boolean isTempSwitch = false;
        if (ar.userObj != null) {
            int[] ddsData = (int[])(ar.userObj);
            sub = ddsData[0];
            isTempSwitch = (ddsData[1] == 1);
        } else {
            loge("processAllDataDisconnected: ar.userObj corrupt");
            return;
        }

        logd("processAllDataDisconnected: sub = " + sub + " , isTempSwitch = " + isTempSwitch);
        MSimProxyManager.getInstance().unregisterForAllDataDisconnected(sub, this);

        /*
         * Check if the DDS switch is in progress. If so update the DDS
         * subscription.
         */
        if (mDisableDdsInProgress) {
            updateDataSubscription(isTempSwitch);
            return;
        }

        SubscriptionId subId = SubscriptionId.values()[sub];
        logd("processAllDataDisconnected: subscriptionReadiness[" + sub + "] = "
                + getCurrentSubscriptionReadiness(subId));
        if (!getCurrentSubscriptionReadiness(subId)) {
            // Update the subscription preferences
            updateSubPreferences();
            notifySubscriptionDeactivated(sub);
            triggerUpdateFromAvaialbleCards();
        }
    }

    /**
     * Handles the SET_DATA_SUBSCRIPTION_DONE event
     * @param ar
     */
    private void processSetDataSubscriptionDone(AsyncResult ar) {
        if (ar.exception == null) {
            boolean isTempSwitch = false;
            if (ar.userObj != null) {
                int[] ddsData = (int[])(ar.userObj);
                isTempSwitch = (ddsData[1] == 1);
            } else {
                loge("processSetDataSubscriptionDone: ar.userObj corrupt");
                return;
            }
            logd("Register for the all data disconnect, isTempSwitch = " + isTempSwitch);
            int[] ddsData = new int[2];
            ddsData[0] = mCurrentDds;
            ddsData[1] = (isTempSwitch? 1: 0);
            MSimProxyManager.getInstance().registerForAllDataDisconnected(mCurrentDds, this,
                    EVENT_ALL_DATA_DISCONNECTED, ddsData);
            mDdsSwitchRegistrants.notifyRegistrants();
        } else {
            Rlog.d(LOG_TAG, "setDataSubscriptionSource Failed : ");
            // Reset the flag.
            mDisableDdsInProgress = false;

            // Send the message back to callee with result.
            if (mSetDdsCompleteMsg != null) {
                AsyncResult.forMessage(mSetDdsCompleteMsg, false, null);
                logd("posting failure message");
                mSetDdsCompleteMsg.sendToTarget();
                mSetDdsCompleteMsg = null;
            }

            MSimProxyManager.getInstance().enableDataConnectivity(mCurrentDds);
        }
    }

    private void updateDataSubscription(boolean isTemporarySwitch) {
        logd("updateDefaultDataSubscription: isTempSwitch = " + isTemporarySwitch);
        // Mark this as the current dds
        MSimPhoneFactory.setDataSubscription(mQueuedDds);

        /* Save the user preferred data subscription in DB if it is not a temporary
           DDS switch.*/
        if (!isTemporarySwitch) {
            MSimPhoneFactory.setDefaultDataSubscription(mQueuedDds);
        }

        if (mCurrentDds != mQueuedDds) {
            // The current DDS is changed.  Call update to unregister for all the
            // events in DCT to avoid unnecessary processings in the non-DDS.
            MSimProxyManager.getInstance().updateDataConnectionTracker(mCurrentDds);
        }

        mCurrentDds = mQueuedDds;

        // Update the DCT corresponds to the new DDS.
        MSimProxyManager.getInstance().updateDataConnectionTracker(mCurrentDds);

        // Enable the data connectivity on new dds.
        logd("setDataSubscriptionSource is Successful"
                + "  Enable Data Connectivity on Subscription " + mCurrentDds);
        MSimProxyManager.getInstance().enableDataConnectivity(mCurrentDds);
        mDataActive = true;

        // Reset the flag.
        mDisableDdsInProgress = false;

        // Send the message back to callee.
        if (mSetDdsCompleteMsg != null) {
            AsyncResult.forMessage(mSetDdsCompleteMsg, true, null);
            logd("Enable Data Connectivity Done!! Sending the cnf back!");
            mSetDdsCompleteMsg.sendToTarget();
            mSetDdsCompleteMsg = null;
        }
    }

    /**
     * Handles the EVENT_CLEANUP_DATA_CONNECTION_DONE.
     * @param ar
     */
    private void processCleanupDataConnectionDone(Integer subId) {
		if (!mRadioOn[subId]) {
           logd("processCleanupDataConnectionDone: Radio Not Available on subId = " + subId);
           return;
        }
        // Cleanup data connection is done!  Start processing the
        // pending deactivate requests now.
        mDataActive = false;
        startNextPendingDeactivateRequests();
    }

    /**
     * Handles the EVENT_SUBSCRIPTION_STATUS_CHANGED.
     * @param ar
     */
    private void processSubscriptionStatusChanged(AsyncResult ar) {
        Integer subId = (Integer)ar.userObj;
        int actStatus = ((int[])ar.result)[0];
        SubscriptionId sub = SubscriptionId.values()[subId];
        boolean isSubReady = mCurrentSubscriptions.get(sub).subReady;
        logd("processSubscriptionStatusChanged sub = " + subId
                + " actStatus = " + actStatus);
		
		if (!mRadioOn[subId]) {
           logd("processSubscriptionStatusChanged: Radio Not Available on subId = " + subId);
           return;
        }

        if ((isSubReady == true && actStatus == SUB_STATUS_ACTIVATED) ||
                (isSubReady == false && actStatus == SUB_STATUS_DEACTIVATED)) {
            logd("processSubscriptionStatusChanged: CurrentSubStatus and NewSubStatus are same" +
                    "for subId = "+subId+". Ignore indication!!!");
            return;
        }

        updateSubscriptionReadiness(subId, (actStatus == SUB_STATUS_ACTIVATED));
        if (actStatus == SUB_STATUS_ACTIVATED) { // Subscription Activated
            mCardSubMgr.setSubActivated(subId, true);
            // Shall update the DDS here
            if (mSetDdsRequired) {
                if (subId == mCurrentDds) {
                    logd("setDataSubscription on " + mCurrentDds);
                    // Set mQueuedDds so that when the set data sub src is done, it will
                    // update the system property and enable the data connectivity.
                    mQueuedDds = mCurrentDds;
                    mDisableDdsInProgress = true;
                    int[] ddsData = new int[2];
                    ddsData[0] = mCurrentDds;
                    ddsData[1] = 0; // Not a temporary switch
                    Message msgSetDdsDone = Message.obtain(this, EVENT_SET_DATA_SUBSCRIPTION_DONE,
                            ddsData);
                    // Set Data Subscription preference at RIL
                    mCi[mCurrentDds].setDataSubscription(msgSetDdsDone);
                    mSetDdsRequired = false;
                }
            }
            notifySubscriptionActivated(subId);
        } else if (actStatus == SUB_STATUS_DEACTIVATED) {
            mCardSubMgr.setSubActivated(subId, false);
            if (mCardInfoAvailable[subId] == false) resetCurrentSubscription(sub);
            // In case if this is DDS subscription, then wait for the all data disconnected
            // indication from the lower layers to mark the subscription as deactivated.
            if (subId == mCurrentDds) {
                logd("Register for the all data disconnect");
                mDdsSwitchRegistrants.notifyRegistrants();
                int[] ddsData = new int[2];
                ddsData[0] = subId;
                ddsData[1] = 0; // Not a temporary switch
                MSimProxyManager.getInstance().registerForAllDataDisconnected(subId, this,
                        EVENT_ALL_DATA_DISCONNECTED, ddsData);
            } else {
                updateSubPreferences();
                notifySubscriptionDeactivated(subId);
                triggerUpdateFromAvaialbleCards();
            }
        } else {
            logd("handleSubscriptionStatusChanged INVALID");
        }
    }

    /**
     * Handles the EVENT_SET_UICC_SUBSCRPTION_DONE.
     * @param ar
     */
    private void processSetUiccSubscriptionDone(AsyncResult ar) {
        SetUiccSubsParams setSubParam = (SetUiccSubsParams)ar.userObj;
        boolean saveGlobalSettings = false;
        String cause = null;
        SubscriptionStatus subStatus = SubscriptionStatus.SUB_INVALID;
        Subscription currentSub = null;
		
		if (!mRadioOn[setSubParam.subId]) {
           logd("processSetUiccSubscriptionDone: Radio Not Available on subId = "
                + setSubParam.subId);
           return;
        }

        if (setSubParam.appType.equals("GLOBAL") &&
                (setSubParam.subStatus == SubscriptionStatus.SUB_ACTIVATE)) {
            if ((mCardSubMgr.is3gppApp(setSubParam.subId, setSubParam.app3gppId)) &&
                   setSubParam.app3gppAppStatus == SUB_NOT_CHANGED) {
                if (ar.exception == null) {
                    mCurrentAppId = setSubParam.app3gppId;
                    setSubParam.app3gppAppStatus = SUB_ACTIVATE_SUCCESS;
                } else {
                    setSubParam.app3gppAppStatus = SUB_ACTIVATE_FAILED;
                }
            }
            if ((mCardSubMgr.is3gpp2App(setSubParam.subId, setSubParam.app3gpp2Id)) &&
                     setSubParam.app3gpp2AppStatus == SUB_NOT_CHANGED) {
                if (ar.exception == null) {
                    mCurrentAppId = setSubParam.app3gpp2Id;
                    setSubParam.app3gpp2AppStatus = SUB_ACTIVATE_SUCCESS;
                } else {
                    setSubParam.app3gpp2AppStatus = SUB_ACTIVATE_FAILED;
                }
            }
            if (setSubParam.app3gppAppStatus == SUB_NOT_CHANGED ||
                  setSubParam.app3gpp2AppStatus == SUB_NOT_CHANGED) {
                return;
            }
            if (setSubParam.app3gppAppStatus == SUB_ACTIVATE_SUCCESS
                    && setSubParam.app3gpp2AppStatus == SUB_ACTIVATE_SUCCESS) {
                subStatus = SubscriptionStatus.SUB_ACTIVATED;
                cause = SUB_ACTIVATE_SUCCESS;
                currentSub = mActivatePending.get(SubscriptionId.values()[setSubParam.subId]);
            } else if (setSubParam.app3gppAppStatus == SUB_ACTIVATE_FAILED
                      && setSubParam.app3gpp2AppStatus == SUB_ACTIVATE_FAILED) {
                cause = SUB_ACTIVATE_FAILED;
                subStatus = SubscriptionStatus.SUB_DEACTIVATED;
                currentSub = mActivatePending.get(SubscriptionId.values()[setSubParam.subId]);
            } else {
                saveGlobalSettings = true;
                subStatus = SubscriptionStatus.SUB_ACTIVATED;
                cause = SUB_GLOBAL_ACTIVATE_FAILED;
                currentSub = mCardSubMgr.getCardSubscriptions(
                        setSubParam.subId).subscription[mCurrentAppId];
            }
            mActivatePending.put(SubscriptionId.values()[setSubParam.subId], null);
        } else if (setSubParam.appType.equals("GLOBAL") &&
                (setSubParam.subStatus == SubscriptionStatus.SUB_DEACTIVATE)) {
            if ((mCardSubMgr.is3gppApp(setSubParam.subId, setSubParam.app3gppId)) &&
                   setSubParam.app3gppAppStatus == SUB_NOT_CHANGED) {
                if (ar.exception == null) {
                    mCurrentAppId = setSubParam.app3gppId;
                    setSubParam.app3gppAppStatus = SUB_DEACTIVATE_SUCCESS;
                } else {
                    setSubParam.app3gppAppStatus = SUB_DEACTIVATE_FAILED;
                }
            }
            if ((mCardSubMgr.is3gpp2App(setSubParam.subId, setSubParam.app3gpp2Id)) &&
                     setSubParam.app3gpp2AppStatus == SUB_NOT_CHANGED) {
                if (ar.exception == null) {
                    mCurrentAppId = setSubParam.app3gpp2Id;
                    setSubParam.app3gpp2AppStatus = SUB_DEACTIVATE_SUCCESS;
                } else {
                    setSubParam.app3gpp2AppStatus = SUB_DEACTIVATE_FAILED;
                }
            }
            if (setSubParam.app3gppAppStatus == SUB_NOT_CHANGED ||
                  setSubParam.app3gpp2AppStatus == SUB_NOT_CHANGED) {
                return;
            }
            if (setSubParam.app3gppAppStatus == SUB_DEACTIVATE_SUCCESS
                    && setSubParam.app3gpp2AppStatus == SUB_DEACTIVATE_SUCCESS) {
                subStatus = SubscriptionStatus.SUB_DEACTIVATED;
                cause = SUB_DEACTIVATE_SUCCESS;
                currentSub = mDeactivatePending.get(SubscriptionId.values()[setSubParam.subId]);
                notifySubscriptionDeactivated(setSubParam.subId);
            } else if (setSubParam.app3gppAppStatus == SUB_DEACTIVATE_FAILED
                      && setSubParam.app3gpp2AppStatus == SUB_DEACTIVATE_FAILED) {
                cause = SUB_DEACTIVATE_FAILED;
                subStatus = SubscriptionStatus.SUB_ACTIVATED;
                currentSub = mDeactivatePending.get(SubscriptionId.values()[setSubParam.subId]);
            } else {
                saveGlobalSettings = true;
                subStatus = SubscriptionStatus.SUB_DEACTIVATED;
                cause = SUB_GLOBAL_DEACTIVATE_FAILED;
                currentSub = mCardSubMgr.getCardSubscriptions(
                        setSubParam.subId).subscription[mCurrentAppId];
                notifySubscriptionDeactivated(setSubParam.subId);
            }
            mDeactivatePending.put(SubscriptionId.values()[setSubParam.subId], null);
        } else if (ar.exception != null) {
            // SET_UICC_SUBSCRIPTION failed
            if (ar.exception instanceof CommandException ) {
                CommandException.Error error = ((CommandException) (ar.exception))
                    .getCommandError();
                if (error != null &&
                        error ==  CommandException.Error.SUBSCRIPTION_NOT_SUPPORTED) {
                    if (setSubParam.subStatus == SubscriptionStatus.SUB_ACTIVATE) {
                        cause = SUB_ACTIVATE_NOT_SUPPORTED;
                    } else if (setSubParam.subStatus == SubscriptionStatus.SUB_DEACTIVATE) {
                        cause = SUB_DEACTIVATE_NOT_SUPPORTED;
                    }
                }
            }
            if (setSubParam.subStatus == SubscriptionStatus.SUB_ACTIVATE) {
                // Set uicc subscription failed for activating the sub.
                logd("subscription of SUB:" + setSubParam.subId + " Activate Failed");
                if (cause == null) {
                    cause = SUB_ACTIVATE_FAILED;
                }
                subStatus = SubscriptionStatus.SUB_DEACTIVATED;
                currentSub = mActivatePending.get(SubscriptionId.values()[setSubParam.subId]);

                // Clear the pending activate request list
                mActivatePending.put(SubscriptionId.values()[setSubParam.subId], null);
            } else if (setSubParam.subStatus == SubscriptionStatus.SUB_DEACTIVATE) {
                // Set uicc subscription failed for deactivating the sub.
                logd("subscription of SUB:" + setSubParam.subId + " Deactivate Failed");

                // If there is any pending request for activate the same sub
                // which means user might have tried to deactivate the sub, and
                // and activate with another app from any of the cards
                if (cause == null) {
                    if (isAnyPendingActivateRequest(setSubParam.subId )) {
                        cause = SUB_ACTIVATE_FAILED;
                        // Activate failed means the sub is active with the current app.
                        // We cannot proceed with a activate request for particular
                        // subscription which is active with another application.
                        // Clear the pending activate entry
                        mActivatePending.put(SubscriptionId.values()[setSubParam.subId], null);
                    } else {
                        cause = SUB_DEACTIVATE_FAILED;
                    }
                }
                subStatus = SubscriptionStatus.SUB_ACTIVATED;
                currentSub = mDeactivatePending.get(SubscriptionId.values()[setSubParam.subId]);
                // Clear the deactivate pending entry
                mDeactivatePending.put(SubscriptionId.values()[setSubParam.subId], null);

                if (mCurrentDds == setSubParam.subId) {
                    // Deactivating the current DDS is failed. Try bring up data again.
                    MSimProxyManager.getInstance().enableDataConnectivity(mCurrentDds);
                }
            }else {
                logd("UNKOWN: SHOULD NOT HIT HERE");
            }
        } else {
            // SET_UICC_SUBSCRIPTION success
            if (setSubParam.subStatus == SubscriptionStatus.SUB_ACTIVATE) {
                // Activate Success!!
                logd("subscription of SUB:" + setSubParam.subId + " Activated");
                subStatus = SubscriptionStatus.SUB_ACTIVATED;
                cause = SUB_ACTIVATE_SUCCESS;
                currentSub = mActivatePending.get(SubscriptionId.values()[setSubParam.subId]);

                // Clear the pending activate request list
                mActivatePending.put(SubscriptionId.values()[setSubParam.subId], null);
            } else if (setSubParam.subStatus == SubscriptionStatus.SUB_DEACTIVATE) {
                // Deactivate success
                logd("subscription of SUB:" + setSubParam.subId + " Deactivated");
                subStatus = SubscriptionStatus.SUB_DEACTIVATED;
                cause = SUB_DEACTIVATE_SUCCESS;
                currentSub = mDeactivatePending.get(SubscriptionId.values()[setSubParam.subId]);
                // Clear the deactivate pending entry
                mDeactivatePending.put(SubscriptionId.values()[setSubParam.subId], null);
                // Deactivate completed!
                notifySubscriptionDeactivated(setSubParam.subId);
            } else {
                logd("UNKOWN: SHOULD NOT HIT HERE");
            }
        }

        logd("set uicc subscription done. update the current subscriptions");
        // Update the current subscription for this subId;
        updateCurrentSubscription(setSubParam.subId,
                currentSub,
                subStatus,
                cause);
        // do not saveUserPreferredSubscription in case of failure
        if (ar.exception == null || saveGlobalSettings) {
            saveUserPreferredSubscription(setSubParam.subId,
                    getCurrentSubscription(SubscriptionId.values()[setSubParam.subId]));
        } else {
            // Failure case: update the subscription readiness properly.
            updateSubscriptionReadiness(setSubParam.subId,
                    (subStatus == SubscriptionStatus.SUB_ACTIVATED));
        }

        mSubResult[setSubParam.subId] = cause;

        if (startNextPendingDeactivateRequests()) {
            // There are deactivate requests.
        } else if (startNextPendingActivateRequests()) {
            // There are activate requests.
        } else {
            mSetSubscriptionInProgress = false;
            updateSubPreferences();
            // DONE! notify now!
            if (mSetSubscriptionRegistrants != null) {
                mSetSubscriptionRegistrants.notifyRegistrants(
                        new AsyncResult(null, mSubResult, null));
                        //new AsyncResult(null, getSetSubscriptionResults(), null));
            }
        }
    }

    /**
     * Returns the set subscription status.
     * @return
     */
    private String[] getSetSubscriptionResults() {
        String result[] = new String[NUM_SUBSCRIPTIONS];
        for (int i = 0; i < NUM_SUBSCRIPTIONS; i++) {
            result[i] = mCurrentSubscriptions.get(SubscriptionId.values()[i]).cause;
        }

        return result;
    }

    public int getSlotId(int subscription) {
         return getCurrentSubscription(subscription).slotId;
    }

    /**
     * Get the next available subscription
     */
    private int getNextActiveSubscription(int sub) {
        int subscription = sub;
        SubscriptionId subId;
        do {
            subscription = (subscription + 1) < mNumPhones
                           ? (subscription + 1)
                           : MSimConstants.SUB1;
            subId = SubscriptionId.values()[subscription];
            if (getCurrentSubscriptionStatus(subId) == SubscriptionStatus.SUB_ACTIVATED) {
                return subscription;
            }
        } while (sub != subscription);
        Rlog.e(LOG_TAG, "getNextActiveSubscription should not come here !!!!!");
        return -1; // This should not come
    }

    /**
     * Updates the subscriptions preferences based on the number of active subscriptions.
     */
    private void updateSubPreferences() {
        if (mIsShutDownInProgress) {
            logd("updateSubPreferences: Shutdown in progress. Do not update sub prefernces");
            return;
        }

        int activeSubCount = 0;

        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            if (getCurrentSubscriptionStatus(sub) == SubscriptionStatus.SUB_ACTIVATED) {
                activeSubCount++;
            } else {
                // If there any pending activation requests on the deactivated sub, we
                // should not update sub preferences, instead wait for the activations to complete.
                // This can happen when user changes the subscription app within the same sub.
                // For ex: CSIM->Global, Global->CSIM etc.
                if ((mSetSubscriptionInProgress && isAnyPendingActivateRequest(i)) ||
                        mCardSubMgr.isApmSimPwrDown(i)) {
                    logd("updateSubPreferences: Sub" + i + " has pending activation reqs or is in"
                           + "APM sim powerdown. Do not update sub prefs now.");
                    return;
                }
            }
        }

        logd("updateSubPreferences: activeSubCount = " + activeSubCount
                + " mNumPhones = " + mNumPhones);
        // If preferred subscription is deactivated then check next available subscription and
        // set that subscription as preferred for voice/sms/data.
        if (activeSubCount > 0 && activeSubCount < mNumPhones) {
            int subscription = MSimPhoneFactory.getVoiceSubscription();
            SubscriptionId subId = SubscriptionId.values()[subscription];
            if (getCurrentSubscriptionStatus(subId) != SubscriptionStatus.SUB_ACTIVATED) {
                subscription = getNextActiveSubscription(subscription);
                MSimPhoneFactory.setVoiceSubscription(subscription);
                MSimPhoneFactory.setPrioritySubscription(subscription);
            }
            subscription = MSimPhoneFactory.getSMSSubscription();
            subId = SubscriptionId.values()[subscription];
            if (getCurrentSubscriptionStatus(subId) != SubscriptionStatus.SUB_ACTIVATED) {
                subscription = getNextActiveSubscription(subscription);
                MSimPhoneFactory.setSMSSubscription(subscription);
            }
            //Disable Prompt mode if only one sub is active.
            if (activeSubCount == 1) {
                MSimPhoneFactory.setPromptEnabled(false);
                MSimPhoneFactory.setSMSPromptEnabled(false);
            }
            sendDefaultSubsInfo();

            logd("updateSubPreferences: current defaultSub = "
                    + MSimPhoneFactory.getDefaultSubscription());
            logd("updateSubPreferences: current mCurrentDds = " + mCurrentDds);
            subscription = MSimPhoneFactory.getDefaultSubscription();
            subId = SubscriptionId.values()[subscription];
            if (getCurrentSubscriptionStatus(subId) != SubscriptionStatus.SUB_ACTIVATED) {
                subscription = getNextActiveSubscription(subscription);
                MSimPhoneFactory.setDefaultSubscription(subscription);
            }

            subId = SubscriptionId.values()[mCurrentDds];
            if (getCurrentSubscriptionStatus(subId) != SubscriptionStatus.SUB_ACTIVATED) {
                subscription = getNextActiveSubscription(mCurrentDds);

                // Currently selected DDS subscription is not in activated state.
                // So set the DDS to the next subscription available now.
                // Directly set the Data Subscription Source to the only activeSub if it
                // is READY. If the SUBSCRIPTION_READY event is not yet received on this
                // subscription, wait for the event to set the Data Subscription Source.
                subId = SubscriptionId.values()[subscription];
                if (getCurrentSubscriptionReadiness(subId)) {
                    mQueuedDds = subscription;
                    int[] ddsData = new int[2];
                    ddsData[0] = subscription;
                    ddsData[1] = 0; // Not a temporary switch
                    Message callback = Message.obtain(this, EVENT_SET_DATA_SUBSCRIPTION_DONE,
                            ddsData);
                    mDisableDdsInProgress = true;
                    logd("update setDataSubscription to " + subscription);
                    mCi[subscription].setDataSubscription(callback);
                    mSetDdsRequired = false;
                } else {
                    // Set the flag and update the mCurrentDds, so that when subscription
                    // ready event receives, it will set the dds properly.
                    mSetDdsRequired = true;
                    mCurrentDds = subscription;
                    //MSimPhoneFactory.setDataSubscription(mCurrentDds);
                }
            }
        }
    }

    /**
     * Handles EVENT_ALL_CARDS_INFO_AVAILABLE.
     */
    private void processAllCardsInfoAvailable() {
		if (!isAllRadioOn()) {
           logd("processAllCardsInfoAvailable: Radio Not Available ");
           return;
        }
        int availableCards = 0;
        mAllCardsStatusAvailable = true;

        for (int i = 0; i < mNumPhones; i++) {
            if (mCardInfoAvailable[i] || mCardSubMgr.isCardAbsentOrError(i)) {
                availableCards++;
            }
        }
        // Process any pending activate requests if there is any.
        if (availableCards == mNumPhones
            && !mSetSubscriptionInProgress) {
            processActivateRequests();
        }

        notifyIfAnyNewCardsAvailable();
    }

    /**
     * Sends a message to itself to process the available cards
     */
    private void triggerUpdateFromAvaialbleCards() {
        sendMessage(obtainMessage(EVENT_PROCESS_AVAILABLE_CARDS));
    }

    /**
     * Handles EVENT_PROCESS_AVAILABLE_CARDS
     */
    private void processAvailableCards() {
		if (!isAllRadioOn()) {
           logd("processAvailableCards: Radio Not Available ");
           return;
        }
        if (mSetSubscriptionInProgress) {
           logd("processAvailableCards: set subscription in progress!!");
           return;
        }

        for (int cardIndex = 0; cardIndex < mNumPhones; cardIndex++) {
            updateActivatePendingList(cardIndex);
        }

        processActivateRequests();

        notifyIfAnyNewCardsAvailable();
    }

    /**
     * Handles the EVENT_SET_PRIORITY_SUBSCRIPTION_DONE event
     * @param ar
     */
    private void processSetPrioritySubscriptionDone(AsyncResult ar) {
        if (ar.exception == null) {
            Rlog.d(LOG_TAG, "setPrioritySubscriptionDone is success");
        } else {
            Rlog.e(LOG_TAG, "setPrioritySubscriptionDone is failed");
        }
    }

    /**
     * Handles the EVENT_SET_DEFAULT_VOICE_SUBSCRIPTION_DONE event
     * @param ar
     */
    private void processSetDefaultVoiceSubscriptionDone(AsyncResult ar) {
        if (ar.exception == null) {
            Rlog.d(LOG_TAG, "setDefaultVoiceSubscriptionDone is success");
        } else {
            Rlog.e(LOG_TAG, "setDefaultVoiceSubscriptionDone is failed");
        }
    }

    private void notifyIfAnyNewCardsAvailable() {
        if (isNewCardAvailable()) {
            // NEW CARDs Available!!!
            // Notify the USER HERE!!! unless auto provision is enabled
            if (!mContext.getResources().getBoolean
                    (com.android.internal.R.bool.config_auto_provision_enable)) {
                notifyNewCardsAvailable();
            }
            for (int i = 0; i < mIsNewCard.length; i++) {
                mIsNewCard[i] = false;
            }
        }
    }

    private void updateActivatePendingList(int cardIndex) {
        if (mCardInfoAvailable[cardIndex]) {
            SubscriptionData cardSubInfo = mCardSubMgr.getCardSubscriptions(cardIndex);

            logd("updateActivatePendingList: cardIndex = " + cardIndex
                    + "\n Card Sub Info = " + cardSubInfo);
            if (cardSubInfo == null) {
                return;
            }

            Subscription userSub = mUserPrefSubs.subscription[cardIndex];
            int subId = userSub.subId;
            Subscription currentSub = getCurrentSubscription(SubscriptionId.values()[subId]);

            logd("updateActivatePendingList: subId = " + subId
                    + "\n user pref sub = " + userSub
                    + "\n current sub   = " + currentSub);

            if ((userSub.subStatus == SubscriptionStatus.SUB_ACTIVATED)
                    && (currentSub.subStatus != SubscriptionStatus.SUB_ACTIVATED)
                    && (cardSubInfo.hasSubscription(userSub))
                    && !isPresentInActivatePendingList(userSub)){
                logd("updateActivatePendingList: subId = " + subId + " need to activate!!!");

                // Need to activate this Subscription!!! - userSub.subId
                // Push to the queue, so that start the SET_UICC_SUBSCRIPTION
                // only when the both cards are ready.
                Subscription sub = new Subscription();
                sub.copyFrom(cardSubInfo.getSubscription(userSub));
                sub.slotId = cardIndex;
                sub.subId = subId;
                sub.subStatus = SubscriptionStatus.SUB_ACTIVATE;
                mActivatePending.put(SubscriptionId.values()[subId], sub);
            } else if (mContext.getResources().getBoolean
                    (com.android.internal.R.bool.config_auto_provision_enable)) {
                Subscription sub = setDefaultApp(cardIndex);
                if (sub != null && !(userSub.subStatus == SubscriptionStatus.SUB_DEACTIVATED
                        && sub.isSame(userSub))) {
                    logd("enable the SIM card on sub" + cardIndex + " by auto provisioning");
                    mActivatePending.put(SubscriptionId.values()[subId], sub);
                }
            }

            // If this is a new card(no user preferred subscriptions are from
            // this card), then notify a prompt to user.  Let user select
            // the subscriptions from new card!
            if (cardSubInfo.hasSubscription(userSub)) {
                mIsNewCard[cardIndex] = false;
            } else {
                mIsNewCard [cardIndex] = true;
            }
            logd("updateActivatePendingList: mIsNewCard [" + cardIndex + "] = "
                    + mIsNewCard [cardIndex]);
        }
    }

    /**
     * Handles EVENT_CARDS_INFO_AVAILABLE.
     * New cards available.
     * @param ar
     */
    private void processCardInfoAvailable(AsyncResult ar) {
        Integer cardIndex = (Integer)ar.userObj;
		
		if (!mRadioOn[cardIndex]) {
           logd("processCardInfoAvailable: Radio Not Available on cardIndex = " + cardIndex);
           return;
        }

        mCardInfoAvailable[cardIndex] = true;

        logd("processCardInfoAvailable: CARD:" + cardIndex + " is available");

        // Card info on slot cardIndex is available.
        // Check if any user preferred subscriptions are available in
        // this card.  If there is any, and which are not yet activated,
        // activate them!
        updateActivatePendingList(cardIndex);

        if (!isAllCardsInfoAvailable()) {
            logd("All cards info not available!! Waiting for all info before processing");
            return;
        }

        logd("processCardInfoAvailable: mSetSubscriptionInProgress = "
                + mSetSubscriptionInProgress);

        if (!mSetSubscriptionInProgress) {
            processActivateRequests();
        }

        notifyIfAnyNewCardsAvailable();
    }

    private boolean isPresentInActivatePendingList(Subscription userSub) {
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            Subscription actPendingSub = mActivatePending.get(sub);
            if (userSub != null && userSub.isSame(actPendingSub)
                    && userSub.subId == actPendingSub.subId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notify new cards available.
     * Starts the SetSubscription activity.
     */
    void notifyNewCardsAvailable() {
        logd("notifyNewCardsAvailable" );
        Intent setSubscriptionIntent = new Intent(Intent.ACTION_MAIN);
        setSubscriptionIntent.setClassName("com.android.phone",
                "com.android.phone.SetSubscription");
        setSubscriptionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        setSubscriptionIntent.putExtra("NOTIFY_NEW_CARD_AVAILABLE", true);

        mContext.startActivity(setSubscriptionIntent);
    }
	
	private boolean isAllRadioOn() {
        boolean result = true;
        for (boolean radioOn : mRadioOn) {
            result = result && radioOn;
        }
        return result;
    }

    private boolean isAllCardsInfoAvailable() {
        boolean result = true;
        for (boolean available : mCardInfoAvailable) {
            result = result && available;
        }
        return result || mAllCardsStatusAvailable;
    }
    private boolean isNewCardAvailable() {
        boolean result = false;
        for (boolean isNew : mIsNewCard) {
            result = result || isNew;
        }
        return result;
    }

    /**
     * Handles EVENT_CARDS_INFO_NOT_AVAILABLE..
     * Card has been removed!!
     * @param ar
     */
    private void processCardInfoNotAvailable(AsyncResult ar) {
        if (ar.exception != null || ar.result == null) {
            logd("processCardInfoNotAvailable - Exception");
            return;
        }

        Integer cardIndex = (Integer)ar.userObj;
        CardUnavailableReason reason = (CardUnavailableReason)ar.result;
        SubscriptionId sub = SubscriptionId.values()[cardIndex];

        logd("processCardInfoNotAvailable on cardIndex = " + cardIndex
                + " reason = " + reason);

        mCardInfoAvailable[cardIndex] = false;

        // Set subscription is required if both the cards are unavailable
        // and when those are available next time!

        boolean subscriptionRequired = true;
        for (int i = 0; i < mNumPhones; i++) {
            subscriptionRequired = subscriptionRequired && !mCardInfoAvailable[i];
        }

        // Reset the current subscription and notify the subscriptions deactivated.
        if (reason == CardUnavailableReason.REASON_RADIO_UNAVAILABLE
                || reason == CardUnavailableReason.REASON_SIM_REFRESH_RESET
                || reason == CardUnavailableReason.REASON_APM_SIM_POWER_DOWN
                || (getCurrentSubscriptionReadiness(sub) == false
                && reason == CardUnavailableReason.REASON_CARD_REMOVED)) {
            // Card has been removed from slot - cardIndex.
            // Mark the active subscription from this card as de-activated!!
            resetCurrentSubscription(sub);
            if (reason == CardUnavailableReason.REASON_CARD_REMOVED) {
                updateSubPreferences();
            }
            notifySubscriptionDeactivated(sub.ordinal());
        }

        if (reason == CardUnavailableReason.REASON_RADIO_UNAVAILABLE
                || reason == CardUnavailableReason.REASON_APM_SIM_POWER_DOWN) {
            mAllCardsStatusAvailable = false;
        }
    }


    /**
     * Prints the pending list. For debugging.
     */
    private void printPendingActivateRequests() {
        logd("ActivatePending Queue : ");
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            Subscription newSub = mActivatePending.get(sub);
            logd(sub + ":" + newSub);
        }
    }

    /**
     * Prints the pending list. For debugging.
     */
    private void printPendingDeactivateRequests() {
        logd("DeactivatePending Queue : ");
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            Subscription newSub = mDeactivatePending.get(sub);
            logd(sub + ":" + newSub);
        }
    }

    private void setUiccSubscription(Subscription newSub) {
        if (newSub.appType.equals("GLOBAL")) {
            int globalAppsIndex[] = mCardSubMgr.getGlobalAppsIndex(newSub.slotId);
            SetUiccSubsParams globalSetSubParam = new SetUiccSubsParams(
                    newSub.subId, newSub.subStatus, newSub.appType, globalAppsIndex[0],
                    globalAppsIndex[1], SUB_NOT_CHANGED, SUB_NOT_CHANGED);

            for (int i=0; i < globalAppsIndex.length; i++) {
                Message msgSetUiccSubDone = Message.obtain(this,
                        EVENT_SET_UICC_SUBSCRIPTION_DONE,
                        globalSetSubParam);
                mCi[newSub.subId].setUiccSubscription(newSub.slotId,
                        globalAppsIndex[i],
                        newSub.subId,
                        newSub.subStatus.ordinal(),
                        msgSetUiccSubDone);
            }
        } else {
            SetUiccSubsParams setSubParam = new SetUiccSubsParams(
                    newSub.subId, newSub.subStatus, newSub.appType,
                    Subscription.SUBSCRIPTION_INDEX_INVALID,
                    Subscription.SUBSCRIPTION_INDEX_INVALID,
                    SUB_NOT_CHANGED, SUB_NOT_CHANGED);
            Message msgSetUiccSubDone = Message.obtain(this,
                    EVENT_SET_UICC_SUBSCRIPTION_DONE,
                    setSubParam);
            mCi[newSub.subId].setUiccSubscription(newSub.slotId,
                    newSub.getAppIndex(),
                    newSub.subId,
                    newSub.subStatus.ordinal(),
                    msgSetUiccSubDone);
        }
    }

    /**
     * Start one deactivate from the pending deactivate request queue.
     * If the deactivate is required for the DDS SUB, then initiate
     * clean up the data connection and deactivate later.
     * @return true if deactivate is started.
     */
    private boolean startNextPendingDeactivateRequests() {
        printPendingDeactivateRequests();

        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            Subscription newSub = mDeactivatePending.get(sub);
            if (newSub != null && newSub.subStatus == SubscriptionStatus.SUB_DEACTIVATE) {
                if (!validateDeactivationRequest(newSub)) {
                    // Not a valid entry. Clear the deactivate pending entry
                    mDeactivatePending.put(sub, null);
                    continue;
                }

                logd("startNextPendingDeactivateRequests: Need to deactivating SUB : " + newSub);
                if (mCurrentDds == newSub.subId && mDataActive) {
                    // This is the DDS.
                    // Tear down all the data calls on this subscription. Once the
                    // clean up completed, the set uicc subscription request with
                    // deactivate will be sent to deactivate this subscription.
                    logd("Deactivate all the data calls if there is any");
                    Message allDataCleanedUpMsg = Message.obtain(this,
                            EVENT_CLEANUP_DATA_CONNECTION_DONE, mCurrentDds);
                    MSimProxyManager.getInstance().disableDataConnectivity(
                            mCurrentDds, allDataCleanedUpMsg);
                    mSetDdsRequired = true;
                } else {
                    logd("startNextPendingDeactivateRequests: Deactivating now");
                    setUiccSubscription(newSub);
                }
                // process one request at a time!!
                return true;
            }
        }
        return false;
    }

    /**
     * Process activate requests.  Set the subscription mode if required.
     */
    private void processActivateRequests() {
        logd("processActivateRequests: mSetSubscriptionInProgress = "
                 + mSetSubscriptionInProgress);
        if (!mSetSubscriptionInProgress) {
            mSetSubscriptionInProgress = startNextPendingActivateRequests();
        }
    }


    private boolean validateDeactivationRequest(Subscription sub) {
        // Check the parameters here!
        // subStatus, subId, slotId, appIndex
        if (sub.subStatus == Subscription.SubscriptionStatus.SUB_DEACTIVATE
                && (sub.subId >= 0 && sub.subId < NUM_SUBSCRIPTIONS)
                && (sub.slotId >= 0 && sub.slotId < NUM_SUBSCRIPTIONS)
                && (sub.getAppIndex() >= 0
                        && (mCardSubMgr.getCardSubscriptions(sub.slotId) != null)
                        && sub.getAppIndex() <
                        mCardSubMgr.getCardSubscriptions(sub.slotId).getLength())) {
            return true;
        }
        return false;
    }

    private boolean validateActivationRequest(Subscription sub) {
        // Check the parameters here!
        // subStatus, subId, slotId, appIndex
        if (sub.subStatus == Subscription.SubscriptionStatus.SUB_ACTIVATE
                && (sub.subId >= 0 && sub.subId < NUM_SUBSCRIPTIONS)
                && (sub.slotId >= 0 && sub.slotId < NUM_SUBSCRIPTIONS)
                && (sub.getAppIndex() >= 0
                        && (mCardSubMgr.getCardSubscriptions(sub.slotId) != null)
                        && sub.getAppIndex() <
                        mCardSubMgr.getCardSubscriptions(sub.slotId).getLength())) {
            return true;
        }
        return false;
    }

    /**
     * Start one activate request from the pending activate request queue.
     * @return true if activate request is started.
     */
    private boolean startNextPendingActivateRequests() {
        printPendingActivateRequests();

        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            Subscription newSub = mActivatePending.get(sub);
            if (newSub != null && newSub.subStatus == SubscriptionStatus.SUB_ACTIVATE) {
                if (!validateActivationRequest(newSub)) {
                    // Not a valid entry.  Clear the pending activate request list
                    mActivatePending.put(sub, null);
                    continue;
                }

                // We need to update the phone object for the new subscription.
                MSimProxyManager.getInstance().checkAndUpdatePhoneObject(newSub);

                logd("startNextPendingActivateRequests: Activating SUB : " + newSub);
                setUiccSubscription(newSub);
                // process one request at a time!!
                return true;
            }
        }
        return false;
    }

    private boolean isAnyPendingActivateRequest(int subId) {
        Subscription newSub = mActivatePending.get(SubscriptionId.values()[subId]);
        if (newSub != null
                && newSub.subStatus == SubscriptionStatus.SUB_ACTIVATE) {
            return true;
        }
        return false;
    }

    private void updateCurrentSubscription(int subId, Subscription subscription,
            SubscriptionStatus subStatus, String cause) {
        SubscriptionId sub = SubscriptionId.values()[subId];

        logd("updateCurrentSubscription: subId = " + sub
                + " subStatus = " + subStatus + "\n subscription = " + subscription);

        if (subStatus == SubscriptionStatus.SUB_ACTIVATED) {
            getCurrentSubscription(sub).copyFrom(subscription);
        } else {
            // If not activated, mark as deactivated always!!
            subStatus = SubscriptionStatus.SUB_DEACTIVATED;
        }
        getCurrentSubscription(sub).subStatus = subStatus;
        if (cause == null) {
            cause = SUB_NOT_CHANGED;
        }
        mCurrentSubscriptions.get(sub).cause = cause;
        mCurrentSubscriptions.get(sub).subReady = false;
    }

    private void updateSubscriptionReadiness(int subId, boolean ready) {
        SubscriptionId sub = SubscriptionId.values()[subId];
        logd("updateSubscriptionReadiness(" + subId + "," + ready + ")");

        // Set subscription ready to true only if subscription is activated!
        if (ready && getCurrentSubscription(sub).subStatus == SubscriptionStatus.SUB_ACTIVATED) {
            mCurrentSubscriptions.get(sub).subReady = true;
            return;
        }
        // Subscription is not activated.  So irrespective of the ready, set to false.
        mCurrentSubscriptions.get(sub).subReady = false;
    }

    /**
     * Reset the subscriptions.  Mark the selected subscription as Deactivated.
     * @param subId
     */
    private void resetCurrentSubscription(SubscriptionId subId){
        getCurrentSubscription(subId).clear();
        getCurrentSubscription(subId).subStatus = SubscriptionStatus.SUB_DEACTIVATED;

        mCurrentSubscriptions.get(subId).cause = null;
        mCurrentSubscriptions.get(subId).subReady = false;
    }

    private Subscription getCurrentSubscription(SubscriptionId subId) {
        return mCurrentSubscriptions.get(subId).sub;
    }

    public Subscription getCurrentSubscription(int subId) {
        return getCurrentSubscription(SubscriptionId.values()[subId]);
    }

    private SubscriptionStatus getCurrentSubscriptionStatus(SubscriptionId subId) {
        return mCurrentSubscriptions.get(subId).sub.subStatus;
    }

    private boolean getCurrentSubscriptionReadiness(SubscriptionId subId) {
        return mCurrentSubscriptions.get(subId).subReady;
    }

    public boolean isSubActive(int subscription) {
        Subscription currentSelSub = getCurrentSubscription(subscription);
        return (currentSelSub.subStatus == SubscriptionStatus.SUB_ACTIVATED);
    }

    /**
     * Notifies the SUB subId is deactivated.
     * @param subId
     */
    private void notifySubscriptionDeactivated(int subId) {
        mSubDeactivatedRegistrants[subId].notifyRegistrants();
    }

    /**
     * Notifies the SUB subId is activated.
     * @param subId
     */
    private void notifySubscriptionActivated(int subId) {
        mSubActivatedRegistrants[subId].notifyRegistrants();
    }

    /**
     * Set Uicc Subscriptions
     * Algorithm:
     * 1. Process the set subscription request if not in progress, return false if
     *    already in progress.
     * 2. Read each requested SUB
     * 3. If user selected a different app for a SUB and previous status of SUB is
     *    ACTIVATED, then need to deactivate it.
     *    Add to the pending Deactivate request Queue.
     * 4. If user selected an app for SUB to ACTIVATE
     *    Add to the pending Activate request Queue.
     * 5. Start deactivate requests
     * 6. If no deactivate requests, start activate requests.
     *    In case of deactivate requests started, the pending activate requests with
     *    be processed after finishing the deactivate.
     * 7. If any set uicc subscription started, return true.
     *
     * @param subData - Contains the required SUB1 and SUB2 subscription information.
     *        To activate a SUB, set the subStatus to ACTIVATE
     *        To deactivate, set the subStatus to DEACTIVATE
     *        To keep the subscription with out any change, set the sub to current sub.
     * @return true if the requested set subscriptions are started.
     *         false if there is no request to update the subscriptions
     *               or if already a set subscription is in progress.
     */
    public boolean setSubscription(SubscriptionData subData) {
        boolean ret = false;

        // Return failure if the set uicc subscription is already in progress.
        // Ideally the setSubscription should not be called when there is a
        // activate/deactivate in undergoing.  Whoever calling should aware of
        // set subscription status.
        if (mSetSubscriptionInProgress) {
            return false;
        }

        for (int i =0; i < mNumPhones; i++) {
            mSubResult[i] = SUB_NOT_CHANGED;
        }

        // Check what are the user preferred subscriptions.
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId subId = SubscriptionId.values()[i];
            // If previous subscription is not same as the requested subscription
            //    (ie., the user must have marked this subscription as deactivate or
            //    selected a new sim app for this subscription), then deactivate the
            //    previous subscription.
            if (!getCurrentSubscription(subId).equals(subData.subscription[subId.ordinal()])) {
                if (getCurrentSubscriptionStatus(subId) == SubscriptionStatus.SUB_ACTIVATED) {
                    logd("Need to deactivate current SUB :" + subId);
                    Subscription newSub = new Subscription();
                    newSub.copyFrom(getCurrentSubscription(subId));
                    newSub.subStatus = SubscriptionStatus.SUB_DEACTIVATE;
                    mDeactivatePending.put(subId, newSub);
                } else if (getCurrentSubscriptionStatus(subId) == SubscriptionStatus.SUB_DEACTIVATED
                        && subData.subscription[subId.ordinal()].subStatus ==
                        SubscriptionStatus.SUB_DEACTIVATE) {
                    // This subscription is already in deactivated state!
                }
            }
            if (subData.subscription[subId.ordinal()].subStatus ==
                    SubscriptionStatus.SUB_ACTIVATE) {
                logd("Need to activate new SUB : " + subId);
                Subscription newSub = new Subscription();
                newSub.copyFrom(subData.subscription[subId.ordinal()]);
                mActivatePending.put(subId, newSub);
            }
        }

        // Start the set uicc subscription only if
        if (!mSetSubscriptionInProgress) {
            boolean deactivateInProgress = startNextPendingDeactivateRequests();
            if (deactivateInProgress) {
                mSetSubscriptionInProgress = true;
            } else {
                processActivateRequests();
            }
        }

        if (mSetSubscriptionInProgress) {
            // No set uicc request to process.
            ret = true;
        }
        return ret;
    }

    /**
     * Sets the designated data subscription source(DDS).
     * @param subscription
     * @param onCompleteMsg
     */
    public void setDataSubscription(int subscription, Message onCompleteMsg) {
        setDataSubscription(subscription, false, onCompleteMsg);
    }

    /**
     * Sets the designated data subscription source(DDS).
     * @param subscription
     * @param isTemporarySwitch - Decides if this is a temporary dds switch.
     *        eg: DDS switch for MMS transaction on non-DDS sub.
     * @param onCompleteMsg
     */
    public void setDataSubscription(int subscription, boolean isTemporarySwitch,
            Message onCompleteMsg) {
        boolean result = false;
        RuntimeException exception;

        logd("setDataSubscription: mCurrentDds = "
                + mCurrentDds + " new subscription = " + subscription);

        if (!mDisableDdsInProgress) {

            if (getCurrentSubscriptionStatus(SubscriptionId.values()[subscription])
                                              != SubscriptionStatus.SUB_ACTIVATED) {
                logd("setDataSubscription: requested SUB:" + subscription
                        + " is not yet activated, returning failure");
                exception = new RuntimeException("Subscription not active");
            } else if (mCurrentDds != subscription) {
                boolean flag = MSimProxyManager.getInstance()
                         .disableDataConnectivityFlag(mCurrentDds);
                mSetDdsCompleteMsg = onCompleteMsg;
                mQueuedDds = subscription;
                mDisableDdsInProgress = true;
                int[] ddsData = new int[2];
                ddsData[0] = mQueuedDds;
                ddsData[1] = (isTemporarySwitch? 1: 0);
                // Set the DDS in cmd interface
                Message msgSetDataSub = Message.obtain(this,
                        EVENT_SET_DATA_SUBSCRIPTION_DONE, ddsData);
                Rlog.d(LOG_TAG, "Set DDS to " + mQueuedDds
                        + " Calling cmd interface setDataSubscription");
                mCi[mQueuedDds].setDataSubscription(msgSetDataSub);
                return;
            } else {
                logd("Current subscription is same as requested Subscription");
                result = true;
                exception = null;
            }
        } else {
            logd("DDS switch in progress. Sending false");
            exception = new RuntimeException("DDS switch in progress");
        }

        // Send the message back to callee with result.
        if (onCompleteMsg != null) {
            AsyncResult.forMessage(onCompleteMsg, result, exception);
            onCompleteMsg.sendToTarget();
        }
    }


    /**
     * Notifies handler when the SUB subId is deactivated.
     * @param subId
     * @param h
     * @param what
     * @param obj
     */
    public void registerForSubscriptionDeactivated(int subId, Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        synchronized (mSubDeactivatedRegistrants[subId]) {
            mSubDeactivatedRegistrants[subId].add(r);
        }
    }

    public void unregisterForSubscriptionDeactivated(int subId, Handler h) {
        synchronized (mSubDeactivatedRegistrants[subId]) {
            mSubDeactivatedRegistrants[subId].remove(h);
        }
    }

    public void registerForDdsSwitch(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        synchronized (mDdsSwitchRegistrants) {
            mDdsSwitchRegistrants.add(r);
        }
    }

    public void unregisterForDdsSwitch(Handler h) {
        synchronized (mDdsSwitchRegistrants) {
            mDdsSwitchRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler when the SUB subId is activated.
     * @param subId
     * @param h
     * @param what
     * @param obj
     */
    public void registerForSubscriptionActivated(int subId, Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        synchronized (mSubActivatedRegistrants[subId]) {
            mSubActivatedRegistrants[subId].add(r);
        }
    }

    public void unregisterForSubscriptionActivated(int subId, Handler h) {
        synchronized (mSubActivatedRegistrants[subId]) {
            mSubActivatedRegistrants[subId].remove(h);
        }
    }

    /**
     * Register for set subscription completed notification.
     * @param h
     * @param what
     * @param obj
     */
    public synchronized void registerForSetSubscriptionCompleted(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSetSubscriptionRegistrants.add(r);
    }

    /**
     * Unregister for set subscription completed.
     * @param h
     */
    public synchronized void unRegisterForSetSubscriptionCompleted(Handler h) {
        mSetSubscriptionRegistrants.remove(h);
    }


    /**
     *  This function will read from the User Preferred Subscription from the
     *  system property, parse and populate the member variable mUserPrefSubs.
     *  User Preferred Subscription is stored in the system property string as
     *    iccId,appType,appId,activationStatus,3gppIndex,3gpp2Index
     *  If the the property is not set already, then set it to the default values
     *  for appType to USIM and activationStatus to ACTIVATED.
     */
    private void getUserPreferredSubs() {
        boolean errorOnParsing = false;

        mUserPrefSubs = new SubscriptionData(NUM_SUBSCRIPTIONS);

        for(int i = 0; i < NUM_SUBSCRIPTIONS; i++) {
            String strUserSub = Settings.Global.getString(mContext.getContentResolver(),
                    Settings.Global.MULTI_SIM_USER_PREFERRED_SUBS[i]);
            if (strUserSub != null) {
                Rlog.d(LOG_TAG, "getUserPreferredSubs: strUserSub = " + strUserSub);

                try {
                    String splitUserSub[] = strUserSub.split(",");

                    // There should be 6 fields in the user preferred settings.
                    if (splitUserSub.length == USER_PREF_SUB_FIELDS) {
                        if (!TextUtils.isEmpty(splitUserSub[0])) {
                            mUserPrefSubs.subscription[i].iccId = splitUserSub[0];
                        }
                        if (!TextUtils.isEmpty(splitUserSub[1])) {
                            mUserPrefSubs.subscription[i].appType = splitUserSub[1];
                        }
                        if (!TextUtils.isEmpty(splitUserSub[2])) {
                            mUserPrefSubs.subscription[i].appId = splitUserSub[2];
                        }

                        try {
                            int subStatus = Integer.parseInt(splitUserSub[3]);
                            mUserPrefSubs.subscription[i].subStatus =
                                SubscriptionStatus.values()[subStatus];
                        } catch (NumberFormatException ex) {
                            Rlog.e(LOG_TAG, "getUserPreferredSubs: NumberFormatException: " + ex);
                            mUserPrefSubs.subscription[i].subStatus =
                                SubscriptionStatus.SUB_INVALID;
                        }

                        try {
                            mUserPrefSubs.subscription[i].m3gppIndex =
                                Integer.parseInt(splitUserSub[4]);
                        } catch (NumberFormatException ex) {
                            Rlog.e(LOG_TAG,
                                    "getUserPreferredSubs:m3gppIndex: NumberFormatException: "
                                    + ex);
                            mUserPrefSubs.subscription[i].m3gppIndex =
                                Subscription.SUBSCRIPTION_INDEX_INVALID;
                        }

                        try {
                            mUserPrefSubs.subscription[i].m3gpp2Index =
                                Integer.parseInt(splitUserSub[5]);
                        } catch (NumberFormatException ex) {
                            Rlog.e(LOG_TAG,
                                    "getUserPreferredSubs:m3gpp2Index: NumberFormatException: "
                                    + ex);
                            mUserPrefSubs.subscription[i].m3gpp2Index =
                                Subscription.SUBSCRIPTION_INDEX_INVALID;
                        }

                    } else {
                        Rlog.e(LOG_TAG,
                                "getUserPreferredSubs: splitUserSub.length != "
                                + USER_PREF_SUB_FIELDS);
                        errorOnParsing = true;
                    }
                } catch (PatternSyntaxException pe) {
                    Rlog.e(LOG_TAG,
                            "getUserPreferredSubs: PatternSyntaxException while split : "
                            + pe);
                    errorOnParsing = true;

                }
            }

            if (strUserSub == null || errorOnParsing) {
                String defaultUserSub = "" + ","        // iccId
                    + "" + ","                          // app type
                    + "" + ","                          // app id
                    + Integer.toString(SubscriptionStatus.SUB_INVALID.ordinal()) // activate state
                    + "," + Subscription.SUBSCRIPTION_INDEX_INVALID   // 3gppIndex in the card
                    + "," + Subscription.SUBSCRIPTION_INDEX_INVALID;  // 3gpp2Index in the card

                Settings.Global.putString(mContext.getContentResolver(),
                        Settings.Global.MULTI_SIM_USER_PREFERRED_SUBS[i], defaultUserSub);

                mUserPrefSubs.subscription[i].iccId = null;
                mUserPrefSubs.subscription[i].appType = null;
                mUserPrefSubs.subscription[i].appId = null;
                mUserPrefSubs.subscription[i].subStatus = SubscriptionStatus.SUB_INVALID;
                mUserPrefSubs.subscription[i].m3gppIndex = Subscription.SUBSCRIPTION_INDEX_INVALID;
                mUserPrefSubs.subscription[i].m3gpp2Index = Subscription.SUBSCRIPTION_INDEX_INVALID;
            }

            mUserPrefSubs.subscription[i].subId = i;

            logd("getUserPreferredSubs: mUserPrefSubs.subscription[" + i + "] = "
                    + mUserPrefSubs.subscription[i]);
        }
    }

    private void saveUserPreferredSubscription(int subIndex, Subscription userPrefSub) {
        String userSub;
        if ((subIndex >= NUM_SUBSCRIPTIONS) || (userPrefSub == null)) {
            Rlog.d(LOG_TAG, "saveUserPreferredSubscription: INVALID PARAMETERS:"
                    + " subIndex = " + subIndex + " userPrefSub = " + userPrefSub);
            return;
        }

        // Update the user preferred sub
        mUserPrefSubs.subscription[subIndex].copyFrom(userPrefSub);
        mUserPrefSubs.subscription[subIndex].subId = subIndex;

        userSub = ((userPrefSub.iccId != null) ? userPrefSub.iccId : "") + ","
            + ((userPrefSub.appType != null) ? userPrefSub.appType : "") + ","
            + ((userPrefSub.appId != null) ? userPrefSub.appId : "") + ","
            + Integer.toString(userPrefSub.subStatus.ordinal()) + ","
            + Integer.toString(userPrefSub.m3gppIndex) + ","
            + Integer.toString(userPrefSub.m3gpp2Index);

        logd("saveUserPreferredSubscription: userPrefSub = " + userPrefSub);
        logd("saveUserPreferredSubscription: userSub = " + userSub);

        // Construct the string and store in Settings data base at subIndex.
        // update the user pref settings so that next time user is
        // not prompted of the subscriptions
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_USER_PREFERRED_SUBS[subIndex], userSub);
    }

    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    private void loge(String string) {
        Rlog.e(LOG_TAG, string);
    }

    public int getActiveSubscriptionsCount() {
        int activeSubCount = 0;
        for (int i = 0; i < mNumPhones; i++) {
            SubscriptionId sub = SubscriptionId.values()[i];
            if (getCurrentSubscriptionStatus(sub) == SubscriptionStatus.SUB_ACTIVATED) {
                activeSubCount++;
            }
        }
        Rlog.d(LOG_TAG, "count of subs activated " + activeSubCount);
        return activeSubCount;
    }

    public boolean isSetSubscriptionInProgress() {
        return mSetSubscriptionInProgress;
    }

    public void sendDefaultSubsInfo () {
        int prioritySubVal = MSimPhoneFactory.getPrioritySubscription();
        int defaultVoiceSubVal = MSimPhoneFactory.getVoiceSubscription();
        Rlog.d(LOG_TAG, "Multi Sim Subscription Values." + prioritySubVal + defaultVoiceSubVal);
        Message msgPrioritySub = Message.obtain(this,
                EVENT_SET_PRIORITY_SUBSCRIPTION_DONE, null);
        Message msgDefaultVoiceSub = Message.obtain(this,
                EVENT_SET_DEFAULT_VOICE_SUBSCRIPTION_DONE, null);
        ((RIL)mCi[0]).setPrioritySub(prioritySubVal, msgPrioritySub);
        ((RIL)mCi[0]).setDefaultVoiceSub(defaultVoiceSubVal, msgDefaultVoiceSub);
    }
}