/*
 * Copyright (C) 2011-2012 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

/**
 * MSimUiccController holds two UiccCards.
 */
public class MSimUiccController extends UiccController {
    private CommandsInterface[] mCis;
    private MSimUiccCard[] mUiccCards = new MSimUiccCard[MSimTelephonyManager.
                                                              getDefault().getPhoneCount()];
    public static MSimUiccController make(Context c, CommandsInterface[] ci) {
        synchronized (mLock) {
            if (mInstance != null) {
                throw new RuntimeException("MSimUiccController.make() should only be called once");
            }
            mInstance = new MSimUiccController(c, ci);
            return (MSimUiccController)mInstance;
        }
    }

    public static MSimUiccController getInstance() {
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException(
                        "MSimUiccController.getInstance can't be called before make()");
            }
            return (MSimUiccController)mInstance;
        }
    }

    public UiccCard getUiccCard(int slotId) {
        synchronized (mLock) {
            if (isValidCardIndex(slotId)) {
                return mUiccCards[slotId];
            }
            return null;
        }
    }

    public UiccCard[] getUiccCards() {
        // Return cloned array since we don't want to give out reference
        // to internal data structure.
        synchronized (mLock) {
            return mUiccCards.clone();
        }
    }

    // Easy to use API
    public UiccCardApplication getUiccCardApplication(int slotId, int family) {
        synchronized (mLock) {
            if (isValidCardIndex(slotId)) {
                UiccCard c = mUiccCards[slotId];
                if (c != null) {
                    return mUiccCards[slotId].getApplication(family);
                }
            }
            return null;
        }
    }

    // Easy to use API
    public IccRecords getIccRecords(int slotId, int family) {
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(slotId, family);
            if (app != null) {
                return app.getIccRecords();
            }
            return null;
        }
    }

    // Easy to use API
    public IccFileHandler getIccFileHandler(int slotId, int family) {
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(slotId, family);
            if (app != null) {
                return app.getIccFileHandler();
            }
            return null;
        }
    }

    @Override
    public void handleMessage (Message msg) {
        synchronized (mLock) {
            Integer index = getCiIndex(msg);

            if (index < 0 || index >= mCis.length) {
                Rlog.e(LOG_TAG, "Invalid index : " + index + " received with event " + msg.what);
                return;
            }

            switch (msg.what) {
                case EVENT_ICC_STATUS_CHANGED:
                    if (DBG) log("Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus"
                            + "on index " + index);
                    mCis[index].getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, index));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    if (DBG) log("Received EVENT_GET_ICC_STATUS_DONE on index " + index);
                    AsyncResult ar = (AsyncResult)msg.obj;
                    onGetIccCardStatusDone(ar, index);
                    break;
                case EVENT_RADIO_UNAVAILABLE:
                    if (DBG) log("EVENT_RADIO_UNAVAILABLE ");
                    disposeCard(mUiccCards[index]);
                    mUiccCards[index] = null;
                    mIccChangedRegistrants.notifyRegistrants(new AsyncResult(null, index, null));
                    break;
                default:
                    Rlog.e(LOG_TAG, " Unknown Event " + msg.what);
            }
        }
    }

    private MSimUiccController(Context c, CommandsInterface []ci) {
        if (DBG) log("Creating MSimUiccController");
        mContext = c;
        mCis = ci;
        for (int i = 0; i < mCis.length; i++) {
            Integer index = new Integer(i);
            mCis[i].registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, index);
            // TODO remove this once modem correctly notifies the unsols
            mCis[i].registerForOn(this, EVENT_ICC_STATUS_CHANGED, index);
            mCis[i].registerForNotAvailable(this, EVENT_RADIO_UNAVAILABLE, index);
        }
    }


    private Integer getCiIndex(Message msg) {
        AsyncResult ar;
        Integer index = new Integer(MSimConstants.DEFAULT_CARD_INDEX);

        /*
         * The events can be come in two ways. By explicitly sending it using
         * sendMessage, in this case the user object passed is msg.obj and from
         * the CommandsInterface, in this case the user object is msg.obj.userObj
         */
        if (msg != null) {
            if (msg.obj != null && msg.obj instanceof Integer) {
                index = (Integer)msg.obj;
            } else if(msg.obj != null && msg.obj instanceof AsyncResult) {
                ar = (AsyncResult)msg.obj;
                if (ar.userObj != null && ar.userObj instanceof Integer) {
                    index = (Integer)ar.userObj;
                }
            }
        }
        return index;
    }

    private boolean isValidCardIndex(int index) {
        return (index >= 0 && index < mUiccCards.length);
    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar, Integer index) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG,"Error getting ICC status. "
                    + "RIL_REQUEST_GET_ICC_STATUS should "
                    + "never return an error", ar.exception);
            return;
        }
        if (!isValidCardIndex(index)) {
            Rlog.e(LOG_TAG,"onGetIccCardStatusDone: invalid index : " + index);
            return;
        }

        IccCardStatus status = (IccCardStatus)ar.result;

        if (mUiccCards[index] == null) {
            //Create new card
            mUiccCards[index] = new MSimUiccCard(mContext, mCis[index], status, index);

            // Update the UiccCard in base class, so that if someone calls
            // UiccManager.getUiccCard(), it will return the default card.
            if (index == MSimConstants.DEFAULT_CARD_INDEX) {
                mUiccCard = mUiccCards[index];
            }
        } else {
            //Update already existing card
            mUiccCards[index].update(mContext, mCis[index] , status);
        }

        if (DBG) log("Notifying IccChangedRegistrants");
        mIccChangedRegistrants.notifyRegistrants(new AsyncResult(null, index, null));

    }

    private void log(String string) {
        Rlog.d(LOG_TAG, string);
    }
}