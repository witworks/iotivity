/**
 * ***************************************************************
 *
 * Copyright 2016 Samsung Electronics All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ****************************************************************
 */

package org.iotivity.service.easysetup.mediator;

import android.util.Log;

import org.iotivity.base.OcException;
import org.iotivity.base.OcRepresentation;

/**
 * This class contains cloud server properties to be delivered to Enrollee
 */
public class CloudProp {
    private static final String TAG = CloudProp.class.getName();
    protected OcRepresentation mRep;
    protected String mCloudID;

    /**
     * Constructor
     */
    public CloudProp() {
        mRep = new OcRepresentation();
        mCloudID = "";
    }

    public void setCloudProp(String authCode, String authProvider, String ciServer)
    {
        try {
            mRep.setValue(ESConstants.OC_RSRVD_ES_AUTHCODE, authCode);
            mRep.setValue(ESConstants.OC_RSRVD_ES_AUTHPROVIDER, authProvider);
            mRep.setValue(ESConstants.OC_RSRVD_ES_CISERVER, ciServer);
        } catch (OcException e) {
            Log.e(TAG, "setCloudProp is failed.");
        }
    }

    public void setCloudID(String cloudID)
    {
        mCloudID = cloudID;
    }

    /**
     * This method returns the authCode used for the first registration to IoTivity cloud
     * @return AuthCode for sign-up to IoTivity cloud
     */
    public String getAuthCode()
    {
        try {
            if (mRep.hasAttribute(ESConstants.OC_RSRVD_ES_AUTHCODE))
                return mRep.getValue(ESConstants.OC_RSRVD_ES_AUTHCODE);
        } catch (OcException e) {
            Log.e(TAG, "getAuthCode is failed.");
        }
        return new String("");
    }

    /**
     * This method returns the auth provider which issued the given AuthCode
     * @return Auth provider which issued the given AuthCode
     */
    public String getAuthProvider()
    {
        try {
            if (mRep.hasAttribute(ESConstants.OC_RSRVD_ES_AUTHPROVIDER))
                return mRep.getValue(ESConstants.OC_RSRVD_ES_AUTHPROVIDER);
        } catch (OcException e) {
            Log.e(TAG, "getAuthProvider is failed.");
        }
        return new String("");
    }

	/**
     * This method returns the Cloud Interface server's URL to be registered
     * @return CI server's URL to be registered
     */
    public String getCiServer()
    {
        try {
            if (mRep.hasAttribute(ESConstants.OC_RSRVD_ES_CISERVER))
                return mRep.getValue(ESConstants.OC_RSRVD_ES_CISERVER);
        } catch (OcException e) {
            Log.e(TAG, "getCiServer is failed.");
        }
        return new String("");
    }

    /**
     * This method returns the Cloud Interface server's UUID
     * @return CI server's UUID
     */
    public String getCloudID()
    {
        return mCloudID;
    }

    public OcRepresentation toOCRepresentation()
    {
        return mRep;
    }
}
