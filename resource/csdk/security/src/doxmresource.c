//******************************************************************
//
// Copyright 2015 Intel Mobile Communications GmbH All Rights Reserved.
//
//-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
//-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
#include <stdlib.h>
#include <string.h>

#if HAVE_STRINGS_H
#include <strings.h>
#endif

#ifdef __WITH_DTLS__
#include "global.h"
#endif

#include "ocstack.h"
#include "oic_malloc.h"
#include "payload_logging.h"
#include "utlist.h"
#include "ocrandom.h"
#include "ocpayload.h"
#include "cainterface.h"
#include "ocserverrequest.h"
#include "resourcemanager.h"
#include "doxmresource.h"
#include "pstatresource.h"
#include "aclresource.h"
#include "psinterface.h"
#include "srmresourcestrings.h"
#include "securevirtualresourcetypes.h"
#include "credresource.h"
#include "srmutility.h"
#include "pinoxmcommon.h"

#define TAG  "SRM-DOXM"

/** Default cbor payload size. This value is increased in case of CborErrorOutOfMemory.
 * The value of payload size is increased until reaching belox max cbor size. */
static const uint8_t CBOR_SIZE = 255;

/** Max cbor size payload. */
static const uint16_t CBOR_MAX_SIZE = 4400;

/** DOXM Map size - Number of mandatory items. */
static const uint8_t DOXM_MAP_SIZE = 5;

static OicSecDoxm_t        *gDoxm = NULL;
static OCResourceHandle    gDoxmHandle = NULL;

static OicSecOxm_t gOicSecDoxmJustWorks = OIC_JUST_WORKS;
static OicSecDoxm_t gDefaultDoxm =
{
    NULL,                   /* OicUrn_t *oxmType */
    0,                      /* size_t oxmTypeLen */
    &gOicSecDoxmJustWorks,  /* uint16_t *oxm */
    1,                      /* size_t oxmLen */
    OIC_JUST_WORKS,         /* uint16_t oxmSel */
    SYMMETRIC_PAIR_WISE_KEY,/* OicSecCredType_t sct */
    false,                  /* bool owned */
    {.id = {0}},            /* OicUuid_t deviceID */
    false,                  /* bool dpc */
    {.id = {0}},            /* OicUuid_t owner */
};

void DeleteDoxmBinData(OicSecDoxm_t* doxm)
{
    if (doxm)
    {
        //Clean oxmType
        for (size_t i = 0; i < doxm->oxmTypeLen; i++)
        {
            OICFree(doxm->oxmType[i]);
        }
        OICFree(doxm->oxmType);

        //clean oxm
        OICFree(doxm->oxm);

        //Clean doxm itself
        OICFree(doxm);
    }
}

