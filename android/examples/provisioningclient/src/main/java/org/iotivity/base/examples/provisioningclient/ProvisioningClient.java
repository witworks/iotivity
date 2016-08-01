package org.iotivity.base.examples.provisioningclient;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.iotivity.base.CredType;
import org.iotivity.base.DeviceStatus;
import org.iotivity.base.KeySize;
import org.iotivity.base.ModeType;
import org.iotivity.base.OcException;
import org.iotivity.base.OcPlatform;
import org.iotivity.base.OcProvisioning;
import org.iotivity.base.OcSecureResource;
import org.iotivity.base.OicSecAcl;
import org.iotivity.base.OicSecAce;
import org.iotivity.base.OicSecResr;
import org.iotivity.base.OicSecValidity;
import org.iotivity.base.OicSecPdAcl;
import org.iotivity.base.OcPrmType;
import org.iotivity.base.OxmType;
import org.iotivity.base.PlatformConfig;
import org.iotivity.base.ProvisionResult;
import org.iotivity.base.QualityOfService;
import org.iotivity.base.ServiceType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ProvisioningClient extends Activity implements
OcSecureResource.DoOwnershipTransferListener, OcSecureResource.ProvisionPairwiseDevicesListener {

    private static final String TAG = "Provisioning Client: ";
    private static final int BUFFER_SIZE = 1024;
    int unownedDevCount = StringConstants.NUMBER_ZERO;
    OcProvisioning.PinCallbackListener pinCallbackListener =
        new OcProvisioning.PinCallbackListener() {
            @Override
                public String pinCallbackListener() {
                    Log.d(TAG, "Inside Pin Callback ");
                    return "";
                }
        };
    private String filePath = "";
    private OcSecureResource newSecureResource;
    private List<OcSecureResource> deviceList;
    private List<OcSecureResource> ownedDeviceList;
    private TextView mEventsTextView;

    OcSecureResource.ProvisionDirectPairingListener provisionDPListener =
        new OcSecureResource.ProvisionDirectPairingListener() {
            @Override
                public void provisionDirectPairingListener(List<ProvisionResult> provisionResults,
                        int hasError) {
                    Log.d(TAG, "Inside provisionDPListener");
                    ProvisionResult pResult = provisionResults.get(0);
                    if (hasError == StringConstants.ERROR_CODE) {
                        logMessage(TAG + "Provision direct pairing Failed for " + pResult.getDevId());
                    } else {
                        logMessage(TAG + "Provision direct pairing Successful for " + pResult.getDevId());
                        if (ownedDeviceList.size() == 1) {
                            new ProvisionACLAsyncTask().execute();
                        }
                        if (ownedDeviceList.size() > 1) {
                            new GetLinkedDevicesAsyncTask().execute();
                        }
                    }
                }
        };

    OcSecureResource.ProvisionAclListener provisionAclListener =
        new OcSecureResource.ProvisionAclListener() {
            @Override
                public void provisionAclListener(List<ProvisionResult> provisionResults,
                        int hasError) {
                    Log.d(TAG, "Inside ProvisionAclListener ");
                    if (hasError == StringConstants.ERROR_CODE) {
                        logMessage("Error: ACL Provision failed !!");
                    } else {
                        logMessage("ACL Provision Done !!");
                        new DeviceRevocationAsyncTask().execute();
                    }
                }
        };
    OcSecureResource.ProvisionCredentialsListener provisionCredentialsListener =
        new OcSecureResource.ProvisionCredentialsListener() {
            @Override
                public void provisionCredentialsListener(List<ProvisionResult> provisionResults,
                        int hasError) {
                    Log.d(TAG, "Inside ProvisionCredentialsListener ");
                    if (hasError == StringConstants.ERROR_CODE) {
                        logMessage("Error: Provision Credentials failed !!");
                    } else {
                        logMessage("Provision Credentials Done !!");
                        new ProvisionACLAsyncTask().execute();
                    }
                }
        };
    OcSecureResource.UnlinkDevicesListener unlinkDevicesListener =
        new OcSecureResource.UnlinkDevicesListener() {
            @Override
                public void unlinkDevicesListener(List<ProvisionResult> provisionResults,
                        int hasError) {
                    Log.d(TAG, "Inside unlinkDevicesListener ");
                    if (hasError == StringConstants.ERROR_CODE) {
                        logMessage("Error: UnLinking device !!");
                    } else {
                        logMessage("Unlink Done !!");
                        new ProvisionCredentialAsyncTask().execute();
                    }
                }
        };
    OcSecureResource.RemoveDeviceListener removeDeviceListener =
        new OcSecureResource.RemoveDeviceListener() {
            @Override
                public void removeDeviceListener(List<ProvisionResult> provisionResults,
                        int hasError) {
                    if (hasError == StringConstants.ERROR_CODE) {
                        logMessage("Error: Remove Fail !!");
                    } else {
                        logMessage("Remove Device done !!");
                    }
                }
        };

    @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_secure_provision_client);
            mEventsTextView = new TextView(this);
            mEventsTextView.setGravity(Gravity.BOTTOM);
            mEventsTextView.setMovementMethod(new ScrollingMovementMethod());
            LinearLayout layout = (LinearLayout) findViewById(R.id.linearLayout);
            layout.addView(mEventsTextView, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                    );
            filePath = getFilesDir().getPath() + "/"; //  data/data/<package>/files/
            //copy CBOR file when application runs first time
            SharedPreferences wmbPreference = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isFirstRun = wmbPreference.getBoolean("FIRSTRUN", true);
            if (isFirstRun) {
                copyCborFromAsset();
                SharedPreferences.Editor editor = wmbPreference.edit();
                editor.putBoolean("FIRSTRUN", false);
                editor.commit();
            }
            initOICStack();
        }

    /**
     * configure OIC platform and call findResource
     */
    private void initOICStack() {
        //create platform config
        PlatformConfig cfg = new PlatformConfig(
                this,
                ServiceType.IN_PROC,
                ModeType.CLIENT_SERVER,
                "0.0.0.0", // bind to all available interfaces
                0,
                QualityOfService.LOW, filePath + StringConstants.OIC_CLIENT_CBOR_DB_FILE);
        OcPlatform.Configure(cfg);
        try {
            /*
             * Initialize DataBase
             */
            String sqlDbPath = getFilesDir().getAbsolutePath().replace("files", "databases") +
                File.separator;
            File file = new File(sqlDbPath);
            //check files directory exists
            if (!(file.isDirectory())) {
                file.mkdirs();
                Log.d(TAG, "Sql db directory created at " + sqlDbPath);
            }
            Log.d(TAG, "Sql db directory exists at " + sqlDbPath);
            OcProvisioning.provisionInit(sqlDbPath + StringConstants.OIC_SQL_DB_FILE);
        } catch (OcException e) {
            logMessage(TAG + "provisionInit error: " + e.getMessage());
            Log.e(TAG, e.getMessage());
        }
        new DiscoveryOTTransferAsyncTask().execute();
    }

    @Override
        synchronized public void doOwnershipTransferListener(List<ProvisionResult> ProvisionResultList,
                int hasError) {
            ProvisionResult pResult = ProvisionResultList.get(0);
            if (hasError == StringConstants.ERROR_CODE) {
                logMessage(TAG + "Ownership Transfer Failed for " + pResult.getDevId());
            } else {
                logMessage(TAG + "Ownership Transfer Successful for "
                        + pResult.getDevId());
                unownedDevCount--;
            }
            if (unownedDevCount == 0) { //When done with Ownership Transfer
                new OwnedDiscoveryAsyncTask().execute();
            }
        }


    private void doDPProvisioning() {
        try {
            logMessage(TAG + "Provision direct pairing for " + ownedDeviceList.get(0).getDeviceID());
            newSecureResource = ownedDeviceList.get(0);
            String pin = "00000000";
            List<OcPrmType> prmTypes = new ArrayList<OcPrmType>();
            prmTypes.add(OcPrmType.DP_PRE_CONFIGURED);
            boolean edp = true;
            List<String> resources = new ArrayList<String>();
            List<String> periods = new ArrayList<String>();
            List<String> recurrences = new ArrayList<String>();
            resources.add(StringConstants.DEFAULT_RESOURCES);
            OicSecPdAcl pdAcl = new OicSecPdAcl(recurrences, periods,
                    StringConstants.DEFAULT_PERMISSION, resources);
            OicSecPdAcl[] oicSecPdAcls = new OicSecPdAcl[1];
            oicSecPdAcls[0] = pdAcl;
            newSecureResource.doProvisionDirectPairing(pin, oicSecPdAcls, prmTypes, edp,
                    provisionDPListener);
        } catch (Exception e) {
            logMessage(TAG + "Direct Pairing Provisioning error: " + e.getMessage());
            Log.e(TAG, e.getMessage());
        }
    }


    private void doPairwiseProvisioning() {
        try {
            logMessage(TAG + "Pairwise Provisioning b/w " + ownedDeviceList.get(0).getDeviceID()
                    + " and " + ownedDeviceList.get(1).getDeviceID());
            newSecureResource = ownedDeviceList.get(0);

            List<OicSecResr> resources1 = new ArrayList<OicSecResr>();
            List<OicSecResr> resources2 = new ArrayList<OicSecResr>();
            List<OicSecValidity> validities = new ArrayList<OicSecValidity>();

            List<String> types1 = new ArrayList<String>();
            types1.add(StringConstants.RESOURCE_TYPE_1A);
            types1.add(StringConstants.RESOURCE_TYPE_1B);
            List<String> types2 = new ArrayList<String>();
            types2.add(StringConstants.RESOURCE_TYPE_2A);
            types2.add(StringConstants.RESOURCE_TYPE_2B);
            List<String> interfaces = new ArrayList<String>();
            interfaces.add(StringConstants.RESOURCE_INTERFACE_1);
            interfaces.add(StringConstants.RESOURCE_INTERFACE_2);

            OicSecResr oicSecResr1a = new OicSecResr(StringConstants.HREF_RESOURCES_1A, "", types1, 2, interfaces, 2);
            OicSecResr oicSecResr1b = new OicSecResr(StringConstants.HREF_RESOURCES_1B, "", types1, 2, interfaces, 2);
            resources1.add(oicSecResr1a);
            resources1.add(oicSecResr1b);

            OicSecResr oicSecResr2a = new OicSecResr(StringConstants.HREF_RESOURCES_2A, "", types2, 2, interfaces, 2);
            OicSecResr oicSecResr2b = new OicSecResr(StringConstants.HREF_RESOURCES_2B, "", types2, 2, interfaces, 2);
            resources2.add(oicSecResr2a);
            resources2.add(oicSecResr2b);

            List<String> recurrences = new ArrayList<String>();
            recurrences.add(StringConstants.DEFAULT_RECURRENCES_1);
            recurrences.add(StringConstants.DEFAULT_RECURRENCES_2);
            OicSecValidity oicSecValidity = new OicSecValidity(StringConstants.DEFAULT_PERIOD,
                    recurrences, 2);
            validities.add(oicSecValidity);

            OicSecAce oicsecace1 = new OicSecAce(newSecureResource.getDeviceID(),
                    StringConstants.DEFAULT_PERMISSION, resources1, validities);
            OcSecureResource newSecureResource2 = ownedDeviceList.get(1);
            OicSecAce oicsecace2 = new OicSecAce(newSecureResource2.getDeviceID(),
                    StringConstants.DEFAULT_PERMISSION, resources2, validities);

            List<OicSecAce> oicSecAces1=new ArrayList<OicSecAce>();
            oicSecAces1.add(oicsecace1);
            List<OicSecAce> oicSecAces2=new ArrayList<OicSecAce>();
            oicSecAces2.add(oicsecace2);
            OicSecAcl acl1 = new OicSecAcl(StringConstants.DEFAULT_ROWNER_ID,oicSecAces1);
            OicSecAcl acl2 = new OicSecAcl(StringConstants.DEFAULT_ROWNER_ID,oicSecAces2);

            newSecureResource.provisionPairwiseDevices(EnumSet.of(CredType.SYMMETRIC_PAIR_WISE_KEY),
                    KeySize.OWNER_PSK_LENGTH_128, acl1, newSecureResource2, acl2, this);
        } catch (Exception e) {
            logMessage(TAG + "Pairwise Provisioning  error: " + e.getMessage());
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
        public void provisionPairwiseDevicesListener(List<ProvisionResult> ProvisionResultList,
                int hasError) {
            if (hasError == StringConstants.ERROR_CODE) {
                logMessage(TAG + "provisionPairwiseDevices Failed");
            } else {
                for (int i = 0; i < ProvisionResultList.size(); i++) {
                    ProvisionResult pResult = ProvisionResultList.get(i);
                    logMessage(TAG + "provisionPairwiseDevices Result for "
                            + pResult.getDevId() + "is " + pResult.getResult());
                }
                doDPProvisioning();
            }
        }

    /**
     * Copy svr db CBOR dat file from assets folder to app data files dir
     */
    private void copyCborFromAsset() {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        int length;
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            inputStream = getAssets().open(StringConstants.OIC_CLIENT_CBOR_DB_FILE);
            File file = new File(filePath);
            //check files directory exists
            if (!(file.exists() && file.isDirectory())) {
                file.mkdirs();
            }
            outputStream = new FileOutputStream(filePath + StringConstants.OIC_CLIENT_CBOR_DB_FILE);
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        } catch (NullPointerException e) {
            logMessage(TAG + "Null pointer exception " + e.getMessage());
            Log.e(TAG, e.getMessage());
        } catch (FileNotFoundException e) {
            logMessage(TAG + "CBOR svr db file not found " + e.getMessage());
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            logMessage(TAG + StringConstants.OIC_CLIENT_CBOR_DB_FILE + " file copy failed");
            Log.e(TAG, e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    public void logMessage(String text) {
        logMsg(text);
    }

    public void logMsg(final String text) {
        runOnUiThread(new Runnable() {
                public void run() {
                Message msg = new Message();
                msg.obj = text;
                mEventsTextView.append(text);
                mEventsTextView.append("\n\n");
                }
                });
        Log.i(TAG, text);
        Intent intent = new Intent(getPackageName());
        intent.putExtra(StringConstants.MESSAGE, text);
        sendBroadcast(intent);
    }

    private class DiscoveryOTTransferAsyncTask extends AsyncTask<Void, String, String> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected String doInBackground(Void... params) {
                try {
                    /**
                     * Discover Un-owned devices
                     */
                    publishProgress(TAG + "Discovering Unowned Devices");
                    deviceList = new ArrayList<OcSecureResource>(OcProvisioning.discoverUnownedDevices
                            (StringConstants.DISCOVERY_TIMEOUT_10));
                    if (deviceList.size() > 0) {
                        unownedDevCount = deviceList.size();
                        for (int i = 0; i < deviceList.size(); i++) {
                            publishProgress(TAG + "Un-owned Discovered Device " + (i + 1) + "= " +
                                    deviceList.get(i).getDeviceID());
                        }
                        try {
                            OcProvisioning.SetownershipTransferCBdata(OxmType.OIC_JUST_WORKS,
                                    pinCallbackListener);
                            for (int i = 0; i < deviceList.size(); i++) {
                                publishProgress(TAG + "Doing Ownership Transfer for " +
                                        deviceList.get(i).getDeviceID());
                                deviceList.get(i).doOwnershipTransfer(ProvisioningClient.this);
                            }
                        } catch (OcException e) {
                            publishProgress(TAG + "Ownership Transfer error: " + e.getMessage());
                            return "Ownership Transfer error: " + e.getMessage();

                        }
                    } else {
                        publishProgress(TAG + "No un-owned devices present");
                        new OwnedDiscoveryAsyncTask().execute();
                    }
                } catch (OcException e) {
                    publishProgress(TAG + "Un-owned discovery error: " + e.getMessage());
                    return "Un-owned discovery error:  " + e.getMessage();
                }
                return "success";
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }

        @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
            }
    }

    private class ProvisionACLAsyncTask extends AsyncTask<Void, String, Void> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ownedDeviceList.size() > 0) {
                        OcSecureResource ocSecureResource = ownedDeviceList.get(0);

                        publishProgress(TAG + "ACL Provision for " + ocSecureResource.getDeviceID());
                        List<OicSecResr> resources1 = new ArrayList<OicSecResr>();
                        List<OicSecResr> resources2 = new ArrayList<OicSecResr>();
                        List<OicSecValidity> validities = new ArrayList<OicSecValidity>();

                        List<String> types1 = new ArrayList<String>();
                        types1.add(StringConstants.RESOURCE_TYPE_1A);
                        types1.add(StringConstants.RESOURCE_TYPE_1B);
                        List<String> types2 = new ArrayList<String>();
                        types2.add(StringConstants.RESOURCE_TYPE_2A);
                        types2.add(StringConstants.RESOURCE_TYPE_2B);
                        List<String> interfaces = new ArrayList<String>();
                        interfaces.add(StringConstants.RESOURCE_INTERFACE_1);
                        interfaces.add(StringConstants.RESOURCE_INTERFACE_2);

                        OicSecResr oicSecResr1a = new OicSecResr(StringConstants.HREF_RESOURCES_1A, "", types1, 2, interfaces, 2);
                        OicSecResr oicSecResr1b = new OicSecResr(StringConstants.HREF_RESOURCES_1B, "", types1, 2, interfaces, 2);
                        resources1.add(oicSecResr1a);
                        resources1.add(oicSecResr1b);

                        OicSecResr oicSecResr2a = new OicSecResr(StringConstants.HREF_RESOURCES_2A, "", types2, 2, interfaces, 2);
                        OicSecResr oicSecResr2b = new OicSecResr(StringConstants.HREF_RESOURCES_2B, "", types2, 2, interfaces, 2);
                        resources2.add(oicSecResr2a);
                        resources2.add(oicSecResr2b);

                        List<String> recurrences = new ArrayList<String>();
                        recurrences.add(StringConstants.DEFAULT_RECURRENCES_1);
                        recurrences.add(StringConstants.DEFAULT_RECURRENCES_2);
                        OicSecValidity oicSecValidity = new OicSecValidity(StringConstants.DEFAULT_PERIOD,
                                recurrences, 2);
                        validities.add(oicSecValidity);

                        OicSecAce oicsecace1 = new OicSecAce(StringConstants.DEFAULT_SUBJECT_ID1,
                                StringConstants.DEFAULT_PERMISSION, resources1, validities);

                        OicSecAce oicsecace2 = new OicSecAce(StringConstants.DEFAULT_SUBJECT_ID2,
                                StringConstants.DEFAULT_PERMISSION, resources2, validities);

                        List<OicSecAce> oicSecAces=new ArrayList<OicSecAce>();
                        oicSecAces.add(oicsecace1);
                        oicSecAces.add(oicsecace2);

                        OicSecAcl aclObject = new OicSecAcl(StringConstants.DEFAULT_ROWNER_ID, oicSecAces);

                        ocSecureResource.provisionACL(aclObject, provisionAclListener);
                    } else {
                        publishProgress(TAG + "No Owned devices present");
                    }
                } catch (Exception e) {
                    publishProgress(TAG + "ProvisionACL error: " + e.getMessage());
                }
                return null;
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }
    }

    private class ProvisionCredentialAsyncTask extends AsyncTask<Void, String, Void> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ownedDeviceList.size() > 1) {
                        OcSecureResource ocSecureResource = ownedDeviceList.get(0);
                        OcSecureResource ocSecureResourceDest = ownedDeviceList.get(1);
                        publishProgress(TAG + "ProvisionCredential for " +
                                ocSecureResource.getDeviceID() + " with " +
                                ocSecureResourceDest.getDeviceID());
                        ocSecureResource.provisionCredentials(EnumSet.of(CredType.SYMMETRIC_PAIR_WISE_KEY),
                                KeySize.OWNER_PSK_LENGTH_128,
                                ocSecureResourceDest, provisionCredentialsListener);
                    } else {
                        publishProgress(TAG + "Cannot perform credentials between devices");
                    }
                } catch (Exception e) {
                    publishProgress(TAG + "Provision credentials error: " + e.getMessage());
                }
                return null;
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }
    }

    private class GetLinkedDevicesAsyncTask extends AsyncTask<Void, String, String> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected String doInBackground(Void... params) {
                try {
                    if (ownedDeviceList.size() > 1) {
                        OcSecureResource ocSecureResource = ownedDeviceList.get(0);
                        publishProgress(TAG + "Get linked devices of " + ocSecureResource.getDeviceID());
                        List<String> linkedDevices = ocSecureResource.getLinkedDevices();
                        if (linkedDevices.size() > 0) {
                            for (int i = 0; i < linkedDevices.size(); i++) {
                                publishProgress(TAG + "Linked Devices " +
                                        (i + 1) + "= " + linkedDevices.get(i));
                            }
                        } else {
                            publishProgress(TAG + "No linked Devices found");
                        }
                    } else {
                        publishProgress(TAG + "Cannot perform linked devices");
                    }
                } catch (Exception e) {
                    publishProgress(TAG + "getLinked device error: " + e.getMessage());
                    return "failed";
                }
                return "success";
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }

        @Override
            protected void onPostExecute(String s) {
                if ("success".equals(s)) {
                    new ProvisionUnlinkAsyncTask().execute();
                }
            }
    }

    private class ProvisionUnlinkAsyncTask extends AsyncTask<Void, String, Void> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ownedDeviceList.size() > 1) {
                        OcSecureResource ocSecureResource = ownedDeviceList.get(0);
                        OcSecureResource ocSecureResourceDest = ownedDeviceList.get(1);
                        publishProgress(TAG + "Un linking  " + ocSecureResource.getDeviceID() +
                                " with " + ocSecureResourceDest.getDeviceID());
                        ocSecureResource.unlinkDevices(ocSecureResourceDest, unlinkDevicesListener);
                    } else {
                        publishProgress(TAG + "Cannot perform unlink devices");
                    }
                } catch (Exception e) {
                    publishProgress(TAG + "Unlink error: " + e.getMessage());
                }
                return null;
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }
    }

    private class DeviceRevocationAsyncTask extends AsyncTask<Void, String, Void> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ownedDeviceList.size() > 0) {
                        OcSecureResource ocSecureResource = ownedDeviceList.get(0);
                        publishProgress(TAG + "Removing " + ocSecureResource.getDeviceID());
                        ocSecureResource.removeDevice(StringConstants.DISCOVERY_TIMEOUT_20,
                                removeDeviceListener);
                    } else {
                        publishProgress(TAG + "Cannot remove");
                    }
                } catch (Exception e) {
                    publishProgress(TAG + "Remove Device error: " + e.getMessage());
                }
                return null;
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }
    }

    private class OwnedDiscoveryAsyncTask extends AsyncTask<Void, String, String> {

        @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

        @Override
            protected String doInBackground(Void... params) {
                try {
                    publishProgress(TAG + "Initiate Owned device Discovery");
                    ownedDeviceList = OcProvisioning.discoverOwnedDevices
                        (StringConstants.DISCOVERY_TIMEOUT_10);
                    if (ownedDeviceList.size() > 0) {
                        for (int i = 0; i < ownedDeviceList.size(); i++) {
                            publishProgress(TAG + "Owned Discovered Device " + (i + 1) + "= " +
                                    ownedDeviceList.get(i).getDeviceID()
                                    + "\nIP Address= " + ownedDeviceList.get(i).getIpAddr()
                                    + "\nOwned Status= " + ownedDeviceList.get(i).getOwnedStatus()
                                    + "\nDevice Status= " + ((ownedDeviceList.get(i).
                                            getDeviceStatus() == DeviceStatus.ON) ? "ON" : "OFF")
                                    );
                        }
                    } else {
                        publishProgress(TAG + "No Owned devices present");
                    }
                } catch (OcException e) {
                    publishProgress(TAG + "Owned device Discovery error: " + e.getMessage());
                    return "Owned device Discovery error: " + e.getMessage();
                }
                return "success";
            }

        @Override
            protected void onProgressUpdate(String... values) {
                logMessage(values[0]);
            }

        @Override
            protected void onPostExecute(String s) {


                if (ownedDeviceList.size() == 1 && "success".equals(s)) {
                    doDPProvisioning();
                }
                if (ownedDeviceList.size() > 1 && "success".equals(s)) {
                    doPairwiseProvisioning();
                }

            }
    }

    /**
     * to display on Server Message on Client screen
     */
    public class MessageReceiver extends BroadcastReceiver {
        @Override
            public void onReceive(Context context, Intent intent) {
                final String message = intent.getStringExtra(StringConstants.MESSAGE);
                logMessage(message);
            }
    }
}
