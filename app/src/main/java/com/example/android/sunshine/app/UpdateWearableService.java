package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by oofong25 on 10/12/15.
 * This class is used to communicate with the wearable device.
 */
public class UpdateWearableService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    GoogleApiClient mGoogleApiClient;
    private static final String TAG = UpdateWearableService.class.getSimpleName();
    private static final String SEND_TEMPERATURE_PATH = "/send_temperature";
    private static final String IMAGE_KEY = "image_key";
    private static final String TEMPERATURE_HIGH = "temperature_high";
    private static final String TEMPERATURE_LOW = "temperature_low";


    private static final String COUNT_PATH = "/count";
    private static final String COUNT_KEY = "count";


    private static final int COL_WEATHER_CONDITION_ID = 6;
    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    public UpdateWearableService(){}

//    public UpdateWearableService() {
//        super(UpdateWearableService.class.getSimpleName());
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy ");
      //  mGoogleApiClient.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate ");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApiIfAvailable(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        handleJob();
        return super.onStartCommand(intent, flags, startId);
    }

    private void handleJob(){

        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        String locationSetting = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());

        Cursor mCursor = null;

        try {
            mCursor = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null, null, sortOrder);
            if (mCursor != null && mCursor.moveToFirst()) {
                int weatherId = mCursor.getInt(COL_WEATHER_CONDITION_ID);


                int icon = Utility.getIconResourceForWeatherCondition(weatherId);

                // Read high temperature from cursor
                double high = mCursor.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
                String highString = Utility.formatTemperature(this, high);

                // Read low temperature from cursor
                double low = mCursor.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);
                String lowString = Utility.formatTemperature(this, low);

                if (Utility.usingLocalGraphics(this)) {
                    //use default image
                    int defaultImage = Utility.getArtResourceForWeatherCondition(weatherId);
                    icon = defaultImage;
                }

                Log.d(TAG, "Success weather id int -> " + weatherId);
                Log.d(TAG, "Success weather icon int -> " + icon);
                Log.d(TAG, "Success weather high temp -> " + highString);
                Log.d(TAG, "Success weather low temp -> " + lowString);

                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), icon);
                if (bitmap != null) {
                    sendTemperatureToWatch(toAsset(bitmap), highString, lowString);
                    sendCountPath();
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mCursor != null)
                mCursor.close();
        }

    }


    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected: " + connectionHint);
        // Now you can use the Data Layer API

    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended: " + cause);

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: " + connectionResult);

    }


    private void sendTemperatureToWatch(Asset asset, String tempHigh, String tempLow) {
        boolean isAssetOK = asset != null;
        Log.d(TAG, "Sending start sendTemperatureToWatch");
        Log.d(TAG, "Success Asset -> " + isAssetOK);
        Log.d(TAG, "Success mGoogleApiClient connected -> " + mGoogleApiClient.isConnected());

        PutDataMapRequest dataMap = PutDataMapRequest.create(SEND_TEMPERATURE_PATH);
        dataMap.getDataMap().putAsset(IMAGE_KEY, asset);
        dataMap.getDataMap().putString(TEMPERATURE_HIGH, tempHigh);
        dataMap.getDataMap().putString(TEMPERATURE_LOW, tempLow);

        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        String str = "status " + dataItemResult.getStatus().isSuccess();
                        doSomething(str);
                        Log.d(TAG, "TempPath Sending image was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });

        Log.d(TAG, "Sending end sendTemperatureToWatch");
    }


    public void sendCountPath() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(COUNT_PATH);
        putDataMapRequest.getDataMap().putInt(COUNT_KEY, 777);
        PutDataRequest request = putDataMapRequest.asPutDataRequest();

        if (!mGoogleApiClient.isConnected()) {
            return;
        }
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        String str = "status " + dataItemResult.getStatus().isSuccess();
                        doSomething(str);
                        Log.d(TAG, "CountPath Sending image was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });
    }

    void doSomething(String msg) {
        Toast.makeText(this, "doing something " + msg, Toast.LENGTH_LONG).show();
        Log.d(TAG, "doing something " + msg);
    }

    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }


}