OCStackResult DoxmToCBORPayload(const OicSecDoxm_t *doxm, uint8_t **payload, size_t *size)
{
    if (NULL == doxm || NULL == payload || NULL != *payload || NULL == size)
    {
        return OC_STACK_INVALID_PARAM;
    }

    size_t cborLen = *size;
    if (0 == cborLen)
    {
        cborLen = CBOR_SIZE;
    }

    *payload = NULL;
    *size = 0;

    OCStackResult ret = OC_STACK_ERROR;

    CborEncoder encoder = { {.ptr = NULL }, .end = 0 };
    CborEncoder doxmMap = { {.ptr = NULL }, .end = 0 };

    CborError cborEncoderResult = CborNoError;

    uint8_t mapSize = DOXM_MAP_SIZE;

    if (doxm->oxmTypeLen > 0)
    {
        mapSize++;
    }
    if (doxm->oxmLen > 0)
    {
        mapSize++;
    }

    uint8_t *outPayload = (uint8_t *)OICCalloc(1, cborLen);
    VERIFY_NON_NULL(TAG, outPayload, ERROR);
    cbor_encoder_init(&encoder, outPayload, cborLen, 0);

    cborEncoderResult = cbor_encoder_create_map(&encoder, &doxmMap, mapSize);
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Doxm Map.");

    //OxmType -- Not Mandatory
    if (doxm->oxmTypeLen > 0)
    {
        cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_OXM_TYPE_NAME,
            strlen(OIC_JSON_OXM_TYPE_NAME));
        VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding oxmType Tag.");
        CborEncoder oxmType = { {.ptr = NULL }, .end = 0 };
        cborEncoderResult = cbor_encoder_create_array(&doxmMap, &oxmType, doxm->oxmTypeLen);
        VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding oxmType Array.");

        for (size_t i = 0; i < doxm->oxmTypeLen; i++)
        {
            cborEncoderResult = cbor_encode_text_string(&oxmType, doxm->oxmType[i],
                strlen(doxm->oxmType[i]));
            VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding oxmType Value.");
        }
        cborEncoderResult = cbor_encoder_close_container(&doxmMap, &oxmType);
        VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Closing oxmType.");
    }

    //Oxm -- Not Mandatory
    if (doxm->oxmLen > 0)
    {
        cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_OXM_NAME,
            strlen(OIC_JSON_OXM_NAME));
        VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding oxmName Tag.");
        CborEncoder oxm = { {.ptr = NULL }, .end = 0 };
        cborEncoderResult = cbor_encoder_create_array(&doxmMap, &oxm, doxm->oxmLen);
        VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding oxmName Array.");

        for (size_t i = 0; i < doxm->oxmLen; i++)
        {
            cborEncoderResult = cbor_encode_int(&oxm, doxm->oxm[i]);
            VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding oxmName Value");
        }
        cborEncoderResult = cbor_encoder_close_container(&doxmMap, &oxm);
        VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Closing oxmName.");
    }

    //OxmSel -- Mandatory
    cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_OXM_SEL_NAME,
        strlen(OIC_JSON_OXM_SEL_NAME));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Sel Tag.");
    cborEncoderResult = cbor_encode_int(&doxmMap, doxm->oxmSel);
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Sel Value.");

    //sct -- Mandatory
    cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_SUPPORTED_CRED_TYPE_NAME,
        strlen(OIC_JSON_SUPPORTED_CRED_TYPE_NAME));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Cred Type Tag");
    cborEncoderResult = cbor_encode_int(&doxmMap, doxm->sct);
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Cred Type Value.");

    //Owned -- Mandatory
    cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_OWNED_NAME,
        strlen(OIC_JSON_OWNED_NAME));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Owned Tag.");
    cborEncoderResult = cbor_encode_boolean(&doxmMap, doxm->owned);
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Owned Value.");

    //TODO: Need more clarification on deviceIDFormat field type.
#if 0
    //DeviceIdFormat -- Mandatory
    cJSON_AddNumberToObject(jsonDoxm, OIC_JSON_DEVICE_ID_FORMAT_NAME, doxm->deviceIDFormat);
#endif

    //DeviceId -- Mandatory
    cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_DEVICE_ID_NAME,
        strlen(OIC_JSON_DEVICE_ID_NAME));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Device Id Tag.");
    cborEncoderResult = cbor_encode_byte_string(&doxmMap, doxm->deviceID.id,
                                                sizeof(doxm->deviceID.id));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Device Id Value.");

    //DPC -- Mandatory
    cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_DPC_NAME,
        strlen(OIC_JSON_DPC_NAME));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding DPC Tag.");
    cborEncoderResult = cbor_encode_boolean(&doxmMap, doxm->dpc);
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding DPC Value.");

    //Owner -- Mandatory
    cborEncoderResult = cbor_encode_text_string(&doxmMap, OIC_JSON_OWNER_NAME,
        strlen(OIC_JSON_OWNER_NAME));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Owner tag.");
    cborEncoderResult = cbor_encode_byte_string(&doxmMap, doxm->owner.id,
                                                sizeof(doxm->owner.id));
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Adding Owner Value");

    cborEncoderResult = cbor_encoder_close_container(&encoder, &doxmMap);
    VERIFY_CBOR_SUCCESS(TAG, cborEncoderResult, "Failed Closing DoxmMap.");

    *size = encoder.ptr - outPayload;
    *payload = outPayload;
    ret = OC_STACK_OK;

