package com.score.senz.handlers;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import com.score.senz.R;
import com.score.senz.db.SenzorsDbSource;
import com.score.senz.exceptions.NoUserException;
import com.score.senz.listeners.ShareSenzListener;
import com.score.senz.services.LocationService;
import com.score.senz.utils.NotificationUtils;
import com.score.senz.utils.PreferenceUtils;
import com.score.senzc.enums.SenzTypeEnum;
import com.score.senzc.pojos.Senz;
import com.score.senzc.pojos.User;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.HashMap;

/**
 * Handle All senz messages from here
 */
public class SenzHandler {
    private static final String TAG = SenzHandler.class.getName();

    private static Context context;
    private static ShareSenzListener listener;

    private static SenzHandler instance;

    private SenzHandler() {
    }

    public static SenzHandler getInstance(Context context, ShareSenzListener listener) {
        if (instance == null) {
            instance = new SenzHandler();
            SenzHandler.context = context;
            SenzHandler.listener = listener;
        }
        return instance;
    }

    public void handleSenz(Senz senz) {
        switch (senz.getSenzType()) {
            case PING:
                Log.d(TAG, "PING received");
                break;
            case SHARE:
                Log.d(TAG, "SHARE received");
                handleShareSenz(senz);
                break;
            case GET:
                Log.d(TAG, "GET received");
                handleGetSenz(senz);
                break;
            case DATA:
                Log.d(TAG, "DATA received");
                handleDataSenz(senz);
                break;
        }
    }

    private static void verifySenz(Senz senz) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        senz.getSender();

        // TODO get public key of sender
        // TODO verify signature of the senz
        //RSAUtils.verifyDigitalSignature(senz.getPayload(), senz.getSignature(), null);
    }

    private void handleShareSenz(Senz senz) {
        if (senz.getAttributes().containsKey("#lat") || senz.getAttributes().containsKey("lon")) {
            // location senz
            // create senz
            SenzorsDbSource dbSource = new SenzorsDbSource(context);
            User sender = dbSource.getOrCreateUser(senz.getSender().getUsername());
            senz.setSender(sender);

            // if senz already exists in the db, SQLiteConstraintException should throw
            try {
                dbSource.createSenz(senz);
                sendShareResponse(sender, true);

                NotificationUtils.showNotification(context, context.getString(R.string.new_senz), "SenZ received from @" + senz.getSender().getUsername());
            } catch (SQLiteConstraintException e) {
                sendShareResponse(sender, false);
                Log.e(TAG, e.toString());
            }
        } else if (senz.getAttributes().containsKey("#gpio")) {
            // pi senz
            // we broadcast it
            Intent newSenzIntent = new Intent("com.score.senz.NEW_SENZ");
            newSenzIntent.putExtra("SENZ", senz);
            context.sendBroadcast(newSenzIntent);
        }
    }

    private void handleGetSenz(Senz senz) {
        Log.d("Tag", senz.getSender() + " : " + senz.getSenzType().toString());

        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.putExtra("USER", senz.getSender());

        context.startService(serviceIntent);
    }

    private void handleDataSenz(Senz senz) {
        // sync data with db data
        SenzorsDbSource dbSource = new SenzorsDbSource(context);
        User sender = dbSource.getOrCreateUser(senz.getSender().getUsername());
        senz.setSender(sender);

        // we broadcast data senz
        Intent intent = new Intent("DATA");
        intent.putExtra("SENZ", senz);
        context.sendBroadcast(intent);

        // broadcast received senz
        Intent newSenzIntent = new Intent("com.score.senz.NEW_SENZ");
        newSenzIntent.putExtra("SENZ", senz);
        context.sendBroadcast(newSenzIntent);
    }

    private void sendShareResponse(User receiver, boolean isDone) {
        try {
            // create senz attributes
            HashMap<String, String> senzAttributes = new HashMap<>();
            senzAttributes.put("time", ((Long) (System.currentTimeMillis() / 1000)).toString());
            if (isDone) senzAttributes.put("msg", "ShareDone");
            else senzAttributes.put("msg", "ShareFail");

            String id = "_ID";
            String signature = "";
            SenzTypeEnum senzType = SenzTypeEnum.DATA;
            User sender = PreferenceUtils.getUser(context);
            Senz senz = new Senz(id, signature, senzType, sender, receiver, senzAttributes);

            listener.onShareSenz(senz);
        } catch (NoUserException e) {
            e.printStackTrace();
        }
    }

}
