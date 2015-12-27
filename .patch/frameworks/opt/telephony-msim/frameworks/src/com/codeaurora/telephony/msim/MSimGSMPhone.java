/*
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2006 The Android Open Source Project
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

package com.codeaurora.telephony.msim;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.net.Uri;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyProperties;

import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.PhoneConstants;

import static com.android.internal.telephony.MSimConstants.EVENT_SUBSCRIPTION_ACTIVATED;
import static com.android.internal.telephony.MSimConstants.EVENT_SUBSCRIPTION_DEACTIVATED;

public class MSimGSMPhone extends GSMPhone {
    // Holds the subscription information
    private Subscription mSubscriptionData = null;
    private int mSubscription = 0;
    NotificationManager mNotificationManager;

    // Call Forward icons. Values have to be same as mentioned in
    // NotificationMgr.java
    private static final int CALL_FORWARD_NOTIFICATION = 6;
    private static final int CALL_FORWARD_NOTIFICATION_SUB2 = 21;

    public
    MSimGSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, int subscription) {
        this(context, ci, notifier, false, subscription);
    }

    public
    MSimGSMPhone (Context context, CommandsInterface ci,
            PhoneNotifier notifier, boolean unitTestMode, int subscription) {
        super(context, ci, notifier, unitTestMode);

        mSubscription = subscription;

        log("MSimGSMPhone: constructor: sub = " + mSubscription);

        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);

        mDcTracker = new MSimDcTracker (this);

        SubscriptionManager subMgr = SubscriptionManager.getInstance();
        subMgr.registerForSubscriptionActivated(mSubscription,
                this, EVENT_SUBSCRIPTION_ACTIVATED, null);
        subMgr.registerForSubscriptionDeactivated(mSubscription,
                this, EVENT_SUBSCRIPTION_DEACTIVATED, null);

        setProperties();
    }

    @Override
    public void dispose() {
        super.dispose();

        SubscriptionManager subMgr = SubscriptionManager.getInstance();
        subMgr.unregisterForSubscriptionActivated(mSubscription, this);
        subMgr.unregisterForSubscriptionDeactivated(mSubscription, this);
    }

    @Override
    public void handleMessage(Message msg) {
        if (!mIsTheCurrentActivePhone) {
            log("Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case EVENT_SUBSCRIPTION_ACTIVATED:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                break;

            case EVENT_SUBSCRIPTION_DEACTIVATED:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                break;

            case EVENT_GET_BASEBAND_VERSION_DONE:
                AsyncResult ar;
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                log("Baseband version: " + ar.result);
                super.setSystemProperty(TelephonyProperties.PROPERTY_BASEBAND_VERSION,
                        (String)ar.result);
            break;

            default:
                super.handleMessage(msg);
        }
    }

    private void onSubscriptionActivated() {
        SubscriptionManager subMgr = SubscriptionManager.getInstance();
        mSubscriptionData = subMgr.getCurrentSubscription(mSubscription);

        log("SUBSCRIPTION ACTIVATED : slotId : " + mSubscriptionData.slotId
                + " appid : " + mSubscriptionData.m3gppIndex
                + " subId : " + mSubscriptionData.subId
                + " subStatus : " + mSubscriptionData.subStatus);

        // Make sure properties are set for proper subscription.
        setProperties();

        onUpdateIccAvailability();
        mSST.sendMessage(mSST.obtainMessage(ServiceStateTracker.EVENT_ICC_CHANGED));
        ((MSimDcTracker)mDcTracker).updateRecords();

        // read the subscription specifics now
        mCi.getIMEI(obtainMessage(EVENT_GET_IMEI_DONE));
        mCi.getBasebandVersion(obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));
        mCi.getIMEISV(obtainMessage(EVENT_GET_IMEISV_DONE));
    }

    private void onSubscriptionDeactivated() {
        log("SUBSCRIPTION DEACTIVATED");
        resetSubSpecifics();
    }

    public void resetSubSpecifics() {
        if (getCallForwardingIndicator()) {
            int notificationId = (mSubscription == 0) ? CALL_FORWARD_NOTIFICATION :
                    CALL_FORWARD_NOTIFICATION_SUB2;
            mNotificationManager.cancel(notificationId);
        }

    }

    //Gets Subscription information in the Phone Object
    public Subscription getSubscriptionInfo() {
        return mSubscriptionData;
    }

    /**
     * Returns the subscription id.
     */
    @Override
    public int getSubscription() {
        return mSubscription;
    }

    /**
     * Initialize the MultiSim Specifics here.
     * Should be called from the base class constructor
     */
    @Override
    protected void initSubscriptionSpecifics() {
        mSST = new MSimGsmServiceStateTracker(this);
    }

    // Set the properties per subscription
    @Override
    protected void setProperties() {
        //Change the system property
        MSimTelephonyManager.setTelephonyProperty(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                mSubscription,
                new Integer(PhoneConstants.PHONE_TYPE_GSM).toString());
    }

    @Override
    protected void storeVoiceMailNumber(String number) {
        SharedPreferences sp =
            PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString((VM_NUMBER + mSubscription), number);
        editor.apply();
        setVmSimImsi(getSubscriberId());
    }

    @Override
    public String getVoiceMailNumber() {
        // Read from the SIM. If its null, try reading from the
        // shared preference area.
        IccRecords r = mIccRecords.get();
        String number = (r != null) ? r.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp =
                PreferenceManager.getDefaultSharedPreferences(getContext());
            number = sp.getString((VM_NUMBER + mSubscription), null);
        }
        return number;
    }

    @Override
    protected String getVmSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString((VM_SIM_IMSI + mSubscription), null);
    }

    @Override
    protected void setVmSimImsi(String imsi) {
        SharedPreferences sp =
            PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor =
            sp.edit();
        editor.putString((VM_SIM_IMSI + mSubscription), imsi);
        editor.apply();
    }

    @Override
    protected UiccCardApplication getUiccCardApplication() {
        return  ((MSimUiccController) mUiccController).getUiccCardApplication(SubscriptionManager.
                getInstance().getSlotId(mSubscription), UiccController.APP_FAM_3GPP);
    }

    @Override
    public void setSystemProperty(String property, String value) {
        if(getUnitTestMode()) {
            return;
        }
        MSimTelephonyManager.setTelephonyProperty(property, mSubscription, value);
    }

    @Override
    public void updatePhoneObject(int voiceRadioTech) {
        // Only the PhoneProxy can update the phone object.
        MSimPhoneFactory.getPhone(mSubscription).updatePhoneObject(voiceRadioTech);
    }

    public String getSystemProperty(String property, String defValue) {
        if(getUnitTestMode()) {
            return null;
        }
        return MSimTelephonyManager.getTelephonyProperty(property, mSubscription, defValue);
    }

    public void updateDataConnectionTracker() {
        ((MSimDcTracker)mDcTracker).update();
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((MSimDcTracker)mDcTracker)
                .setInternalDataEnabled(enable, onCompleteMsg);
    }


    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((MSimDcTracker)mDcTracker)
                .setInternalDataEnabledFlag(enable);
    }

    /**
     * @return operator numeric.
     */
    public String getOperatorNumeric() {
        String operatorNumeric = null;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            operatorNumeric = r.getOperatorNumeric();
        }
        return operatorNumeric;
    }

    /**
     * Sets the "current" field in the telephony provider according to the operator numeric.
     *
     * @return true for success; false otherwise.
     */
    public boolean updateCurrentCarrierInProvider() {
        int currentDds = MSimPhoneFactory.getDataSubscription();
        String operatorNumeric = getOperatorNumeric();

        log("updateCurrentCarrierInProvider: mSubscription = " + getSubscription()
                + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);

        if (!TextUtils.isEmpty(operatorNumeric) && (getSubscription() == currentDds)) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "current");
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    /**
     * This method stores the CF_ENABLED flag in preferences
     * @param enabled
     */
    @Override
    protected void setCallForwardingPreference(boolean enabled) {
        log("Set callforwarding info to perferences for sub = "
                + mSubscription + " enabled = " + enabled);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor edit = sp.edit();
        edit.putBoolean((CF_ENABLED + mSubscription), enabled);
        edit.commit();

        // Using the same method as VoiceMail to be able to track when the sim card is changed.
        setVmSimImsi(getSubscriberId());
    }

    @Override
    protected boolean getCallForwardingPreference() {
        log("Get callforwarding info from perferences for sub = "
                + mSubscription);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean cf = sp.getBoolean((CF_ENABLED + mSubscription), false);
        log("CF enabled = " + cf);
        return cf;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((MSimDcTracker)mDcTracker)
                .registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((MSimDcTracker)mDcTracker).unregisterForAllDataDisconnected(h);
    }

    @Override
    protected void setCardInPhoneBook() {
        log("setCardInPhoneBook: mSubscriptionData: " + mSubscriptionData);
        if (mUiccController == null || mSubscriptionData == null
                || mSubscriptionData.slotId == -1 || mSubscriptionData.
                subStatus != Subscription.SubscriptionStatus.SUB_ACTIVATED) {
            mSimPhoneBookIntManager.setIccCard(null);
            return;
        }
        UiccCard card = ((MSimUiccController)mUiccController).getUiccCard(mSubscriptionData.slotId);
        mSimPhoneBookIntManager.setIccCard(card);
    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[MSimGSMPhone] ["+mSubscription+"]" + s);
    }
}