exit:
    if ((CborErrorOutOfMemory == cborEncoderResult) && (cborLen < CBOR_MAX_SIZE))
    {
       // reallocate and try again!
       OICFree(outPayload);
       // Since the allocated initial memory failed, double the memory.
       cborLen += encoder.ptr - encoder.end;
       cborEncoderResult = CborNoError;
       ret = DoxmToCBORPayload(doxm, payload, &cborLen);
    }

    if ((CborNoError != cborEncoderResult) || (OC_STACK_OK != ret))
    {
       OICFree(outPayload);
       outPayload = NULL;
       *payload = NULL;
       *size = 0;
       ret = OC_STACK_ERROR;
    }

    return ret;
}

OCStackResult CBORPayloadToDoxm(const uint8_t *cborPayload, size_t size,
                                OicSecDoxm_t **secDoxm)
{
    if (NULL == cborPayload || NULL == secDoxm || NULL != *secDoxm)
    {
        return OC_STACK_INVALID_PARAM;
    }

    OCStackResult ret = OC_STACK_ERROR;
    *secDoxm = NULL;

    CborValue doxmCbor = { .parser = NULL };
    CborParser parser = { .end = NULL };
    CborError cborFindResult = CborNoError;
    int cborLen = size;
    if (0 == size)
    {
        cborLen = CBOR_SIZE;
    }
    cbor_parser_init(cborPayload, cborLen, 0, &parser, &doxmCbor);
    CborValue doxmMap = { .parser = NULL } ;
    OicSecDoxm_t *doxm = NULL;
    cborFindResult = cbor_value_enter_container(&doxmCbor, &doxmMap);
    VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Entering Doxm Map.")

    doxm = (OicSecDoxm_t *)OICCalloc(1, sizeof(*doxm));
    VERIFY_NON_NULL(TAG, doxm, ERROR);

    while (cbor_value_is_valid(&doxmMap))
    {
        char *name = NULL;
        size_t len = 0;
        cborFindResult = cbor_value_dup_text_string(&doxmMap, &name, &len, NULL);
        VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding Doxm Map Name.")
        cborFindResult = cbor_value_advance(&doxmMap);
        VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Advancing Doxm Map.")

        CborType type = cbor_value_get_type(&doxmMap);

        //OxmType -- not Mandatory
        if (0 == strcmp(OIC_JSON_OXM_TYPE_NAME, name))
        {
            CborValue oxmType = { .parser = NULL };

            cborFindResult = cbor_value_get_array_length(&doxmMap, &doxm->oxmTypeLen);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding oxmTypeLen.")
            VERIFY_SUCCESS(TAG, doxm->oxmTypeLen != 0, ERROR);

            doxm->oxmType = (OicUrn_t *)OICCalloc(doxm->oxmTypeLen, sizeof(*doxm->oxmType));
            VERIFY_NON_NULL(TAG, doxm->oxmType, ERROR);

            cborFindResult = cbor_value_enter_container(&doxmMap, &oxmType);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Entering oxmType Array.")

            int i = 0;
            size_t len = 0;
            while (cbor_value_is_valid(&oxmType))
            {
                cborFindResult = cbor_value_dup_text_string(&oxmType, &doxm->oxmType[i++],
                                                            &len, NULL);
                VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding omxType text string.")
                cborFindResult = cbor_value_advance(&oxmType);
                VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Advancing oxmType.")
            }
        }

        //Oxm -- not Mandatory
        if (0 == strcmp(OIC_JSON_OXM_NAME, name))
        {
            CborValue oxm = { .parser = NULL };

            cborFindResult = cbor_value_get_array_length(&doxmMap, &doxm->oxmLen);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding oxmName array Length.")
            VERIFY_SUCCESS(TAG, doxm->oxmLen != 0, ERROR);

            doxm->oxm = (OicSecOxm_t *)OICCalloc(doxm->oxmLen, sizeof(*doxm->oxm));
            VERIFY_NON_NULL(TAG, doxm->oxmType, ERROR);

            cborFindResult = cbor_value_enter_container(&doxmMap, &oxm);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Entering oxmName Array.")

            int i = 0;
            while (cbor_value_is_valid(&oxm))
            {
                cborFindResult = cbor_value_get_int(&oxm, (int *) &doxm->oxm[i++]);
                VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding oxmName Value")
                cborFindResult = cbor_value_advance(&oxm);
                VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Advancing oxmName.")
            }
        }

        if (0 == strcmp(OIC_JSON_OXM_SEL_NAME, name))
        {
            cborFindResult = cbor_value_get_int(&doxmMap, (int *) &doxm->oxmSel);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding Sel Name Value.")
        }

        if (0 == strcmp(OIC_JSON_SUPPORTED_CRED_TYPE_NAME, name))
        {
            cborFindResult = cbor_value_get_int(&doxmMap, (int *) &doxm->sct);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding Sct Name Value.")
        }

        if (0 == strcmp(OIC_JSON_OWNED_NAME, name))
        {
            cborFindResult = cbor_value_get_boolean(&doxmMap, &doxm->owned);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding Owned Value.")
        }

        if (0 == strcmp(OIC_JSON_DPC_NAME, name))
        {
            cborFindResult = cbor_value_get_boolean(&doxmMap, &doxm->dpc);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding DPC Value.")
        }

        if (0 == strcmp(OIC_JSON_DEVICE_ID_NAME, name))
        {
            uint8_t *id = NULL;
            cborFindResult = cbor_value_dup_byte_string(&doxmMap, &id, &len, NULL);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding DeviceId Value.")
            memcpy(doxm->deviceID.id, id, len);
            OICFree(id);
        }
        if (0 == strcmp(OIC_JSON_OWNER_NAME, name))
        {
            uint8_t *id = NULL;
            cborFindResult = cbor_value_dup_byte_string(&doxmMap, &id , &len, NULL);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Finding Owner Name Value.")
            memcpy(doxm->owner.id, id, len);
            OICFree(id);
        }
        if (CborMapType != type && cbor_value_is_valid(&doxmMap))
        {
            cborFindResult = cbor_value_advance(&doxmMap);
            VERIFY_CBOR_SUCCESS(TAG, cborFindResult, "Failed Advancing DoxmMap.")
        }
        OICFree(name);
    }

    *secDoxm = doxm;
    ret = OC_STACK_OK;

exit:
    if (CborNoError != cborFindResult)
    {
        OIC_LOG (ERROR, TAG, "CBORPayloadToDoxm failed!!!");
        DeleteDoxmBinData(doxm);
        doxm = NULL;
        ret = OC_STACK_ERROR;
    }
    return ret;
}

