/*
 * //******************************************************************
 * //
 * // Copyright 2016 Samsung Electronics All Rights Reserved.
 * //
 * //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
 * //
 * // Licensed under the Apache License, Version 2.0 (the "License");
 * // you may not use this file except in compliance with the License.
 * // You may obtain a copy of the License at
 * //
 * //      http://www.apache.org/licenses/LICENSE-2.0
 * //
 * // Unless required by applicable law or agreed to in writing, software
 * // distributed under the License is distributed on an "AS IS" BASIS,
 * // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * // See the License for the specific language governing permissions and
 * // limitations under the License.
 * //
 * //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
 */
package org.iotivity.cloud.ciserver.resources.proxy.account;

import java.util.Arrays;

import org.iotivity.cloud.base.connector.ConnectorPool;
import org.iotivity.cloud.base.device.Device;
import org.iotivity.cloud.base.device.IRequestChannel;
import org.iotivity.cloud.base.exception.ServerException;
import org.iotivity.cloud.base.protocols.IRequest;
import org.iotivity.cloud.base.protocols.MessageBuilder;
import org.iotivity.cloud.base.protocols.enums.RequestMethod;
import org.iotivity.cloud.base.resource.Resource;
import org.iotivity.cloud.ciserver.Constants;

public class Account extends Resource {
    IRequestChannel mAuthServer = null;

    public Account() {
        super(Arrays.asList(Constants.PREFIX_OIC, Constants.ACCOUNT_URI));

        mAuthServer = ConnectorPool.getConnection("account");
    }

    @Override
    public void onDefaultRequestReceived(Device srcDevice, IRequest request)
            throws ServerException {
        if (request.getMethod().equals(RequestMethod.DELETE)) {
            StringBuffer additionalQuery = new StringBuffer();
            additionalQuery
                    .append(Constants.USER_ID + "=" + srcDevice.getUserId());
            String uriQuery = request.getUriQuery() + ";"
                    + additionalQuery.toString();
            request = MessageBuilder.modifyRequest(request, null, uriQuery,
                    null, null);
        }
        mAuthServer.sendRequest(request, srcDevice);
    }
}
