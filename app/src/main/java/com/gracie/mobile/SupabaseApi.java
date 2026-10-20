package com.gracie.mobile;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

final class SupabaseApi {
    private static final String TAG = "SupabaseApi";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private final FirebaseAuth firebaseAuth;
    private final String supabaseUrl;
    private final String anonKey;

    SupabaseApi(FirebaseAuth firebaseAuth, String supabaseUrl, String anonKey) {
        this.firebaseAuth = firebaseAuth;
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.trim();
        this.anonKey = anonKey == null ? "" : anonKey.trim();
    }

    JSONObject getProfile() throws IOException, JSONException {
        return request("profile", "GET", null, null);
    }

    JSONObject updateProfile(JSONObject profile) throws IOException, JSONException {
        return request("profile", "PATCH", null, profile);
    }

    void registerDeviceToken(String token) throws IOException, JSONException {
        request(
                "device-tokens",
                "POST",
                null,
                new JSONObject().put("token", token).put("platform", "android"));
    }

    JSONArray matchContacts(JSONArray phoneNumbers) throws IOException, JSONException {
        JSONObject response = request(
                "sync-contacts",
                "POST",
                null,
                new JSONObject().put("phone_numbers", phoneNumbers));
        JSONArray matches = response.optJSONArray("matches");
        if (matches == null) {
            throw new JSONException("Contact matching response is missing matches");
        }
        return matches;
    }

    JSONArray getConversations(String search, int filter, int limit, int offset)
            throws IOException, JSONException {
        String filterName = filter == 1 ? "unread" : filter == 2 ? "groups" : "all";
        Uri uri = Uri.parse("https://placeholder.invalid")
                .buildUpon()
                .appendQueryParameter("search", search)
                .appendQueryParameter("filter", filterName)
                .appendQueryParameter("limit", String.valueOf(limit))
                .appendQueryParameter("offset", String.valueOf(offset))
                .build();
        JSONObject response = request(
                "conversations", "GET", uri.getEncodedQuery(), null);
        return response.optJSONArray("conversations") == null
                ? new JSONArray() : response.getJSONArray("conversations");
    }

    String openDirectConversation(String peerId) throws IOException, JSONException {
        JSONObject response = request(
                "conversations",
                "POST",
                null,
                new JSONObject().put("peer_id", peerId));
        return response.getString("id");
    }

    JSONArray getMessages(String conversationId) throws IOException, JSONException {
        Uri uri = Uri.parse("https://placeholder.invalid")
                .buildUpon()
                .appendQueryParameter("conversation_id", conversationId)
                .appendQueryParameter("limit", "100")
                .build();
        JSONObject response = request("messages", "GET", uri.getEncodedQuery(), null);
        return response.optJSONArray("messages") == null
                ? new JSONArray() : response.getJSONArray("messages");
    }

    JSONObject sendMessage(String conversationId, String body)
            throws IOException, JSONException {
        JSONObject response = request(
                "messages",
                "POST",
                null,
                new JSONObject().put("conversation_id", conversationId)
                        .put("body", body));
        String notificationError = response.optString("notification_error", "");
        JSONObject delivery = response.optJSONObject("notification_delivery");
        if (delivery != null) {
            Log.i(TAG, "FCM delivery: attempted=" + delivery.optInt("attempted")
                    + ", sent=" + delivery.optInt("sent")
                    + ", failed=" + delivery.optInt("failed"));
        } else {
            Log.w(TAG, "Message response did not include FCM delivery counts");
        }
        if (!notificationError.isEmpty()) {
            Log.e(TAG, notificationError);
        } else if (delivery != null && delivery.optInt("attempted") == 0) {
            Log.w(TAG, "No recipient FCM tokens are registered");
        } else if (delivery != null && delivery.optInt("failed") > 0) {
            Log.e(TAG, "One or more FCM notification sends failed");
        }
        return response.getJSONObject("message");
    }

    JSONObject call(String action, JSONObject values) throws IOException, JSONException {
        JSONObject body = values == null ? new JSONObject() : new JSONObject(values.toString());
        body.put("action", action);
        return request("calls", "POST", null, body);
    }

    JSONArray getCallHistory() throws IOException, JSONException {
        JSONObject response = call("history", null);
        JSONArray calls = response.optJSONArray("calls");
        if (calls == null) {
            throw new JSONException("Call history response is missing calls");
        }
        return calls;
    }

    void updateConversation(
            String conversationId, boolean isPinned, boolean isMuted)
            throws IOException, JSONException {
        request(
                "conversations",
                "PATCH",
                null,
                new JSONObject().put("conversation_id", conversationId)
                        .put("is_pinned", isPinned)
                        .put("is_muted", isMuted));
    }

    private JSONObject request(
            String function,
            String method,
            String query,
            JSONObject body) throws IOException, JSONException {
        if (!supabaseUrl.matches("^https://[A-Za-z0-9.-]+(?::\\d+)?/?$")) {
            throw new IOException(
                    "Set supabase.url in local.properties to your HTTPS Supabase project URL.");
        }
        if (anonKey.isEmpty()) {
            throw new IOException(
                    "Set supabase.anonKey in local.properties to the Supabase anon/publishable key.");
        }

        String baseUrl = supabaseUrl.endsWith("/")
                ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        String endpoint = baseUrl + "/functions/v1/" + function
                + (query == null || query.isEmpty() ? "" : "?" + query);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Authorization", "Bearer " + requireIdToken());
            connection.setRequestProperty("apikey", anonKey);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = readBody(input);
            if (status < 200 || status >= 300) {
                String detail;
                try {
                    detail = new JSONObject(responseBody).optString("error", responseBody);
                } catch (JSONException ignored) {
                    detail = responseBody;
                }
                throw new IOException("Supabase request failed (" + status + "): " + detail);
            }
            if (responseBody.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private String requireIdToken() throws IOException {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            throw new IOException("Sign in with your phone number before connecting to chat.");
        }
        try {
            return Tasks.await(user.getIdToken(false)).getToken();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Sign-in was interrupted. Please retry.", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Could not refresh your Firebase sign-in. Please sign in again.",
                    exception.getCause());
        }
    }

    private String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }
}