/**
 * @todo document this function including why code might need to call this.
 * The current suspicion is that it's not being called as much as it should.
 */
static bool UpdatePersistentStorage(OicSecDoxm_t * doxm)
{
    bool bRet = false;

    if (NULL != doxm)
    {
        // Convert Doxm data into CBOR for update to persistent storage
        uint8_t *payload = NULL;
        size_t size = 0;
        OCStackResult res = DoxmToCBORPayload(doxm, &payload, &size);
        if (payload && (OC_STACK_OK == res)
            && (OC_STACK_OK == UpdateSecureResourceInPS(OIC_JSON_DOXM_NAME, payload, size)))
        {
                bRet = true;
        }
        OICFree(payload);
    }

    return bRet;
}

static bool ValidateQuery(const char * query)
{
    // Send doxm resource data if the state of doxm resource
    // matches with the query parameters.
    // else send doxm resource data as NULL
    // TODO Remove this check and rely on Policy Engine
    // and Provisioning Mode to enforce provisioning-state
    // access rules. Eventually, the PE and PM code will
    // not send a request to the /doxm Entity Handler at all
    // if it should not respond.
    OIC_LOG (DEBUG, TAG, "In ValidateQuery");
    if(NULL == gDoxm)
    {
        return false;
    }

    bool bOwnedQry = false;         // does querystring contains 'owned' query ?
    bool bOwnedMatch = false;       // does 'owned' query value matches with doxm.owned status?
    bool bDeviceIDQry = false;      // does querystring contains 'deviceid' query ?
    bool bDeviceIDMatch = false;    // does 'deviceid' query matches with doxm.deviceid ?

    OicParseQueryIter_t parseIter = {.attrPos = NULL};

    ParseQueryIterInit((unsigned char*)query, &parseIter);

    while (GetNextQuery(&parseIter))
    {
        if (strncasecmp((char *)parseIter.attrPos, OIC_JSON_OWNED_NAME, parseIter.attrLen) == 0)
        {
            bOwnedQry = true;
            if ((strncasecmp((char *)parseIter.valPos, OIC_SEC_TRUE, parseIter.valLen) == 0) &&
                    (gDoxm->owned))
            {
                bOwnedMatch = true;
            }
            else if ((strncasecmp((char *)parseIter.valPos, OIC_SEC_FALSE, parseIter.valLen) == 0)
                    && (!gDoxm->owned))
            {
                bOwnedMatch = true;
            }
        }

        if (strncasecmp((char *)parseIter.attrPos, OIC_JSON_DEVICE_ID_NAME, parseIter.attrLen) == 0)
        {
            bDeviceIDQry = true;
            OicUuid_t subject = {.id={0}};

            memcpy(subject.id, parseIter.valPos, parseIter.valLen);
            if (0 == memcmp(&gDoxm->deviceID.id, &subject.id, sizeof(gDoxm->deviceID.id)))
            {
                bDeviceIDMatch = true;
            }
        }
    }

    return ((bOwnedQry ? bOwnedMatch : true) && (bDeviceIDQry ? bDeviceIDMatch : true));
}

