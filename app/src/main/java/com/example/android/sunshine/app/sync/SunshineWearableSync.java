package com.example.android.sunshine.app.sync;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by oofong25 on 10/15/15.
 * .
 */
class SunshineWearableSync implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private final String TAG = SunshineWearableSync.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private PutDataMapRequest mPutDataMapRequest;

    public SunshineWearableSync(Context context, PutDataMapRequest putDataMapRequest) {
        Log.d(TAG, "oncreate SunshineWearableSync");

        mPutDataMapRequest = putDataMapRequest;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        LOGD(TAG, "onConnected: " + connectionHint);


        Wearable.DataApi.putDataItem(mGoogleApiClient, mPutDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        Log.d(TAG, "putDataItem success-> " + dataItemResult.getStatus());
                        Log.d(TAG, "putDataItem path-> " + dataItemResult.getDataItem().getUri());
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int cause) {
        LOGD(TAG, "onConnectionSuspended: " + cause);

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        LOGD(TAG, "onConnectionFailed: " + result);

    }

    private void LOGD(String tag, String msg) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, msg);
        }
    }
}
