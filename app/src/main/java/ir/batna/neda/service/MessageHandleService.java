package ir.batna.neda.service;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import ir.batna.neda.database.ClientApp;
import ir.batna.neda.database.ClientAppDatabase;
import ir.batna.neda.utils.NedaUtils;

import static ir.batna.neda.application.Neda.nedaContext;
import static ir.batna.neda.utils.NedaUtils.log;

public class MessageHandleService extends IntentService {

    public MessageHandleService() {
        super(NedaUtils.MESSAGE_HANDLE_SERVICE);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        log("Processing input message from server in MessageHandleService");
        Bundle bundle = intent.getExtras();
        String data = bundle.getString(NedaUtils.DATA);
        String type = bundle.getString(NedaUtils.TYPE);
        if (type.equalsIgnoreCase(NedaUtils.MESSAGE_HANDLE)) {

            handleReceivedMessages(nedaContext, data);
        }
    }

    private static void handleReceivedMessages(Context context, String data) {

        try {

            JSONObject jsonObject = new JSONObject(data);
            String type = jsonObject.getString(NedaUtils.TYPE);
            log("Received messaged type is: " + type);
            switch (type) {

                case NedaUtils.REGISTER:
                    if (jsonObject.getString(NedaUtils.RESULT).equalsIgnoreCase(NedaUtils.SUCCESS)) {

                        log("Device was successfully registered on push server");
                    } else {
                        log("Device was not registered on push server, result: " + jsonObject.get(NedaUtils.RESULT));
                    }
                    break;

                case NedaUtils.REGISTER_APP:
                    if (jsonObject.getString(NedaUtils.RESULT).equalsIgnoreCase(NedaUtils.SUCCESS)) {

                        String packageName = jsonObject.getString(NedaUtils.APP);
                        String token = jsonObject.getString(NedaUtils.TOKEN);
                        ClientAppDatabase database = ClientAppDatabase.getDatabase(context);
                        ClientApp clientApp = database.clientAppDao().findByToken(token);
                        if (clientApp != null) {

                            clientApp.status = NedaUtils.REGISTERED;
                            log("Updating " + packageName + " status to registered");
                            // For now, since push server does not keep track of registered clients, we need to re-register all clients
                            // every time we connect to server
                            if (1 == 1) {
                                break;
                            }
                            database.clientAppDao().updateByToken(token, NedaUtils.REGISTERED, NedaUtils.getCurrentDateInFormat());
                            log("Database updated");
                        } else {
                            log("Something unusual happened: Received successful registration for an app that doesn't exist" +
                                    " on the database: " + packageName);
                        }
                    } else {
                        log("Failed to register package: " + jsonObject.getString(NedaUtils.PACKAGE_NAME) + " on push server");
                    }
                    break;

                case NedaUtils.PUSH:
                    log("Push message received");
                    String packageName = jsonObject.getString(NedaUtils.APP);
                    String token = null;
                    try {
                        token = jsonObject.getString(NedaUtils.TOKEN);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (token == null) {

                        log("Push message does not have any token");
                        ClientAppDatabase database = ClientAppDatabase.getDatabase(context);
                        ClientApp clientApp = database.clientAppDao().findByPackageName(packageName);
                        if (clientApp != null) {

                            if (NedaUtils.isAppInstalled(context, packageName)) {

                                log("The package that was intended to receive the push message, exists on the device");
                                String pushData = jsonObject.getString(NedaUtils.DATA);
                                Intent intent = new Intent();
                                intent.putExtra(NedaUtils.TYPE, NedaUtils.PUSH);
                                intent.putExtra(NedaUtils.DATA, pushData);
                                intent.setAction(NedaUtils.NEDA_RECEIVER_INTENT_ACTION);
                                intent.setComponent(new ComponentName(packageName, packageName + NedaUtils.NEDA_CLIENT_RECEIVER));
                                context.sendBroadcast(intent);
                                log("Push message sent to " + packageName);
                            } else {
                                log("The package that was intended to receive the push message does not exist on this device anymore!");
                            }
                        } else {
                            log("Received Push message for an app that is not registered in Neda!!!");
                        }
                    } else {
                        log("Push message has a token");
                        ClientAppDatabase database = ClientAppDatabase.getDatabase(context);
                        ClientApp clientApp = database.clientAppDao().findByToken(token);
                        String signature = clientApp.signature;
                        long installDate = new Long(clientApp.dateInstalled).longValue();
                        if (NedaUtils.isAppInstalled(context, packageName, signature, installDate)) {

                            log("The package that was intended to receive the push message, exists on the device");
                            String pushData = jsonObject.getString(NedaUtils.DATA);
                            Intent intent = new Intent();
                            intent.putExtra(NedaUtils.TYPE, NedaUtils.PUSH);
                            intent.putExtra(NedaUtils.DATA, pushData);
                            intent.setAction(NedaUtils.NEDA_RECEIVER_INTENT_ACTION);
                            intent.setComponent(new ComponentName(packageName,NedaUtils.NEDA_CLIENT_RECEIVER));
                            context.sendBroadcast(intent);
                            log("Push message sent to " + packageName);
                        } else {
                            log("The package that was intended to receive the push message does not exist on this device anymore!");
                        }
                    }
                    break;
            }
        } catch (JSONException e) {
            log(e.getMessage());
            e.printStackTrace();
        }
    }
}