static OCEntityHandlerResult HandleDoxmGetRequest (const OCEntityHandlerRequest * ehRequest)
{
    OCEntityHandlerResult ehRet = OC_EH_OK;

    OIC_LOG(DEBUG, TAG, "Doxm EntityHandle processing GET request");

    //Checking if Get request is a query.
    if (ehRequest->query)
    {
        OIC_LOG(DEBUG, TAG, "HandleDoxmGetRequest processing query");
        if (!ValidateQuery(ehRequest->query))
        {
            ehRet = OC_EH_ERROR;
        }
    }

    /*
     * For GET or Valid Query request return doxm resource CBOR payload.
     * For non-valid query return NULL json payload.
     * A device will 'always' have a default Doxm, so DoxmToCBORPayload will
     * return valid doxm resource json.
     */
    uint8_t *payload = NULL;

    if (ehRet == OC_EH_OK)
    {
        size_t size = 0;
        if (OC_STACK_OK != DoxmToCBORPayload(gDoxm, &payload, &size))
        {
            payload = NULL;
        }
    }

    // Send response payload to request originator
    if (OC_STACK_OK != SendSRMCBORResponse(ehRequest, ehRet, payload))
    {
        OIC_LOG(ERROR, TAG, "SendSRMCBORResponse failed in HandleDoxmGetRequest");
    }

    OICFree(payload);

    return ehRet;
}

