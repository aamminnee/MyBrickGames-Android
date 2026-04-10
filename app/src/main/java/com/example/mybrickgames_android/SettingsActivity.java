package com.example.mybrickgames_android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Switch;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * native activity class for managing application settings.
 */
public class SettingsActivity extends AppCompatActivity {

    private GestureDetector gestureDetector;
    private static final String TAG = "SettingsActivity";

    /**
     * initializes the settings activity, sets up the ui components and listeners.
     *
     * @param savedInstanceState the saved state of the instance
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        gestureDetector = new GestureDetector(this, new SwipeGestureListener());

        Switch switchNotification = findViewById(R.id.switch_notification);
        Button buttonWebSetting = findViewById(R.id.bouton_web_setting);
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);

        menuNavigation.setSelectedItemId(R.id.nav_setting);

        SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = preferences.getBoolean("daily_notif_enabled", true);
        switchNotification.setChecked(isEnabled);

        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean("daily_notif_enabled", isChecked).apply()
        );

        buttonWebSetting.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            intent.putExtra("open_web_settings", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id != R.id.nav_setting) {
                Intent intent = createNavigationIntent(id);
                startActivity(intent);

                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }

    /**
     * creates an intent to navigate to a specific tab in the main activity.
     *
     * @param tabId the target navigation tab id
     * @return the configured intent for navigation
     */
    private Intent createNavigationIntent(int tabId) {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);

        String urlToLoad = "https://mybrickstore.duckdns.org/";
        if (tabId == R.id.nav_play) {
            urlToLoad = "https://mybrickstore.duckdns.org/play";
        } else if (tabId == R.id.nav_profile) {
            urlToLoad = "https://mybrickstore.duckdns.org/compte";
        }

        intent.putExtra("load_url", urlToLoad);
        intent.putExtra("target_tab", tabId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return intent;
    }

    /**
     * intercepts all touch events to allow gesture detection across the entire screen.
     *
     * @param ev the motion event
     * @return true if the event was handled, false otherwise
     */
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * private class to handle swipe gestures on the settings screen.
     */
    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        /**
         * handles the fling gesture to detect horizontal swipes.
         *
         * @param e1 the starting motion event
         * @param e2 the ending motion event
         * @param velocityX the horizontal velocity
         * @param velocityY the vertical velocity
         * @return true if the swipe was consumed, false otherwise
         */
        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            navigateToProfile();
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "erreur de détection du glissement", e);
            }
            return false;
        }
    }

    /**
     * closes the settings activity and navigates to the profile tab in the main activity.
     */
    private void navigateToProfile() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.putExtra("load_url", "https://mybrickstore.duckdns.org/compte");
        intent.putExtra("target_tab", R.id.nav_profile);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        finish();
    }
}