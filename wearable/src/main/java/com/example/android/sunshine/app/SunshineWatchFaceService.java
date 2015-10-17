/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "SunshineFaceService";
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Alpha value for drawing time when in mute mode.
     */
    static final int MUTE_ALPHA = 100;

    /**
     * Alpha value for drawing time when not in mute mode.
     */
    static final int NORMAL_ALPHA = 255;

    /**
     * How often {mUpdateTimeHandler} ticks in milliseconds.
     */
    long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

    static final String COLON_STRING = ":";

    float mColonWidth;

    // Default weather bitmap
    Bitmap mWeatherBitmap;


    @Override
    public MyEngine onCreateEngine() {
        return new MyEngine();
    }

    private class MyEngine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;

        boolean mAmbient;

        Time mTime;
        Date mDate;
        float mXOffset;
        float mYOffset;
        int mBackgroundColor;
        Calendar mCalendar;
        boolean mMute;
        boolean mShouldDrawColons;
        float mIs24AdjustmentFactor;
        SimpleDateFormat mDayFormat;
        SimpleDateFormat mDateFormat;
        float mLineHeight;
        Paint mDatePaint;
        float mDateXOffset;
        float mLineXOffset;
        float mLineEndXOffset;
        float mImageXOffset;
        String mHighTemp = ""; // PlaceHolder
        String mLowTemp = ""; // PlaceHolder
        float mTemperatureXOffset;
        float mTempAdjustmentYOffset;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        GoogleApiClient mGoogleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mWeatherBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_clear); // place holder
            mBackgroundColor = resources.getColor(R.color.digital_background);
            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date));
            mHighTempPaint = createTextPaint(resources.getColor(R.color.digital_temp_high));
            mLowTempPaint = createTextPaint(resources.getColor(R.color.digital_temp_low));

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mTime = new Time();
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            initDateFormats();

            /**
             * Google API Client initialization
             */
            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApiIfAvailable(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        } // end of onCreate()

        @Override // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, MyEngine.this);
        }

        @Override // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult connectionResult) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + connectionResult);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {


            Log.d(TAG, "wear - onDataChanged start");

            //new UpdateWatchFaceTask().execute(dataEvents);
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED &&
                        event.getDataItem().getUri().getPath().equals("/send_weather")) {

                    Log.d(TAG, "wear - onDataChanged processing");

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    DataMap dataMap = dataMapItem.getDataMap();
                    if (dataMap != null) {
                        Asset profileAsset = dataMap.getAsset("weatherIcon");
                        if (profileAsset != null)
                            loadBitmapFromAssetAsync(mGoogleApiClient, profileAsset);
                        mHighTemp = dataMapItem.getDataMap().getString("high");
                        mLowTemp = dataMapItem.getDataMap().getString("low");

                        if (!isInAmbientMode()) {
                            invalidate();
                        }
                    }
                }
                dataEvents.release();
            }

            Log.d(TAG, "wear - onDataChanged done");
        }

        // Snippet from Google sample project

        /**
         * Extracts {@link android.graphics.Bitmap} data from the
         * {@link com.google.android.gms.wearable.Asset}
         */
        private Bitmap loadBitmapFromAsset(GoogleApiClient apiClient, Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }

            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    apiClient, asset).await().getInputStream();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            return BitmapFactory.decodeStream(assetInputStream);
        }


        private void loadBitmapFromAssetAsync(GoogleApiClient apiClient, Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }

            Wearable.DataApi.getFdForAsset(
                    apiClient, asset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
                @Override
                public void onResult(DataApi.GetFdForAssetResult getFdForAssetResult) {
                    Log.d(TAG, "wear - getFdForAssetResult success -> " + getFdForAssetResult.getStatus().isSuccess());
                    if (getFdForAssetResult.getStatus().isSuccess()) {
                        mWeatherBitmap = getBitmapFromStream(getFdForAssetResult.getInputStream());
                        getFdForAssetResult.release();
                        if (!isInAmbientMode())
                            invalidate();

                    }

                }
            });

        }

        private Bitmap getBitmapFromStream(InputStream assetInputStream) {

            if (assetInputStream == null) {
                Log.d(TAG, "Requested a null InputStream.");
                return null;
            }
            return BitmapFactory.decodeStream(assetInputStream);
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            }
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mTextPaint.setAlpha(alpha);
                mDatePaint.setAlpha(alpha);
                mHighTempPaint.setAlpha(alpha);
                mLowTempPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
                initDateFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initDateFormats() {
            mDayFormat = new SimpleDateFormat("E", Locale.getDefault());
            mDayFormat.setCalendar(mCalendar);
            mDateFormat = new SimpleDateFormat("MMM dd yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mDateXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_date_x_offset_round : R.dimen.digital_date_x_offset);
            mLineXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_line_x_offset_round : R.dimen.digital_line_x_offset);
            mLineEndXOffset = resources.getDimension(R.dimen.digital_line_x_end_offset);
            mImageXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_image_x_offset_round : R.dimen.digital_image_x_offset);
            mTemperatureXOffset = resources.getDimension(R.dimen.digital_temp_x_offset);
            mTempAdjustmentYOffset = resources.getDimension(R.dimen.digital_temp_image_adjustment_y_offset);
            mIs24AdjustmentFactor = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_is24_adjustment_factor_round : R.dimen.digital_x_offset_is24_adjustment_factor);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHighTempPaint.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));
            mLowTempPaint.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));
            mColonWidth = mTextPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mBackgroundPaint.setColor(isInAmbientMode() ? Color.BLACK : mBackgroundColor);
            // Draw background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFaceService.this);

            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else if (mCalendar.get(Calendar.HOUR_OF_DAY) > 9) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            canvas.drawText(hourString, x, mYOffset, mTextPaint);
            x += mTextPaint.measureText(hourString);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode() || mMute || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mTextPaint);
            }
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mTextPaint);

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                x = mDateXOffset;

                String dayOfWeek = mDayFormat.format(mDate) + ", ";
                // Day of week
                canvas.drawText(
                        dayOfWeek,
                        x, mYOffset + mLineHeight, mDatePaint);

                x += mDatePaint.measureText(dayOfWeek);
                // Date
                canvas.drawText(
                        mDateFormat.format(mDate),
                        x, mYOffset + mLineHeight, mDatePaint);

                x = mLineXOffset;
                float starty = mYOffset + mLineHeight + mLineHeight;
                float endy = starty;
                float endx = x + mLineEndXOffset;

                canvas.drawLine(x, starty, endx, endy, mDatePaint);

                x = mImageXOffset;
                starty = starty + mLineHeight / 2;

                // Draw bitmap
                canvas.drawBitmap(mWeatherBitmap, x, starty, null);
                x += mWeatherBitmap.getWidth() / 3 + mTemperatureXOffset;
                starty += mLineHeight + mTempAdjustmentYOffset;

                // Draw temperature texts
                canvas.drawText(mHighTemp, x, starty, mHighTempPaint);
                x += mTemperatureXOffset;
                canvas.drawText(mLowTemp, x, starty, mLowTempPaint);


            }

        }


        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyEngine> mWeakReference;

        public EngineHandler(MyEngine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyEngine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