static OCEntityHandlerResult HandleDoxmPutRequest(const OCEntityHandlerRequest * ehRequest)
{
    OIC_LOG (DEBUG, TAG, "Doxm EntityHandle  processing PUT request");
    OCEntityHandlerResult ehRet = OC_EH_ERROR;
    OicUuid_t emptyOwner = {.id = {0} };

    /*
     * Convert CBOR Doxm data into binary. This will also validate
     * the Doxm data received.
     */
    uint8_t *payload = ((OCSecurityPayload *)ehRequest->payload)->securityData1;
    OicSecDoxm_t *newDoxm = NULL;

    if (payload)
    {
        OCStackResult res = CBORPayloadToDoxm(payload, CBOR_SIZE, &newDoxm);

        if (newDoxm && OC_STACK_OK == res)
        {
            // Iotivity SRM ONLY supports OIC_JUST_WORKS now
            if (OIC_JUST_WORKS == newDoxm->oxmSel)
            {
                /*
                 * If current state of the device is un-owned, enable
                 * anonymous ECDH cipher in tinyDTLS so that Provisioning
                 * tool can initiate JUST_WORKS ownership transfer process.
                 */
                if ((false == gDoxm->owned) && (false == newDoxm->owned))
                {
                    OIC_LOG (INFO, TAG, "Doxm EntityHandle  enabling AnonECDHCipherSuite");
#ifdef __WITH_DTLS__
                    ehRet = (CAEnableAnonECDHCipherSuite(true) == CA_STATUS_OK) ? OC_EH_OK : OC_EH_ERROR;
#endif //__WITH_DTLS__
                    goto exit;
                }
                else
                {
#ifdef __WITH_DTLS__
                    //Save the owner's UUID to derive owner credential
                    memcpy(&(gDoxm->owner), &(newDoxm->owner), sizeof(OicUuid_t));

                    // OCServerRequest *request = (OCServerRequest *)ehRequest->requestHandle;
                    // Generating OwnerPSK
                    // OIC_LOG (INFO, TAG, "Doxm EntityHandle  generating OwnerPSK");
                    // Generate new credential for provisioning tool
                    // ehRet = AddOwnerPSK((CAEndpoint_t *)&request->devAddr, newDoxm,
                    //       (uint8_t*) OXM_JUST_WORKS, strlen(OXM_JUST_WORKS));
                    // VERIFY_SUCCESS(TAG, OC_EH_OK == ehRet, ERROR);

                    // Update new state in persistent storage
                    if (true == UpdatePersistentStorage(gDoxm))
                    {
                        ehRet = OC_EH_OK;
                    }
                    else
                    {
                        OIC_LOG(ERROR, TAG, "Failed to update DOXM in persistent storage");
                        ehRet = OC_EH_ERROR;
                    }

                   /*
                    * Disable anonymous ECDH cipher in tinyDTLS since device is now
                    * in owned state.
                    */
                    CAResult_t caRes = CA_STATUS_OK;
                    caRes = CAEnableAnonECDHCipherSuite(false);
                    VERIFY_SUCCESS(TAG, caRes == CA_STATUS_OK, ERROR);
                    OIC_LOG(INFO, TAG, "ECDH_ANON CipherSuite is DISABLED");

#ifdef __WITH_X509__
#define TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8 0xC0AE
                    CASelectCipherSuite(TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8);
#endif //__WITH_X509__
#endif //__WITH_DTLS__
                }
            }
        }
        else if (OIC_RANDOM_DEVICE_PIN == newDoxm->oxmSel)
        {
            if ((false == gDoxm->owned) && (false == newDoxm->owned))
            {
                /*
                 * If current state of the device is un-owned, enable
                 * anonymous ECDH cipher in tinyDTLS so that Provisioning
                 * tool can initiate JUST_WORKS ownership transfer process.
                 */
                if(memcmp(&(newDoxm->owner), &emptyOwner, sizeof(OicUuid_t)) == 0)
                {
                    gDoxm->oxmSel = newDoxm->oxmSel;
                    //Update new state in persistent storage
                    if ((UpdatePersistentStorage(gDoxm) == true))
                    {
                        ehRet = OC_EH_OK;
                    }
                    else
                    {
                        OIC_LOG(WARNING, TAG, "Failed to update DOXM in persistent storage");
                        ehRet = OC_EH_ERROR;
                    }

#ifdef __WITH_DTLS__
                    CAResult_t caRes = CA_STATUS_OK;

                    caRes = CAEnableAnonECDHCipherSuite(false);
                    VERIFY_SUCCESS(TAG, caRes == CA_STATUS_OK, ERROR);
                    OIC_LOG(INFO, TAG, "ECDH_ANON CipherSuite is DISABLED");

                    caRes = CASelectCipherSuite(TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA_256);
                    VERIFY_SUCCESS(TAG, caRes == CA_STATUS_OK, ERROR);

                    char ranPin[OXM_RANDOM_PIN_SIZE + 1] = {0,};
                    if(OC_STACK_OK == GeneratePin(ranPin, OXM_RANDOM_PIN_SIZE + 1))
                    {
                        //Set the device id to derive temporal PSK
                        SetUuidForRandomPinOxm(&gDoxm->deviceID);

                        /**
                         * Since PSK will be used directly by DTLS layer while PIN based ownership transfer,
                         * Credential should not be saved into SVR.
                         * For this reason, use a temporary get_psk_info callback to random PIN OxM.
                         */
                        caRes = CARegisterDTLSCredentialsHandler(GetDtlsPskForRandomPinOxm);
                        VERIFY_SUCCESS(TAG, caRes == CA_STATUS_OK, ERROR);
                        ehRet = OC_EH_OK;
                    }
                    else
                    {
                        OIC_LOG(ERROR, TAG, "Failed to generate random PIN");
                        ehRet = OC_EH_ERROR;
                    }
#endif //__WITH_DTLS__
                }
                else
                {
#ifdef __WITH_DTLS__
                    //Save the owner's UUID to derive owner credential
                    memcpy(&(gDoxm->owner), &(newDoxm->owner), sizeof(OicUuid_t));

                    //Update new state in persistent storage
                    if (UpdatePersistentStorage(gDoxm) == true)
                    {
                        ehRet = OC_EH_OK;
                    }
                    else
                    {
                        OIC_LOG(ERROR, TAG, "Failed to update DOXM in persistent storage");
                        ehRet = OC_EH_ERROR;
                    }
#endif
                }
            }
        }

        /*
         * When current state of the device is un-owned and Provisioning
         * Tool is attempting to change the state to 'Owned' with a
         * qualified value for the field 'Owner'
         */
        if ((false == gDoxm->owned) && (true == newDoxm->owned) &&
            (memcmp(&(gDoxm->owner), &(newDoxm->owner), sizeof(OicUuid_t)) == 0))
        {
            gDoxm->owned = true;
            // Update new state in persistent storage
            if (UpdatePersistentStorage(gDoxm))
            {
                //Update default ACL of security resource to prevent anonymous user access.
                if(OC_STACK_OK == UpdateDefaultSecProvACL())
                {
                    ehRet = OC_EH_OK;
                }
                else
                {
                    OIC_LOG(ERROR, TAG, "Failed to remove default ACL for security provisioning");
                    ehRet = OC_EH_ERROR;
                }
            }
            else
            {
                OIC_LOG(ERROR, TAG, "Failed to update DOXM in persistent storage");
                ehRet = OC_EH_ERROR;
            }
        }
    }

exit:
    if(OC_EH_OK != ehRet)
    {
        OIC_LOG(WARNING, TAG, "The operation failed during handle DOXM request,"\
                            "DOXM will be reverted.");

        /*
         * If some error is occured while ownership transfer,
         * ownership transfer related resource should be revert back to initial status.
         */
        RestoreDoxmToInitState();
        RestorePstatToInitState();
    }

    //Send payload to request originator
    if (OC_STACK_OK != SendSRMCBORResponse(ehRequest, ehRet, NULL))
    {
        OIC_LOG(ERROR, TAG, "SendSRMCBORResponse failed in HandleDoxmPostRequest");
    }
    DeleteDoxmBinData(newDoxm);

    return ehRet;
}

