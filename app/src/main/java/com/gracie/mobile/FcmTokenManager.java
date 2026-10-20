package com.gracie.mobile;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class FcmTokenManager {
    private static final String TAG = "FcmTokenManager";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private FcmTokenManager() {
    }

    static void registerCurrentToken() {
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Could not obtain the FCM registration token", task.getException());
                return;
            }
            Log.i(TAG, "Obtained an FCM registration token");
            registerToken(task.getResult());
        });
    }

    static void registerToken(String token) {
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Firebase returned an empty FCM registration token");
            return;
        }
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                SupabaseApi supabaseApi = new SupabaseApi(
                        firebaseAuth,
                        BuildConfig.SUPABASE_URL,
                        BuildConfig.SUPABASE_ANON_KEY);
                supabaseApi.getProfile();
                supabaseApi.registerDeviceToken(token);
                Log.i(TAG, "Registered FCM token with Supabase");
            } catch (IOException | JSONException exception) {
                Log.e(TAG, "Could not register the FCM token with Supabase", exception);
            }
        });
    }
}
