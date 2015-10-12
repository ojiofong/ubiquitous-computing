package com.example.android.sunshine.app;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

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
public class UpdateWearableService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    GoogleApiClient mGoogleApiClient;
    private static final String TAG = UpdateWearableService.class.getSimpleName();
    private static final String SEND_TEMPERATURE_PATH = "/send-temperature";
    private static final String IMAGE_KEY = "image_key";
    private static final String TEMPERATURE_HIGH = "temperature_high";
    private static final String TEMPERATURE_LOW = "temperature_low";


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

    public UpdateWearableService() {
        super(UpdateWearableService.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

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
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mCursor != null)
                mCursor.close();
        }


    }


    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }


    private void sendTemperatureToWatch(Asset asset, String tempHigh, String tempLow) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(SEND_TEMPERATURE_PATH);
        dataMap.getDataMap().putAsset(IMAGE_KEY, asset);
        dataMap.getDataMap().putString(TEMPERATURE_HIGH, tempHigh);
        dataMap.getDataMap().putString(TEMPERATURE_LOW, tempLow);

        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                Log.d(TAG, "Sending image was successful: " + dataItemResult.getStatus()
                        .isSuccess());
            }
        });
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