OCEntityHandlerResult DoxmEntityHandler(OCEntityHandlerFlag flag,
                                        OCEntityHandlerRequest * ehRequest,
                                        void* callbackParam)
{
    (void)callbackParam;
    OCEntityHandlerResult ehRet = OC_EH_ERROR;

    if(NULL == ehRequest)
    {
        return ehRet;
    }

    if (flag & OC_REQUEST_FLAG)
    {
        OIC_LOG(DEBUG, TAG, "Flag includes OC_REQUEST_FLAG");

        switch (ehRequest->method)
        {
            case OC_REST_GET:
                ehRet = HandleDoxmGetRequest(ehRequest);
                break;

            case OC_REST_PUT:
                ehRet = HandleDoxmPutRequest(ehRequest);
                break;

            default:
                ehRet = OC_EH_ERROR;
                SendSRMCBORResponse(ehRequest, ehRet, NULL);
                break;
        }
    }

    return ehRet;
}

OCStackResult CreateDoxmResource()
{
    OCStackResult ret = OCCreateResource(&gDoxmHandle,
                                         OIC_RSRC_TYPE_SEC_DOXM,
                                         OIC_MI_DEF,
                                         OIC_RSRC_DOXM_URI,
                                         DoxmEntityHandler,
                                         NULL,
                                         OC_OBSERVABLE | OC_SECURE |
                                         OC_EXPLICIT_DISCOVERABLE);

    if (OC_STACK_OK != ret)
    {
        OIC_LOG (FATAL, TAG, "Unable to instantiate Doxm resource");
        DeInitDoxmResource();
    }
    return ret;
}

/**
 * Checks if DeviceID is generated during provisioning for the new device.
 * If DeviceID is NULL then generates the new DeviceID.
 * Once DeviceID is assigned to the device it does not change for the lifetime of the device.
 */
