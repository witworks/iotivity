package org.iotivity.cloud.ciserver.resources.proxy.rd;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.iotivity.cloud.base.device.CoapDevice;
import org.iotivity.cloud.base.device.IRequestChannel;
import org.iotivity.cloud.base.exception.ClientException;
import org.iotivity.cloud.base.protocols.IRequest;
import org.iotivity.cloud.base.protocols.IResponse;
import org.iotivity.cloud.base.protocols.MessageBuilder;
import org.iotivity.cloud.base.protocols.coap.CoapRequest;
import org.iotivity.cloud.base.protocols.enums.ContentFormat;
import org.iotivity.cloud.base.protocols.enums.RequestMethod;
import org.iotivity.cloud.base.protocols.enums.ResponseStatus;
import org.iotivity.cloud.ciserver.Constants;
import org.iotivity.cloud.ciserver.DeviceServerSystem;
import org.iotivity.cloud.util.Cbor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ResourcePresenceTest {
    public static final String DEVICE_PRS_REQ_URI = Constants.DEVICE_PRESENCE_FULL_URI;
    public static final String DEVICE_LIST_KEY    = "devices";
    public static final String RES_PRS_URI        = Constants.RESOURCE_PRESENCE_FULL_URI;
    private String             di                 = "B371C481-38E6-4D47-8320-7688D8A5B58C";
    private CoapDevice         mockDevice         = mock(CoapDevice.class);
    IResponse                  res                = null;
    IRequest                   req                = null;
    DeviceServerSystem         deviceServerSystem = new DeviceServerSystem();
    final CountDownLatch       latch              = new CountDownLatch(1);
    @Mock
    IRequestChannel            requestChannel;
    @InjectMocks
    ResourcePresence           adHandler          = new ResourcePresence();

    @Before
    public void setUp() throws Exception {
        res = null;
        req = null;
        Mockito.doReturn("mockDeviceId").when(mockDevice).getDeviceId();
        MockitoAnnotations.initMocks(this);
        deviceServerSystem.addResource(adHandler);
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public CoapRequest answer(InvocationOnMock invocation)
                    throws Throwable {
                Object[] args = invocation.getArguments();
                CoapRequest request = (CoapRequest) args[0];
                System.out.println(
                        "\t----------payload : " + request.getPayloadString());
                System.out.println(
                        "\t----------uripath : " + request.getUriPath());
                System.out.println(
                        "\t---------uriquery : " + request.getUriQuery());
                req = request;
                latch.countDown();
                return request;
            }
        }).when(requestChannel).sendRequest(Mockito.any(IRequest.class),
                Mockito.any(CoapDevice.class));
    }

    @Test
    public void testResourcePresenceEntireResourcesOnRequestReceived()
            throws Exception {
        System.out.println(
                "\t--------------OnRequestReceived(RD) Resource Presence (entire resource) Test------------");
        IRequest request = MessageBuilder.createRequest(RequestMethod.GET,
                RES_PRS_URI, null);
        deviceServerSystem.onRequestReceived(mockDevice, request);
        HashMap<String, List<String>> queryMap = req.getUriQueryMap();
        // assertion: if the request packet from the CI contains the query
        // which includes the accesstoken and the di
        assertTrue(latch.await(1L, SECONDS));
        assertTrue(queryMap.containsKey("mid"));
        assertEquals(req.getUriPath(), Constants.GROUP_FULL_URI + "/null");
    }

    @Test
    public void testResourcePresenceSpecificResourceOnRequestReceived()
            throws Exception {
        System.out.println(
                "\t--------------OnRequestReceived(RD) Resource Presence (specific resources) Test------------");
        IRequest request = MessageBuilder.createRequest(RequestMethod.GET,
                RES_PRS_URI, "di=" + di);
        deviceServerSystem.onRequestReceived(mockDevice, request);
        HashMap<String, List<String>> queryMap = req.getUriQueryMap();
        assertTrue(latch.await(1L, SECONDS));
        assertTrue(queryMap.containsKey("mid"));
        assertEquals(req.getUriPath(), Constants.GROUP_FULL_URI + "/null");
    }

    IRequest                               requestEntireDevices = MessageBuilder
            .createRequest(RequestMethod.GET, RES_PRS_URI, null);
    @InjectMocks
    ResourcePresence.AccountReceiveHandler entireDeviceHandler  = adHandler.new AccountReceiveHandler(
            requestEntireDevices, mockDevice);

    @Test
    public void testEntireDeviceonResponseReceived() throws ClientException {
        System.out.println(
                "\t--------------onResponseReceived(RD) Resource Presence (entire deivces) Test------------");
        IResponse responseFromAccountServer = responseFromAccountServer();
        entireDeviceHandler.onResponseReceived(responseFromAccountServer);
        HashMap<String, List<String>> queryMap = req.getUriQueryMap();
        // assertion : if query has pre-requested multiple di list given from
        // the AS
        assertTrue(req.getMethod() == RequestMethod.GET);
        assertTrue(queryMap.get("di").contains("device1"));
        assertTrue(queryMap.get("di").contains("device2"));
        assertTrue(queryMap.get("di").contains("device3"));
    }

    IRequest                               requestSpecificDevice = MessageBuilder
            .createRequest(RequestMethod.GET, RES_PRS_URI, "di=" + "device1");
    @InjectMocks
    ResourcePresence.AccountReceiveHandler specificDeviceHandler = adHandler.new AccountReceiveHandler(
            requestSpecificDevice, mockDevice);

    @Test
    public void testSpecificDeviceonResponseReceived() throws ClientException {
        System.out.println(
                "\t--------------onResponseReceived(RD) Resource Presence (specific deivce) Test------------");
        IResponse response = responseFromAccountServer();
        specificDeviceHandler.onResponseReceived(response);
        HashMap<String, List<String>> queryMap = req.getUriQueryMap();
        // assertion : if query has pre-requested di
        assertTrue(queryMap.get("di").contains("device1"));
        assertTrue(req.getMethod() == RequestMethod.GET);
    }

    private IResponse responseFromAccountServer() {
        // make response which has "CONTENT" status
        Cbor<HashMap<String, Object>> cbor = new Cbor<>();
        HashMap<String, Object> responsePayload = new HashMap<String, Object>();
        ArrayList<String> deviceList = new ArrayList<String>();
        // assuming that there are three devices in the response msg from the AS
        deviceList.add("device1");
        deviceList.add("device2");
        deviceList.add("device3");
        responsePayload.put("dilist", deviceList);
        responsePayload.put("gid", "g0001");
        responsePayload.put("gmid", "u0001");
        ArrayList<String> midList = new ArrayList<String>();
        midList.add("u0001");
        responsePayload.put("midlist", midList);
        IResponse response = MessageBuilder.createResponse(requestEntireDevices,
                ResponseStatus.CONTENT, ContentFormat.APPLICATION_CBOR,
                cbor.encodingPayloadToCbor(responsePayload));
        return response;
    }
}
