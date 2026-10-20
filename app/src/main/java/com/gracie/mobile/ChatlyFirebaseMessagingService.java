package com.gracie.mobile;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class ChatlyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "ChatlyFcmService";
    private static final String CHANNEL_ID = "chatly_messages";
    static final String EXTRA_CALL_ACTION = "incoming_call_action";
    static final String ACTION_ACCEPT_CALL = "accept";
    static final String ACTION_REJECT_CALL = "reject";

    @Override
    public void onNewToken(@NonNull String token) {
        FcmTokenManager.registerToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Log.i(TAG, "Received an FCM message");
        if ("incoming_call".equals(message.getData().get("type"))) {
            showIncomingCallNotification(message);
            return;
        }
        if ("call_cancelled".equals(message.getData().get("type"))) {
            cancelIncomingCallNotification(message.getData().get("call_id"));
            return;
        }
        RemoteMessage.Notification remoteNotification = message.getNotification();
        String title = remoteNotification == null ? null : remoteNotification.getTitle();
        String body = remoteNotification == null ? null : remoteNotification.getBody();
        if (title == null || title.isEmpty()) {
            title = message.getData().get("title");
        }
        if (body == null || body.isEmpty()) {
            body = message.getData().get("body");
        }
        if (body == null || body.isEmpty()) {
            body = getString(R.string.notification_new_message);
        }
        showNotification(title, body);
    }

    private void showIncomingCallNotification(RemoteMessage message) {
        String callId = message.getData().get("call_id");
        if (callId == null || callId.isEmpty()) {
            Log.w(TAG, "Ignoring incoming-call push without a call ID");
            return;
        }
        if (MainActivity.isActivityForeground()) {
            return;
        }
        CallNotificationChannel.create(this);
        String callerName = message.getData().get("caller_name");
        String mediaType = message.getData().get("media_type");
        boolean videoCall = "video".equals(mediaType);
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra("incoming_call_id", callId)
                .putExtra("incoming_caller_name", callerName)
                .putExtra("incoming_media_type", mediaType)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                callId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent acceptIntent = createIncomingCallAction(
                callId, callerName, mediaType, ACTION_ACCEPT_CALL);
        PendingIntent rejectIntent = createIncomingCallAction(
                callId, callerName, mediaType, ACTION_REJECT_CALL);

        String title = callerName == null || callerName.isEmpty()
                ? getString(R.string.app_name) : callerName;
        String body = getString(videoCall
                ? R.string.incoming_video_call : R.string.incoming_voice_call, title);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                this, CallNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_chats)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setFullScreenIntent(contentIntent, true)
                .setContentIntent(contentIntent)
                .addAction(R.drawable.ic_calls, getString(R.string.answer_call), acceptIntent)
                .addAction(R.drawable.ic_calls, getString(R.string.reject_call), rejectIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(45_000);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(callId.hashCode(), notification.build());
        } else {
            Log.w(TAG, "Notifications are disabled; incoming call cannot be shown");
        }
    }

    private PendingIntent createIncomingCallAction(
            String callId, String callerName, String mediaType, String action) {
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra("incoming_call_id", callId)
                .putExtra("incoming_caller_name", callerName)
                .putExtra("incoming_media_type", mediaType)
                .putExtra(EXTRA_CALL_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int requestCode = callId.hashCode() * 31 + action.hashCode();
        return PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void cancelIncomingCallNotification(String callId) {
        if (callId == null || callId.isEmpty()) {
            Log.w(TAG, "Ignoring call-cancellation push without a call ID");
            return;
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(callId, 0);
        notificationManager.cancel(callId.hashCode());
    }

    private void showNotification(String title, String body) {
        createNotificationChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chats)
                .setContentTitle(title == null || title.isEmpty()
                        ? getString(R.string.app_name) : title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(
                    (int) (System.currentTimeMillis() & 0x7fffffff), notification.build());
        } else {
            Log.w(TAG, "Notifications are disabled for Chatly on this device");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.notification_channel_messages_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

}