static OCStackResult CheckDeviceID()
{
    OCStackResult ret = OC_STACK_ERROR;
    bool validId = false;
    for (uint8_t i = 0; i < UUID_LENGTH; i++)
    {
        if (gDoxm->deviceID.id[i] != 0)
        {
            validId = true;
            break;
        }
    }

    if (!validId)
    {
        if (OCGenerateUuid(gDoxm->deviceID.id) != RAND_UUID_OK)
        {
            OIC_LOG(FATAL, TAG, "Generate UUID for Server Instance failed!");
            return ret;
        }
        ret = OC_STACK_OK;

        if (UpdatePersistentStorage(gDoxm))
        {
            //TODO: After registering PSI handler in all samples, do ret = OC_STACK_OK here.
            OIC_LOG(FATAL, TAG, "UpdatePersistentStorage failed!");
        }
    }
    else
    {
        ret = OC_STACK_OK;
    }
    return ret;
}

/**
 * Get the default value.
 *
 * @return the default value of doxm, @ref OicSecDoxm_t.
 */
static OicSecDoxm_t* GetDoxmDefault()
{
    OIC_LOG(DEBUG, TAG, "GetDoxmToDefault");
    return &gDefaultDoxm;
}

const OicSecDoxm_t* GetDoxmResourceData()
{
    return gDoxm;
}

OCStackResult InitDoxmResource()
{
    OCStackResult ret = OC_STACK_ERROR;

    //Read DOXM resource from PS
    uint8_t *data = NULL;
    size_t size = 0;
    ret = GetSecureVirtualDatabaseFromPS(OIC_JSON_DOXM_NAME, &data, &size);
    // If database read failed
    if (OC_STACK_OK != ret)
    {
       OIC_LOG (DEBUG, TAG, "ReadSVDataFromPS failed");
    }
    if (data)
    {
       // Read DOXM resource from PS
       ret = CBORPayloadToDoxm(data, size, &gDoxm);
    }
    /*
     * If SVR database in persistent storage got corrupted or
     * is not available for some reason, a default doxm is created
     * which allows user to initiate doxm provisioning again.
     */
     if ((OC_STACK_OK != ret) || !data || !gDoxm)
    {
        gDoxm = GetDoxmDefault();
    }

    //In case of the server is shut down unintentionally, we should initialize the owner
    if(false == gDoxm->owned)
    {
        OicUuid_t emptyUuid = {.id={0}};
        memcpy(&gDoxm->owner, &emptyUuid, sizeof(OicUuid_t));
    }

    ret = CheckDeviceID();
    if (ret == OC_STACK_OK)
    {
        //Instantiate 'oic.sec.doxm'
        ret = CreateDoxmResource();
    }
    else
    {
        OIC_LOG (ERROR, TAG, "CheckDeviceID failed");
    }
    OICFree(data);
    return ret;
}

OCStackResult DeInitDoxmResource()
{
    OCStackResult ret = OCDeleteResource(gDoxmHandle);
    if (gDoxm  != &gDefaultDoxm)
    {
        DeleteDoxmBinData(gDoxm);
    }
    gDoxm = NULL;

    if (OC_STACK_OK == ret)
    {
        return OC_STACK_OK;
    }
    else
    {
        return OC_STACK_ERROR;
    }
}

OCStackResult GetDoxmDeviceID(OicUuid_t *deviceID)
{
    if (deviceID && gDoxm)
    {
       *deviceID = gDoxm->deviceID;
        return OC_STACK_OK;
    }
    return OC_STACK_ERROR;
}

OCStackResult GetDoxmDevOwnerId(OicUuid_t *devOwner)
{
    OCStackResult retVal = OC_STACK_ERROR;
    if (gDoxm)
    {
        if (gDoxm->owned)
        {
            *devOwner = gDoxm->owner; // TODO change to devOwner when available
            retVal = OC_STACK_OK;
        }
    }
    return retVal;
}

/**
 * Function to restore doxm resurce to initial status.
 * This function will use in case of error while ownership transfer
 */
void RestoreDoxmToInitState()
{
    if(gDoxm)
    {
        OIC_LOG(INFO, TAG, "DOXM resource will revert back to initial status.");

        OicUuid_t emptyUuid = {.id={0}};
        memcpy(&(gDoxm->owner), &emptyUuid, sizeof(OicUuid_t));
        gDoxm->owned = false;
        gDoxm->oxmSel = OIC_JUST_WORKS;

        if(!UpdatePersistentStorage(gDoxm))
        {
            OIC_LOG(ERROR, TAG, "Failed to revert DOXM in persistent storage");
        }
    }
}
