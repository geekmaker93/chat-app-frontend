package com.gracie.mobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gracie.mobile.databinding.ActivityMainBinding;
import com.gracie.mobile.databinding.ScreenContactsSyncBinding;
import com.gracie.mobile.databinding.ScreenConversationBinding;
import com.gracie.mobile.databinding.ScreenCallsBinding;
import com.gracie.mobile.databinding.ScreenOtpBinding;
import com.gracie.mobile.databinding.ScreenPhoneSignupBinding;
import com.gracie.mobile.databinding.ScreenProfileSetupBinding;
import com.gracie.mobile.databinding.ScreenSettingsBinding;
import com.gracie.mobile.databinding.ScreenVoiceCallBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static volatile boolean activityForeground;

    static boolean isActivityForeground() {
        return activityForeground;
    }

    private void showCallsScreen() {
        screen = Screen.CALLS;
        callsBinding = ScreenCallsBinding.inflate(getLayoutInflater());
        setContentView(callsBinding.getRoot());
        configureSystemBars(callsBinding.getRoot());
        callsBinding.callsBottomNavigation.setSelectedItemId(R.id.navigation_calls);
        callsBinding.callsBottomNavigation.setOnItemSelectedListener(item -> {
            showMainDestination(item.getItemId());
            return true;
        });
        callsBinding.callHistoryList.setLayoutManager(new LinearLayoutManager(this));
        callsBinding.callHistoryList.setAdapter(new CallHistoryAdapter(
                new CallHistoryAdapter.Listener() {
                    @Override
                    public void onCallRequested(CallHistoryEntry call) {
                        openCallHistoryConversation(call, true);
                    }

                    @Override
                    public void onMessageRequested(CallHistoryEntry call) {
                        openCallHistoryConversation(call, false);
                    }
                }));
        loadCallHistory();
        DeviceContactAdapter callsAdapter =
                new DeviceContactAdapter(this::startCallFromContact, R.string.call_action);
        callsBinding.contactsList.setLayoutManager(new LinearLayoutManager(this));
        callsBinding.contactsList.setAdapter(callsAdapter);
        callsBinding.contactSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                callsAdapter.filter(text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        callsBinding.grantContactsButton.setOnClickListener(view -> requestContactAccess());
        callsBinding.newCallButton.setOnClickListener(view -> toggleCallsPanel());
        callsBinding.dialPadToggle.setOnClickListener(view -> toggleDialPad());
        callsBinding.dialNumber.setShowSoftInputOnFocus(false);
        callsBinding.dialNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                callsBinding.dialNumberLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        callsBinding.dialCallButton.setOnClickListener(view -> startCallFromDialPad());
        callsBinding.dialBackspace.setOnClickListener(view -> {
            Editable number = callsBinding.dialNumber.getText();
            if (number != null && number.length() > 0) {
                number.delete(number.length() - 1, number.length());
            }
        });
        bindDialKey(callsBinding.dialKey1, "1");
        bindDialKey(callsBinding.dialKey2, "2");
        bindDialKey(callsBinding.dialKey3, "3");
        bindDialKey(callsBinding.dialKey4, "4");
        bindDialKey(callsBinding.dialKey5, "5");
        bindDialKey(callsBinding.dialKey6, "6");
        bindDialKey(callsBinding.dialKey7, "7");
        bindDialKey(callsBinding.dialKey8, "8");
        bindDialKey(callsBinding.dialKey9, "9");
        bindDialKey(callsBinding.dialKeyPlus, "+");
        bindDialKey(callsBinding.dialKey0, "0");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            if (deviceContactsLoaded) {
                callsAdapter.submitList(deviceContacts);
                if (deviceContacts.isEmpty()) {
                    callsBinding.contactsCount.setText(R.string.contacts_no_matches);
                } else {
                    callsBinding.contactsCount.setText(
                            getString(R.string.matched_call_contacts, deviceContacts.size()));
                }
                callsBinding.grantContactsButton.setVisibility(View.GONE);
            } else {
                loadCallContacts();
            }
        } else {
            callsBinding.contactsCount.setText(R.string.contacts_not_loaded);
        }
    }

    private void toggleCallsPanel() {
        if (callsBinding == null) {
            return;
        }
        boolean showNewCallPanel =
                callsBinding.newCallPanel.getVisibility() != View.VISIBLE;
        callsBinding.callHistoryPanel.setVisibility(
                showNewCallPanel ? View.GONE : View.VISIBLE);
        callsBinding.newCallPanel.setVisibility(
                showNewCallPanel ? View.VISIBLE : View.GONE);
        callsBinding.callsTitle.setText(
                showNewCallPanel ? R.string.new_call_title : R.string.calls_title);
        callsBinding.newCallButton.setIconResource(
                showNewCallPanel ? R.drawable.ic_close : R.drawable.ic_add_call);
        callsBinding.newCallButton.setContentDescription(getString(showNewCallPanel
                ? R.string.call_history_accessibility : R.string.new_call_accessibility));
        if (!showNewCallPanel && callsBinding.dialPadPanel.getVisibility() == View.VISIBLE) {
            callsBinding.dialPadPanel.setVisibility(View.GONE);
            callsBinding.dialPadToggle.setText(R.string.show_dial_pad);
        }
    }

    private void toggleDialPad() {
        if (callsBinding == null) {
            return;
        }
        boolean showDialPad = callsBinding.dialPadPanel.getVisibility() != View.VISIBLE;
        callsBinding.dialPadPanel.setVisibility(showDialPad ? View.VISIBLE : View.GONE);
        callsBinding.dialPadToggle.setText(
                showDialPad ? R.string.hide_dial_pad : R.string.show_dial_pad);
        if (showDialPad) {
            callsBinding.dialNumber.requestFocus();
        }
    }

    private void bindDialKey(View key, String value) {
        key.setContentDescription(getString(R.string.dial_pad_key_accessibility, value));
        key.setOnClickListener(view -> {
            if (callsBinding == null) {
                return;
            }
            Editable number = callsBinding.dialNumber.getText();
            if (number != null) {
                int cursor = Math.max(callsBinding.dialNumber.getSelectionStart(), 0);
                number.insert(cursor, value);
            }
        });
    }

    private void loadCallContacts() {
        if (callsBinding == null || screen != Screen.CALLS) {
            return;
        }
        callsBinding.contactsCount.setText(R.string.contacts_loading);
        callsBinding.grantContactsButton.setVisibility(View.GONE);
        contactsExecutor.execute(() -> {
            List<DeviceContact> loadedContacts;
            Map<String, String> matchedUsers = new HashMap<>();
            String syncError = null;
            try {
                loadedContacts = queryDeviceContacts();
                JSONArray phoneNumbers = new JSONArray();
                for (DeviceContact contact : loadedContacts) {
                    phoneNumbers.put(contact.e164PhoneNumber);
                }
                JSONArray matches = supabaseApi.matchContacts(phoneNumbers);
                for (int index = 0; index < matches.length(); index++) {
                    JSONObject match = matches.getJSONObject(index);
                    matchedUsers.put(match.optString("phone_number"),
                            match.optString("user_id"));
                }
            } catch (Exception exception) {
                loadedContacts = new ArrayList<>();
                syncError = exception.getLocalizedMessage();
            }
            List<DeviceContact> contacts = loadedContacts;
            Map<String, String> users = matchedUsers;
            String error = syncError;
            runOnUiThread(() -> {
                if (screen != Screen.CALLS || callsBinding == null) {
                    return;
                }
                deviceContacts.clear();
                for (DeviceContact contact : contacts) {
                    contact.registeredUserId = users.get(contact.e164PhoneNumber);
                    if (contact.registeredUserId != null
                            && !contact.registeredUserId.isEmpty()) {
                        deviceContacts.add(contact);
                    }
                }
                RecyclerView.Adapter<?> adapter = callsBinding.contactsList.getAdapter();
                if (adapter instanceof DeviceContactAdapter) {
                    ((DeviceContactAdapter) adapter).submitList(deviceContacts);
                }
                if (error != null) {
                    deviceContactsLoaded = false;
                    callsBinding.contactsCount.setText(
                            getString(R.string.contact_sync_error, error));
                    callsBinding.grantContactsButton.setVisibility(View.VISIBLE);
                } else if (contacts.isEmpty()) {
                    deviceContactsLoaded = true;
                    callsBinding.contactsCount.setText(R.string.contacts_none);
                } else if (deviceContacts.isEmpty()) {
                    deviceContactsLoaded = true;
                    callsBinding.contactsCount.setText(R.string.contacts_no_matches);
                } else {
                    deviceContactsLoaded = true;
                    callsBinding.contactsCount.setText(
                            getString(R.string.matched_call_contacts, deviceContacts.size()));
                }
            });
        });
    }

    private void loadCallHistory() {
        if (callsBinding == null || screen != Screen.CALLS) {
            return;
        }
        callsBinding.callHistoryStatus.setVisibility(View.VISIBLE);
        callsBinding.callHistoryStatus.setText(R.string.call_history_loading);
        conversationExecutor.execute(() -> {
            List<CallHistoryEntry> entries = new ArrayList<>();
            String errorMessage = null;
            try {
                String currentUserId = firebaseAuth.getCurrentUser() == null
                        ? "" : firebaseAuth.getCurrentUser().getUid();
                JSONArray calls = supabaseApi.getCallHistory();
                for (int index = 0; index < calls.length(); index++) {
                    JSONObject call = calls.optJSONObject(index);
                    if (call == null) {
                        continue;
                    }
                    long timestamp = java.time.Instant.parse(
                            call.getString("created_at")).toEpochMilli();
                    String peerId = call.optString("peer_id");
                    if (peerId.isEmpty()) {
                        String callerId = call.optString("caller_id");
                        String calleeId = call.optString("callee_id");
                        peerId = currentUserId.equals(callerId) ? calleeId : callerId;
                    }
                    entries.add(new CallHistoryEntry(
                            call.optString("peer_name", getString(R.string.contact_on_chatly)),
                            peerId,
                            currentUserId.equals(call.optString("caller_id")),
                            call.optBoolean("missed", false),
                            "rejected".equals(call.optString("status")),
                            "video".equals(call.optString("media_type")),
                            timestamp));
                }
            } catch (Exception exception) {
                errorMessage = exception.getLocalizedMessage();
            }
            List<CallHistoryEntry> history = entries;
            String error = errorMessage;
            runOnUiThread(() -> {
                if (screen != Screen.CALLS || callsBinding == null) {
                    return;
                }
                RecyclerView.Adapter<?> adapter =
                        callsBinding.callHistoryList.getAdapter();
                if (adapter instanceof CallHistoryAdapter) {
                    ((CallHistoryAdapter) adapter).submitList(history);
                }
                if (error != null) {
                    callsBinding.callHistoryStatus.setText(
                            getString(R.string.call_history_load_error, error));
                    callsBinding.callHistoryStatus.setVisibility(View.VISIBLE);
                } else if (history.isEmpty()) {
                    callsBinding.callHistoryStatus.setText(R.string.call_history_empty);
                    callsBinding.callHistoryStatus.setVisibility(View.VISIBLE);
                } else {
                    callsBinding.callHistoryStatus.setVisibility(View.GONE);
                }
            });
        });
    }

    private void openCallHistoryConversation(CallHistoryEntry call, boolean startCall) {
        if (call.peerId == null || call.peerId.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.call_history_peer_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        conversationExecutor.execute(() -> {
            try {
                String conversationId = supabaseApi.openDirectConversation(call.peerId);
                ChatConversation conversation = new ChatConversation(
                        conversationId,
                        call.peerId,
                        call.peerName,
                        getString(R.string.no_messages_yet),
                        0,
                        0,
                        false,
                        false,
                        false,
                        null);
                org.json.JSONArray messages = startCall
                        ? null : supabaseApi.getMessages(conversationId);
                chatDatabase.conversationDao().save(conversation);
                if (!startCall) {
                    chatDatabase.conversationDao().markRead(conversationId);
                    conversation.unreadCount = 0;
                }
                runOnUiThread(() -> {
                    selectedConversation = conversation;
                    if (startCall) {
                        requestOutgoingCall(conversation, "audio");
                    } else {
                        previousMainScreen = Screen.CALLS;
                        displayConversation(conversation, messages);
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.backend_request_error,
                                exception.getLocalizedMessage()),
                        Snackbar.LENGTH_LONG).show());
            }
        });
    }

    private void startCallFromDialPad() {
        if (callsBinding == null) {
            return;
        }
        String enteredNumber = callsBinding.dialNumber.getText() == null
                ? "" : callsBinding.dialNumber.getText().toString().trim();
        String region = phoneRegion.isEmpty()
                ? Locale.getDefault().getCountry() : phoneRegion;
        String normalizedNumber;
        try {
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    PhoneNumberUtil.getInstance().parse(enteredNumber, region);
            if (!PhoneNumberUtil.getInstance().isValidNumber(parsed)) {
                throw new NumberParseException(
                        NumberParseException.ErrorType.NOT_A_NUMBER, "Invalid phone number");
            }
            normalizedNumber = PhoneNumberUtil.getInstance().format(
                    parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            callsBinding.dialNumberLayout.setError(getString(R.string.phone_number_invalid));
            return;
        }
        String phoneNumber = normalizedNumber;
        callsBinding.dialCallButton.setEnabled(false);
        callsBinding.dialNumberLayout.setError(null);
        callExecutor.execute(() -> {
            try {
                JSONArray numbers = new JSONArray().put(phoneNumber);
                JSONArray matches = supabaseApi.matchContacts(numbers);
                String userId = null;
                if (matches.length() > 0) {
                    userId = matches.getJSONObject(0).optString("user_id", "");
                }
                if (userId == null || userId.isEmpty()) {
                    runOnUiThread(() -> {
                        if (callsBinding != null) {
                            callsBinding.dialCallButton.setEnabled(true);
                            callsBinding.dialNumberLayout.setError(
                                    getString(R.string.dial_number_not_registered));
                        }
                    });
                    return;
                }
                DeviceContact contact = null;
                for (DeviceContact candidate : deviceContacts) {
                    if (userId.equals(candidate.registeredUserId)) {
                        contact = candidate;
                        break;
                    }
                }
                DeviceContact registeredContact = contact == null
                        ? new DeviceContact(phoneNumber, phoneNumber, null, phoneNumber)
                        : contact;
                registeredContact.registeredUserId = userId;
                runOnUiThread(() -> {
                    if (callsBinding != null) {
                        callsBinding.dialCallButton.setEnabled(true);
                    }
                    startCallFromContact(registeredContact);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (callsBinding != null) {
                        callsBinding.dialCallButton.setEnabled(true);
                        callsBinding.dialNumberLayout.setError(
                                getString(R.string.backend_request_error,
                                        exception.getLocalizedMessage()));
                    }
                });
            }
        });
    }

    private void startCallFromContact(DeviceContact contact) {
        if (contact.registeredUserId == null || contact.registeredUserId.isEmpty()) {
            return;
        }
        callsBinding.dialCallButton.setEnabled(false);
        callExecutor.execute(() -> {
            try {
                String conversationId = supabaseApi.openDirectConversation(
                        contact.registeredUserId);
                ChatConversation conversation = new ChatConversation(
                        conversationId,
                        contact.registeredUserId,
                        contact.name,
                        getString(R.string.no_messages_yet),
                        0,
                        0,
                        false,
                        false,
                        false,
                        contact.photoUri);
                runOnUiThread(() -> {
                    if (callsBinding != null) {
                        callsBinding.dialCallButton.setEnabled(true);
                    }
                    selectedConversation = conversation;
                    requestOutgoingCall(conversation, "audio");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (callsBinding != null) {
                        callsBinding.dialCallButton.setEnabled(true);
                    }
                    Snackbar.make(findViewById(android.R.id.content),
                            getString(R.string.voice_call_failed,
                                    exception.getLocalizedMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private enum Screen {
        PHONE, OTP, PROFILE, CONTACTS, CHATS, CALLS, SETTINGS, CONVERSATION, VOICE_CALL
    }

    private static final String APP_PREFERENCES = "chatly_preferences";
    private static final String CACHED_DISPLAY_NAME = "display_name";
    private static final String CACHED_USERNAME = "username";
    private static final String CACHED_BIO = "bio";
    private static final String PHONE_REGION = "phone_region";
    private static final String THEME_MODE = "theme_mode";
    private static final String INSTANCE_SCREEN = "instance_screen";
    private static final int CONVERSATION_PAGE_SIZE = 20;
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,23}");

    private final ExecutorService contactsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService conversationExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService callExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<DeviceContact> deviceContacts = new ArrayList<>();
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final SupabaseApi supabaseApi = new SupabaseApi(
            FirebaseAuth.getInstance(), BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_ANON_KEY);
    private ActivityResultLauncher<String> contactsPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> profilePhotoLauncher;
    private ActivityResultLauncher<String[]> callPermissionLauncher;

    private Screen screen = Screen.PHONE;
    private Screen previousMainScreen = Screen.CONTACTS;
    private String phoneNumber = "";
    private String phoneRegion = "";
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private String profileDisplayName = "";
    private String profileUsername = "";
    private String profileBio = "";
    private String profilePhotoUri = "";
    private boolean profileOpenedFromMain;
    private boolean profileSetupComplete;
    private boolean deviceContactsLoaded;
    private boolean contactsPermissionGranted;
    private boolean contactsPermissionRequestInFlight;
    private ChatConversation selectedConversation;
    private ChatDatabase chatDatabase;
    private ConversationAdapter conversationAdapter;
    private LinearLayoutManager conversationLayoutManager;
    private String conversationSearch = "";
    private int conversationFilter;
    private int conversationOffset;
    private int conversationRequestId;
    private boolean conversationPageLoading;
    private boolean hasMoreConversations = true;
    private Runnable pendingConversationSearch;
    private ActivityMainBinding mainBinding;
    private ScreenPhoneSignupBinding phoneBinding;
    private ScreenOtpBinding otpBinding;
    private ScreenProfileSetupBinding profileBinding;
    private ScreenContactsSyncBinding contactsBinding;
    private ScreenCallsBinding callsBinding;
    private ScreenConversationBinding conversationBinding;
    private ScreenSettingsBinding settingsBinding;
    private DeviceContactAdapter contactAdapter;
    private ScreenVoiceCallBinding voiceCallBinding;
    private VoiceCallSession voiceCallSession;
    private AudioManager audioManager;
    private int previousAudioMode;
    private boolean previousSpeakerphoneState;
    private boolean speakerphoneEnabled;
    private String activeCallId;
    private long lastCallSignalId;
    private String promptedIncomingCallId;
    private String notificationCallId;
    private String notificationCallerName;
    private String notificationMediaType;
    private String notificationCallAction;
    private JSONObject pendingIncomingCall;
    private boolean incomingCallRequestInFlight;
    private boolean activeCallRequestInFlight;
    private boolean endingCallRequestInFlight;
    private boolean callEnded;
    private boolean incomingCallAwaitingAnswer;
    private boolean microphoneMuted;
    private String pendingOutgoingMediaType;
    private final Runnable incomingCallPoll = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || firebaseAuth.getCurrentUser() == null
                    || activeCallId != null) {
                return;
            }
            if (!incomingCallRequestInFlight) {
                incomingCallRequestInFlight = true;
                callExecutor.execute(() -> {
                    JSONObject response = null;
                    try {
                        response = supabaseApi.call("incoming", new JSONObject());
                    } catch (Exception exception) {
                        android.util.Log.w("ChatlyCalls",
                                "Could not check incoming calls", exception);
                    }
                    JSONObject result = response;
                    runOnUiThread(() -> {
                        incomingCallRequestInFlight = false;
                        showIncomingCallIfPresent(result);
                    });
                });
            }
            mainHandler.postDelayed(this, 2_000);
        }
    };
    private final Runnable activeCallPoll = new Runnable() {
        @Override
        public void run() {
            if (activeCallId == null || callEnded || activeCallRequestInFlight) {
                return;
            }
            activeCallRequestInFlight = true;
            String callId = activeCallId;
            long afterId = lastCallSignalId;
            callExecutor.execute(() -> {
                JSONObject result = null;
                try {
                    result = supabaseApi.call(
                            "poll",
                            new JSONObject().put("call_id", callId)
                                    .put("after_id", afterId));
                } catch (Exception exception) {
                    android.util.Log.w("ChatlyCalls", "Could not poll call signals", exception);
                }
                JSONObject response = result;
                runOnUiThread(() -> {
                    activeCallRequestInFlight = false;
                    mainHandler.postDelayed(this, 700);
                    if (!callId.equals(activeCallId) || response == null) {
                        return;
                    }
                    JSONObject call = response.optJSONObject("call");
                    if (call != null) {
                        String status = call.optString("status");
                        if ("rejected".equals(status) || "ended".equals(status)
                                || "expired".equals(status) || "cancelled".equals(status)
                                || "canceled".equals(status)) {
                            finishVoiceCall();
                            return;
                        }
                    }
                    JSONArray signals = response.optJSONArray("signals");
                    if (signals != null && voiceCallSession != null) {
                        for (int index = 0; index < signals.length(); index++) {
                            JSONObject row = signals.optJSONObject(index);
                            if (row == null) {
                                continue;
                            }
                            lastCallSignalId = Math.max(
                                    lastCallSignalId, row.optLong("id", 0));
                            voiceCallSession.handleRemoteSignal(
                                    row.optJSONObject("signal"));
                        }
                    }
                });
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contactsPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        captureIncomingCallIntent(getIntent());
        createMessageNotificationChannel();
        CallNotificationChannel.create(this);
        chatDatabase = ChatDatabase.getInstance(this);
        int themeMode = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
                .getInt(THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);
        contactsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    contactsPermissionRequestInFlight = false;
                    contactsPermissionGranted = ContextCompat.checkSelfPermission(
                            this, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED;
                    if (contactsPermissionGranted) {
                        if (screen == Screen.CALLS) {
                            loadCallContacts();
                        } else {
                            loadDeviceContacts();
                        }
                    } else if (screen == Screen.CONTACTS && contactsBinding != null) {
                        contactsBinding.syncContactsButton.setText(R.string.sync_contacts);
                        contactsBinding.contactsCount.setText(
                                R.string.contacts_permission_denied);
                    } else if (screen == Screen.CALLS && callsBinding != null) {
                        callsBinding.contactsCount.setText(
                                R.string.contacts_permission_denied);
                        callsBinding.grantContactsButton.setVisibility(View.VISIBLE);
                    }
                });
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });
        callPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                    boolean microphoneGranted = ContextCompat.checkSelfPermission(
                            this, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED;
                    String permissionMediaType = pendingIncomingCall != null
                            ? pendingIncomingCall.optString("media_type", "audio")
                            : pendingOutgoingMediaType;
                    boolean cameraGranted = !"video".equals(permissionMediaType)
                            || ContextCompat.checkSelfPermission(
                                    this, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED;
                    if (!microphoneGranted || !cameraGranted) {
                        pendingIncomingCall = null;
                        pendingOutgoingMediaType = null;
                        Snackbar.make(findViewById(android.R.id.content),
                                microphoneGranted
                                        ? R.string.video_call_camera_permission
                                        : R.string.voice_call_permission,
                                Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    if (pendingIncomingCall != null) {
                        JSONObject call = pendingIncomingCall;
                        pendingIncomingCall = null;
                        acceptIncomingCall(call);
                    } else if (pendingOutgoingMediaType != null
                            && selectedConversation != null) {
                        String mediaType = pendingOutgoingMediaType;
                        pendingOutgoingMediaType = null;
                        startOutgoingCall(selectedConversation, mediaType);
                    }
                });
        profilePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null && screen == Screen.PROFILE && profileBinding != null) {
                        profilePhotoUri = uri.toString();
                        profileBinding.profilePhoto.setImageURI(uri);
                    }
                });
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        registerBackNavigation();

        android.content.SharedPreferences appPreferences =
                getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);
        profileDisplayName = appPreferences.getString(CACHED_DISPLAY_NAME, "");
        profileUsername = appPreferences.getString(CACHED_USERNAME, "");
        profileBio = appPreferences.getString(CACHED_BIO, "");
        phoneRegion = appPreferences.getString(PHONE_REGION, "");
        if (firebaseAuth.getCurrentUser() != null) {
            Screen restoredScreen = savedInstanceState == null ? Screen.CHATS
                    : screenFromName(savedInstanceState.getString(INSTANCE_SCREEN));
            if (savedInstanceState != null && restoredScreen == Screen.SETTINGS
                    && !profileDisplayName.trim().isEmpty()) {
                profileSetupComplete = true;
                showSettingsScreen();
            } else {
                loadAuthenticatedProfile(restoredScreen);
            }
        } else {
            showPhoneSignup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityForeground = true;
        boolean hasContactsPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        if (hasContactsPermission != contactsPermissionGranted) {
            contactsPermissionGranted = hasContactsPermission;
            if (!contactsPermissionRequestInFlight) {
                refreshContactsAfterPermissionChange(hasContactsPermission);
            }
        }
        if (screen == Screen.CALLS && activeCallId == null) {
            loadCallHistory();
        }
        if (firebaseAuth.getCurrentUser() != null && activeCallId == null) {
            mainHandler.removeCallbacks(incomingCallPoll);
            mainHandler.post(incomingCallPoll);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureIncomingCallIntent(intent);
        if (firebaseAuth.getCurrentUser() != null && activeCallId == null) {
            mainHandler.removeCallbacks(incomingCallPoll);
            mainHandler.post(incomingCallPoll);
        }
    }

    private void captureIncomingCallIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        notificationCallId = intent.getStringExtra("incoming_call_id");
        if (notificationCallId == null) {
            notificationCallId = intent.getStringExtra("call_id");
        }
        notificationCallerName = intent.getStringExtra("incoming_caller_name");
        if (notificationCallerName == null) {
            notificationCallerName = intent.getStringExtra("caller_name");
        }
        notificationMediaType = intent.getStringExtra("incoming_media_type");
        if (notificationMediaType == null) {
            notificationMediaType = intent.getStringExtra("media_type");
        }
        notificationCallAction = intent.getStringExtra(
                ChatlyFirebaseMessagingService.EXTRA_CALL_ACTION);
    }

    @Override
    protected void onPause() {
        activityForeground = false;
        mainHandler.removeCallbacks(incomingCallPoll);
        super.onPause();
    }

    private void createMessageNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                "chatly_messages",
                getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.notification_channel_messages_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Screen screenFromName(String screenName) {
        if (Screen.SETTINGS.name().equals(screenName)) {
            return Screen.SETTINGS;
        }
        if (Screen.CALLS.name().equals(screenName)) {
            return Screen.CALLS;
        }
        return Screen.CHATS;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(INSTANCE_SCREEN, screen.name());
        super.onSaveInstanceState(outState);
    }

    private void registerBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (screen == Screen.CONVERSATION) {
                    returnFromConversation();
                } else if (screen == Screen.VOICE_CALL) {
                    if (!callEnded) {
                        endVoiceCall();
                    } else {
                        returnFromVoiceCall();
                    }
                } else if (screen == Screen.CALLS && callsBinding != null
                        && callsBinding.newCallPanel.getVisibility() == View.VISIBLE) {
                    toggleCallsPanel();
                } else if (screen == Screen.OTP) {
                    showPhoneSignup();
                } else if (screen == Screen.PROFILE) {
                    if (profileOpenedFromMain) {
                        profileOpenedFromMain = false;
                        showMainScreen(Screen.CHATS);
                    } else {
                        showOtpScreen();
                    }
                } else if (screen == Screen.CONTACTS && profileSetupComplete) {
                    showMainScreen(Screen.CHATS);
                } else if (screen == Screen.CONTACTS) {
                    showProfileSetup();
                } else if (screen != Screen.CHATS && screen != Screen.PHONE) {
                    showMainScreen(Screen.CHATS);
                } else if (screen == Screen.CHATS) {
                    finish();
                } else {
                    finish();
                }
            }
        });
    }

    private void configureSystemBars(View root) {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        boolean isNight = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, root);
        controller.setAppearanceLightStatusBars(!isNight);
        controller.setAppearanceLightNavigationBars(!isNight);

        View bottomNavigation = root.findViewById(R.id.bottom_navigation);
        if (bottomNavigation == null) {
            bottomNavigation = root.findViewById(R.id.contacts_bottom_navigation);
        }
        final View navigationView = bottomNavigation;
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(systemBars.bottom, ime.bottom);
            boolean extendNavigationBackground = navigationView != null
                    && navigationView.getVisibility() == View.VISIBLE
                    && ime.bottom == 0;
            view.setPadding(view.getPaddingLeft(), systemBars.top,
                    view.getPaddingRight(), extendNavigationBackground ? 0 : bottom);
            if (navigationView != null) {
                navigationView.setPadding(navigationView.getPaddingLeft(),
                        navigationView.getPaddingTop(), navigationView.getPaddingRight(),
                        extendNavigationBackground ? systemBars.bottom : 0);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void showPhoneSignup() {
        screen = Screen.PHONE;
        phoneBinding = ScreenPhoneSignupBinding.inflate(getLayoutInflater());
        setContentView(phoneBinding.getRoot());
        configureSystemBars(phoneBinding.getRoot());

        MaterialAutoCompleteTextView countryCode = phoneBinding.countryCodeInput;
        countryCode.setSimpleItems(getResources().getStringArray(R.array.country_codes));
        countryCode.setOnItemClickListener((parent, view, position, id) -> {
            phoneBinding.phoneInputLayout.setError(null);
        });
        phoneBinding.continueButton.setOnClickListener(view -> continueFromPhoneSignup());
    }

    private void sendPhoneVerification(boolean resend) {
        if (phoneNumber.isEmpty() || otpBinding == null) {
            return;
        }
        otpBinding.continueToProfileButton.setEnabled(false);
        otpBinding.resendCodeButton.setEnabled(false);
        otpBinding.providerTestNumberNote.setText(R.string.otp_sending);
        PhoneAuthOptions.Builder options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(
                            @NonNull PhoneAuthCredential credential) {
                        signInWithPhoneCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException exception) {
                        if (otpBinding != null && screen == Screen.OTP) {
                            otpBinding.continueToProfileButton.setEnabled(true);
                            otpBinding.resendCodeButton.setEnabled(true);
                            otpBinding.providerTestNumberNote.setText(
                                    phoneAuthErrorMessage(exception));
                        }
                    }

                    @Override
                    public void onCodeSent(
                            @NonNull String id,
                            @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id;
                        resendToken = token;
                        if (otpBinding != null && screen == Screen.OTP) {
                            otpBinding.continueToProfileButton.setEnabled(true);
                            otpBinding.resendCodeButton.setEnabled(true);
                            otpBinding.providerTestNumberNote.setText(
                                    R.string.otp_code_sent);
                            otpBinding.otpInput.requestFocus();
                        }
                    }

                    @Override
                    public void onCodeAutoRetrievalTimeOut(@NonNull String id) {
                        verificationId = id;
                        if (otpBinding != null && screen == Screen.OTP) {
                            otpBinding.resendCodeButton.setEnabled(true);
                            otpBinding.providerTestNumberNote.setText(
                                    R.string.otp_expired);
                        }
                    }
                });
        if (resend && resendToken != null) {
            options.setForceResendingToken(resendToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(options.build());
    }

    private String phoneAuthErrorMessage(FirebaseException exception) {
        String message = exception.getLocalizedMessage();
        if (message != null
                && message.toLowerCase(Locale.ROOT).contains(
                        "region enabled by the app developer")) {
            return getString(R.string.phone_auth_region_blocked);
        }
        if (exception instanceof FirebaseAuthException
                && "ERROR_OPERATION_NOT_ALLOWED".equals(
                        ((FirebaseAuthException) exception).getErrorCode())) {
            return getString(R.string.phone_auth_provider_disabled);
        }
        return getString(R.string.phone_auth_error, message);
    }

    private void verifyPhoneCode() {
        if (verificationId == null) {
            if (otpBinding != null) {
                Snackbar.make(otpBinding.getRoot(),
                        R.string.otp_wait_for_sms, Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        String code = otpBinding.otpInput.getText() == null
                ? "" : otpBinding.otpInput.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            otpBinding.otpInputLayout.setError(getString(R.string.otp_invalid));
            return;
        }
        otpBinding.otpInputLayout.setError(null);
        otpBinding.continueToProfileButton.setEnabled(false);
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code));
    }

    private void signInWithPhoneCredential(PhoneAuthCredential credential) {
        firebaseAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                loadAuthenticatedProfile(Screen.CHATS);
                return;
            }
            if (otpBinding != null && screen == Screen.OTP) {
                otpBinding.continueToProfileButton.setEnabled(true);
                String detail = task.getException() == null
                        ? getString(R.string.otp_invalid)
                        : task.getException().getLocalizedMessage();
                otpBinding.otpInputLayout.setError(detail);
            }
        });
    }

    private void loadAuthenticatedProfile(Screen destination) {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        FcmTokenManager.registerCurrentToken();
        conversationExecutor.execute(() -> {
            try {
                org.json.JSONObject profile = supabaseApi.getProfile();
                runOnUiThread(() -> {
                    profileDisplayName = profile.optString("display_name", "");
                    profileUsername = profile.optString("username", "");
                    profileBio = profile.optString("bio", "");
                    profileSetupComplete = !profileDisplayName.trim().isEmpty();
                    getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE).edit()
                            .putString(CACHED_DISPLAY_NAME, profileDisplayName)
                            .putString(CACHED_USERNAME, profileUsername)
                            .putString(CACHED_BIO, profileBio)
                            .apply();
                    phoneNumber = firebaseAuth.getCurrentUser() == null
                            ? "" : firebaseAuth.getCurrentUser().getPhoneNumber();
                    if (phoneRegion.isEmpty() && phoneNumber != null) {
                        try {
                            int countryCode = PhoneNumberUtil.getInstance()
                                    .parse(phoneNumber, null).getCountryCode();
                            phoneRegion = PhoneNumberUtil.getInstance()
                                    .getRegionCodeForCountryCode(countryCode);
                        } catch (NumberParseException exception) {
                            phoneRegion = Locale.getDefault().getCountry();
                        }
                    }
                    if (profileDisplayName.trim().isEmpty()) {
                        profileOpenedFromMain = false;
                        showProfileSetup();
                    } else if (destination == Screen.SETTINGS) {
                        showSettingsScreen();
                    } else if (destination == Screen.CALLS) {
                        showCallsScreen();
                    } else {
                        showMainScreen(Screen.CHATS);
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    showPhoneSignup();
                    Snackbar.make(phoneBinding.getRoot(),
                            getString(R.string.backend_request_error,
                                    exception.getLocalizedMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void continueFromPhoneSignup() {
        phoneBinding.phoneInputLayout.setError(null);
        String country = phoneBinding.countryCodeInput.getText() == null
                ? "" : phoneBinding.countryCodeInput.getText().toString();
        String dialCode = extractDialCode(country);
        String localNumber = phoneBinding.phoneNumberInput.getText() == null
                ? "" : phoneBinding.phoneNumberInput.getText().toString();
        int countryCode;
        try {
            countryCode = Integer.parseInt(digitsOnly(dialCode));
            phoneRegion = PhoneNumberUtil.getInstance()
                    .getRegionCodeForCountryCode(countryCode);
            if (phoneRegion == null || phoneRegion.isEmpty() || "ZZ".equals(phoneRegion)) {
                phoneRegion = Locale.getDefault().getCountry();
            }
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    PhoneNumberUtil.getInstance().parse(localNumber, phoneRegion);
            if (!PhoneNumberUtil.getInstance().isValidNumber(parsed)
                    || parsed.getCountryCode() != countryCode) {
                throw new NumberParseException(
                        NumberParseException.ErrorType.NOT_A_NUMBER, "Invalid phone number");
            }
            phoneNumber = PhoneNumberUtil.getInstance().format(
                    parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException | NumberFormatException exception) {
            phoneBinding.phoneInputLayout.setError(getString(R.string.phone_number_invalid));
            return;
        }

        getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE).edit()
                .putString(PHONE_REGION, phoneRegion)
                .apply();
        showOtpScreen();
    }

    private String extractDialCode(String countryLabel) {
        int openingParenthesis = countryLabel.lastIndexOf('(');
        int closingParenthesis = countryLabel.lastIndexOf(')');
        if (openingParenthesis < 0 || closingParenthesis <= openingParenthesis) {
            return "";
        }
        return countryLabel.substring(openingParenthesis + 1, closingParenthesis);
    }

    private String digitsOnly(String value) {
        return value.replaceAll("[^0-9]", "");
    }

    private void showOtpScreen() {
        screen = Screen.OTP;
        otpBinding = ScreenOtpBinding.inflate(getLayoutInflater());
        setContentView(otpBinding.getRoot());
        configureSystemBars(otpBinding.getRoot());
        otpBinding.phoneNumberLabel.setText(phoneNumber);
        otpBinding.backButton.setOnClickListener(view -> showPhoneSignup());
        otpBinding.continueToProfileButton.setOnClickListener(view -> verifyPhoneCode());
        otpBinding.resendCodeButton.setOnClickListener(view -> sendPhoneVerification(true));
        sendPhoneVerification(false);
    }

    private void showProfileSetup() {
        screen = Screen.PROFILE;
        profileBinding = ScreenProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(profileBinding.getRoot());
        configureSystemBars(profileBinding.getRoot());

        if (profileOpenedFromMain) {
            profileBinding.profileTitle.setText(R.string.profile_edit_title);
            profileBinding.saveProfileButton.setText(R.string.save_profile_changes);
        }
        profileBinding.phoneVerificationStatus.setText(getString(
                R.string.phone_verification_verified,
                firebaseAuth.getCurrentUser() == null
                        ? getString(R.string.phone_not_provided)
                        : firebaseAuth.getCurrentUser().getPhoneNumber()));
        profileBinding.displayNameInput.setText(profileDisplayName);
        profileBinding.usernameInput.setText(profileUsername);
        profileBinding.bioInput.setText(profileBio);
        if (!profilePhotoUri.isEmpty()) {
            profileBinding.profilePhoto.setImageURI(Uri.parse(profilePhotoUri));
        }
        profileBinding.profileBackButton.setOnClickListener(view -> {
            if (profileOpenedFromMain) {
                profileOpenedFromMain = false;
                showMainScreen(Screen.CHATS);
            } else {
                showOtpScreen();
            }
        });
        profileBinding.chooseProfilePhoto.setOnClickListener(view -> {
            try {
                profilePhotoLauncher.launch("image/*");
            } catch (RuntimeException exception) {
                Snackbar.make(profileBinding.getRoot(),
                        R.string.profile_photo_picker_error, Snackbar.LENGTH_SHORT).show();
            }
        });
        profileBinding.saveProfileButton.setOnClickListener(view -> saveProfile());
    }

    private void saveProfile() {
        profileBinding.displayNameLayout.setError(null);
        profileBinding.usernameLayout.setError(null);

        String displayName = profileBinding.displayNameInput.getText() == null
                ? "" : profileBinding.displayNameInput.getText().toString().trim();
        String username = profileBinding.usernameInput.getText() == null
                ? "" : profileBinding.usernameInput.getText().toString().trim();
        if (displayName.isEmpty()) {
            profileBinding.displayNameLayout.setError(getString(R.string.display_name_required));
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            profileBinding.usernameLayout.setError(getString(R.string.username_invalid));
            return;
        }

        String bio = profileBinding.bioInput.getText() == null
                ? "" : profileBinding.bioInput.getText().toString().trim();
        profileBinding.saveProfileButton.setEnabled(false);
        conversationExecutor.execute(() -> {
            try {
                supabaseApi.updateProfile(new org.json.JSONObject()
                        .put("display_name", displayName)
                        .put("username", username)
                        .put("bio", bio));
                runOnUiThread(() -> {
                    profileDisplayName = displayName;
                    profileUsername = username;
                    profileBio = bio;
                    profileSetupComplete = true;
                    getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE).edit()
                            .putString(CACHED_DISPLAY_NAME, displayName)
                            .putString(CACHED_USERNAME, username)
                            .putString(CACHED_BIO, bio)
                            .apply();
                    profileBinding.saveProfileButton.setEnabled(true);
                    if (profileOpenedFromMain) {
                        profileOpenedFromMain = false;
                        showMainScreen(Screen.CHATS);
                    } else {
                        showContacts();
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    profileBinding.saveProfileButton.setEnabled(true);
                    Snackbar.make(profileBinding.getRoot(),
                            getString(R.string.backend_request_error,
                                    exception.getLocalizedMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showContacts() {
        screen = Screen.CONTACTS;
        contactsBinding = ScreenContactsSyncBinding.inflate(getLayoutInflater());
        setContentView(contactsBinding.getRoot());
        configureSystemBars(contactsBinding.getRoot());

        contactAdapter = new DeviceContactAdapter(this::openConversation);
        contactsBinding.contactsList.setLayoutManager(new LinearLayoutManager(this));
        contactsBinding.contactsList.setAdapter(contactAdapter);
        contactsBinding.syncContactsButton.setOnClickListener(view -> requestContactAccess());
        if (profileSetupComplete) {
            contactsBinding.openChatsButton.setVisibility(View.GONE);
            contactsBinding.contactsBottomNavigation.setVisibility(View.VISIBLE);
            ViewCompat.requestApplyInsets(contactsBinding.getRoot());
            contactsBinding.contactsBottomNavigation.setSelectedItemId(
                    R.id.navigation_contacts);
            contactsBinding.contactsBottomNavigation.setOnItemSelectedListener(item -> {
                showMainDestination(item.getItemId());
                return true;
            });
        } else {
            contactsBinding.openChatsButton.setOnClickListener(
                    view -> completeAccountSetup());
        }
        contactsBinding.contactSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (contactAdapter != null) {
                    contactAdapter.filter(text.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            contactsPermissionGranted = true;
            loadDeviceContacts();
        } else {
            contactsPermissionGranted = false;
            contactsBinding.contactsCount.setText(R.string.contacts_not_loaded);
            requestContactPermission();
        }
    }

    private void requestContactAccess() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            contactsPermissionGranted = true;
            if (screen == Screen.CALLS) {
                loadCallContacts();
            } else {
                loadDeviceContacts();
            }
            return;
        }
        contactsPermissionGranted = false;
        requestContactPermission();
    }

    private void requestContactPermission() {
        contactsPermissionRequestInFlight = true;
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
    }

    private void refreshContactsAfterPermissionChange(boolean hasContactsPermission) {
        deviceContactsLoaded = false;
        deviceContacts.clear();
        if (screen == Screen.CONTACTS && contactsBinding != null) {
            contactAdapter.submitList(deviceContacts);
            contactsBinding.syncContactsButton.setEnabled(true);
            if (hasContactsPermission) {
                loadDeviceContacts();
            } else {
                contactsBinding.syncContactsButton.setText(R.string.sync_contacts);
                contactsBinding.contactsCount.setText(R.string.contacts_not_loaded);
            }
        } else if (screen == Screen.CALLS && callsBinding != null) {
            RecyclerView.Adapter<?> adapter = callsBinding.contactsList.getAdapter();
            if (adapter instanceof DeviceContactAdapter) {
                ((DeviceContactAdapter) adapter).submitList(deviceContacts);
            }
            if (hasContactsPermission) {
                loadCallContacts();
            } else {
                callsBinding.contactsCount.setText(R.string.contacts_not_loaded);
                callsBinding.grantContactsButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadDeviceContacts() {
        if (contactsBinding == null || screen != Screen.CONTACTS) {
            return;
        }
        contactsBinding.syncContactsButton.setEnabled(false);
        contactsBinding.syncContactsButton.setText(R.string.contacts_loading);
        contactsBinding.contactsCount.setText(R.string.contacts_loading);
        contactsExecutor.execute(() -> {
            List<DeviceContact> loadedContacts;
            try {
                loadedContacts = queryDeviceContacts();
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    if (screen == Screen.CONTACTS && contactsBinding != null) {
                        contactsBinding.syncContactsButton.setEnabled(true);
                        contactsBinding.syncContactsButton.setText(R.string.refresh_contacts);
                        contactsBinding.contactsCount.setText(R.string.contacts_read_error);
                    }
                });
                return;
            }
            Map<String, String> matchedUsers = new HashMap<>();
            String syncError = null;
            try {
                org.json.JSONArray phoneNumbers = new org.json.JSONArray();
                for (DeviceContact contact : loadedContacts) {
                    phoneNumbers.put(contact.e164PhoneNumber);
                }
                org.json.JSONArray matches = supabaseApi.matchContacts(phoneNumbers);
                for (int index = 0; index < matches.length(); index++) {
                    org.json.JSONObject match = matches.getJSONObject(index);
                    matchedUsers.put(
                            match.optString("phone_number"),
                            match.optString("user_id"));
                }
            } catch (Exception exception) {
                syncError = exception.getLocalizedMessage();
            }
            Map<String, String> usersByNumber = matchedUsers;
            String error = syncError;
            runOnUiThread(() -> showLoadedContacts(loadedContacts, usersByNumber, error));
        });
    }

    private List<DeviceContact> queryDeviceContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(getString(R.string.phone_permissions_required));
        }
        List<DeviceContact> loadedContacts = new ArrayList<>();
        Set<String> uniqueNumbers = new HashSet<>();
        String region = Locale.getDefault().getCountry();
        if (region.isEmpty()) {
            region = phoneRegion;
        }
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        };
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (cursor == null) {
                throw new IllegalStateException(getString(R.string.contacts_read_error));
            }
            int nameColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER);
            int photoColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                String number = cursor.getString(numberColumn);
                String photoUri = cursor.getString(photoColumn);
                if (name == null || name.trim().isEmpty()
                        || number == null || number.trim().isEmpty()) {
                    continue;
                }
                try {
                    String normalizedNumber = phoneNumberUtil.format(
                            phoneNumberUtil.parse(number, region.isEmpty() ? null : region),
                            PhoneNumberUtil.PhoneNumberFormat.E164);
                    if (phoneNumberUtil.isValidNumber(
                            phoneNumberUtil.parse(
                                    normalizedNumber, region.isEmpty() ? null : region))
                            && uniqueNumbers.add(normalizedNumber)) {
                        loadedContacts.add(new DeviceContact(
                                name.trim(), number.trim(), photoUri, normalizedNumber));
                    }
                } catch (NumberParseException ignored) {
                    // Skip numbers that cannot be normalized; the server only accepts E.164.
                }
            }
        }
        return loadedContacts;
    }

    @MainThread
    private void showLoadedContacts(
            List<DeviceContact> loadedContacts,
            Map<String, String> matchedUsers,
            String syncError) {
        if (screen != Screen.CONTACTS || contactsBinding == null) {
            return;
        }
        List<DeviceContact> registeredContacts = new ArrayList<>();
        for (DeviceContact contact : loadedContacts) {
            contact.registeredUserId = matchedUsers.get(contact.e164PhoneNumber);
            if (contact.registeredUserId != null && !contact.registeredUserId.isEmpty()) {
                registeredContacts.add(contact);
            }
        }
        deviceContacts.clear();
        deviceContacts.addAll(registeredContacts);
        deviceContactsLoaded = syncError == null;
        contactsBinding.syncContactsButton.setEnabled(true);
        contactsBinding.syncContactsButton.setText(R.string.refresh_contacts);
        contactAdapter.submitList(deviceContacts);
        if (syncError != null) {
            contactsBinding.contactsCount.setText(
                    getString(R.string.contact_sync_error, syncError));
        } else if (loadedContacts.isEmpty()) {
            contactsBinding.contactsCount.setText(R.string.contacts_none);
        } else if (registeredContacts.isEmpty()) {
            contactsBinding.contactsCount.setText(R.string.contacts_no_matches);
        } else {
            contactsBinding.contactsCount.setText(
                    getString(R.string.contacts_matched_count, registeredContacts.size()));
        }
    }

    private void completeAccountSetup() {
        showMainScreen(Screen.CHATS);
    }

    private void showMainScreen(Screen destination) {
        screen = destination;
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        configureSystemBars(mainBinding.getRoot());

        mainBinding.bottomNavigation.setSelectedItemId(menuItemFor(destination));
        mainBinding.bottomNavigation.setOnItemSelectedListener(item -> {
            showMainDestination(item.getItemId());
            return true;
        });
        mainBinding.addContactButton.setOnClickListener(view -> {
            previousMainScreen = Screen.CHATS;
            showContacts();
        });
        mainBinding.profileButton.setOnClickListener(view -> {
            profileOpenedFromMain = true;
            showProfileSetup();
        });
        if (!profileDisplayName.isEmpty()) {
            mainBinding.profileInitial.setText(
                    profileDisplayName.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        }
        mainBinding.emptyFindPeople.setOnClickListener(view -> {
            previousMainScreen = Screen.CHATS;
            showContacts();
        });

        if (destination == Screen.CHATS) {
            mainBinding.screenTitle.setText(R.string.chats_title);
            mainBinding.searchContainer.setVisibility(View.VISIBLE);
            mainBinding.chatListingContent.setVisibility(View.VISIBLE);
            mainBinding.conversationFilters.setVisibility(View.VISIBLE);
            mainBinding.recentLabel.setVisibility(View.VISIBLE);
            mainBinding.emptyFindPeople.setOnClickListener(view -> {
                previousMainScreen = Screen.CHATS;
                showContacts();
            });
            mainBinding.searchInput.setText(conversationSearch);
            mainBinding.chatList.setVisibility(View.VISIBLE);
            mainBinding.emptyState.setVisibility(View.GONE);
            mainBinding.conversationsLoading.setVisibility(View.GONE);

            conversationLayoutManager = new LinearLayoutManager(this);
            mainBinding.chatList.setLayoutManager(conversationLayoutManager);
            conversationAdapter = new ConversationAdapter(new ConversationAdapter.Listener() {
                @Override
                public void onConversationSelected(ChatConversation conversation) {
                    openConversation(conversation);
                }

                @Override
                public void onConversationLongPressed(
                        ChatConversation conversation, View anchor) {
                    showConversationMenu(conversation, anchor);
                }
            });
            mainBinding.chatList.setAdapter(conversationAdapter);
            mainBinding.filterRecent.setChecked(conversationFilter == 0);
            mainBinding.filterUnread.setChecked(conversationFilter == 1);
            mainBinding.filterGroups.setChecked(conversationFilter == 2);
            mainBinding.conversationFilters.setOnCheckedStateChangeListener(
                    (group, checkedIds) -> {
                        int selectedId = group.getCheckedChipId();
                        conversationFilter = selectedId == R.id.filter_unread ? 1
                                : selectedId == R.id.filter_groups ? 2 : 0;
                        loadConversationPage(true);
                    });
            mainBinding.searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(
                        CharSequence text, int start, int before, int count) {
                    conversationSearch = text.toString().trim();
                    if (pendingConversationSearch != null) {
                        mainHandler.removeCallbacks(pendingConversationSearch);
                    }
                    pendingConversationSearch = () -> loadConversationPage(true);
                    mainHandler.postDelayed(pendingConversationSearch, 250);
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            mainBinding.chatList.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView,
                        int dx, int dy) {
                    if (dy > 0 && conversationLayoutManager != null
                            && conversationAdapter != null
                            && conversationLayoutManager.findLastVisibleItemPosition()
                                    >= conversationAdapter.getItemCount() - 4) {
                        loadConversationPage(false);
                    }
                }
            });
            loadConversationPage(true);
        } else {
            mainBinding.chatListingContent.setVisibility(View.VISIBLE);
            mainBinding.conversationFilters.setVisibility(View.GONE);
            mainBinding.recentLabel.setVisibility(View.GONE);
        }
    }

    private void showMainDestination(int itemId) {
        if (itemId == R.id.navigation_chats) {
            showMainScreen(Screen.CHATS);
        } else if (itemId == R.id.navigation_contacts) {
            previousMainScreen = Screen.CONTACTS;
            showContacts();
        } else if (itemId == R.id.navigation_calls) {
            showCallsScreen();
        } else if (itemId == R.id.navigation_settings) {
            showSettingsScreen();
        }
    }

    private void loadConversationPage(boolean reset) {
        if (screen != Screen.CHATS || mainBinding == null) {
            return;
        }
        if (reset) {
            conversationRequestId++;
            conversationOffset = 0;
            hasMoreConversations = true;
            conversationPageLoading = false;
            conversationAdapter.replace(new ArrayList<>());
            mainBinding.chatList.setVisibility(View.VISIBLE);
            mainBinding.emptyState.setVisibility(View.GONE);
            mainBinding.conversationsLoading.setVisibility(View.VISIBLE);
        } else if (conversationPageLoading || !hasMoreConversations) {
            return;
        }

        conversationPageLoading = true;
        int requestId = conversationRequestId;
        int pageOffset = conversationOffset;
        String search = conversationSearch;
        int filter = conversationFilter;
        conversationExecutor.execute(() -> {
            List<ChatConversation> page = new ArrayList<>();
            Exception loadError = null;
            try {
                org.json.JSONArray response = supabaseApi.getConversations(
                        search, filter, CONVERSATION_PAGE_SIZE, pageOffset);
                ConversationDao dao = chatDatabase.conversationDao();
                for (int index = 0; index < response.length(); index++) {
                    org.json.JSONObject row = response.getJSONObject(index);
                    String id = row.getString("id");
                    ChatConversation cached = dao.getById(id);
                    String displayName = row.optString("display_name", "");
                    String peerId = row.isNull("peer_id") ? null : row.optString("peer_id");
                    String avatar = row.isNull("avatar_url")
                            ? null : row.optString("avatar_url");
                    for (DeviceContact contact : deviceContacts) {
                        if (peerId != null && peerId.equals(contact.registeredUserId)) {
                            displayName = contact.name;
                            if (contact.photoUri != null) {
                                avatar = contact.photoUri;
                            }
                            break;
                        }
                    }
                    String preview = row.optString(
                            "last_message_preview", getString(R.string.no_messages_yet));
                    if (preview.isEmpty()) {
                        preview = getString(R.string.no_messages_yet);
                    }
                    long lastMessageAt = 0;
                    String timestamp = row.optString("last_message_at", "");
                    if (!timestamp.isEmpty()) {
                        lastMessageAt = java.time.Instant.parse(timestamp).toEpochMilli();
                    }
                    ChatConversation conversation = new ChatConversation(
                            id,
                            peerId,
                            displayName,
                            preview,
                            lastMessageAt,
                            row.optInt("unread_count", 0),
                            row.optBoolean("is_group"),
                            row.optBoolean("is_muted"),
                            row.optBoolean("is_pinned"),
                            avatar);
                    if (cached != null && cached.peerKey != null
                            && cached.peerKey.equals(peerId)) {
                        conversation.avatarUri = cached.avatarUri == null
                                ? avatar : cached.avatarUri;
                    }
                    dao.save(conversation);
                    page.add(conversation);
                }
            } catch (Exception exception) {
                loadError = exception;
            }
            List<ChatConversation> loadedPage = page;
            Exception error = loadError;
            runOnUiThread(() -> {
                if (screen != Screen.CHATS || mainBinding == null
                        || requestId != conversationRequestId) {
                    return;
                }
                conversationPageLoading = false;
                mainBinding.conversationsLoading.setVisibility(View.GONE);
                if (error != null) {
                    mainBinding.chatList.setVisibility(View.GONE);
                    mainBinding.emptyState.setVisibility(View.VISIBLE);
                    mainBinding.emptyTitle.setText(R.string.conversation_load_error);
                    mainBinding.emptyMessage.setText("");
                    mainBinding.emptyFindPeople.setVisibility(View.GONE);
                    Snackbar.make(mainBinding.getRoot(),
                            getString(R.string.backend_request_error,
                                    error.getLocalizedMessage()),
                            Snackbar.LENGTH_LONG).show();
                    return;
                }

                if (pageOffset == 0) {
                    conversationAdapter.replace(loadedPage);
                    if (loadedPage.isEmpty()) {
                        mainBinding.chatList.setVisibility(View.GONE);
                        mainBinding.emptyState.setVisibility(View.VISIBLE);
                        if (!search.isEmpty()) {
                            mainBinding.emptyTitle.setText(R.string.no_matching_conversations);
                            mainBinding.emptyMessage.setText("");
                        } else if (filter == 1) {
                            mainBinding.emptyTitle.setText(R.string.no_unread_chats);
                            mainBinding.emptyMessage.setText("");
                        } else if (filter == 2) {
                            mainBinding.emptyTitle.setText(R.string.no_group_chats);
                            mainBinding.emptyMessage.setText("");
                        } else {
                            mainBinding.emptyTitle.setText(R.string.no_chats_yet);
                            mainBinding.emptyMessage.setText(R.string.chats_empty_message);
                        }
                        mainBinding.emptyFindPeople.setVisibility(
                                filter == 0 && search.isEmpty() ? View.VISIBLE : View.GONE);
                    } else {
                        mainBinding.chatList.setVisibility(View.VISIBLE);
                        mainBinding.emptyState.setVisibility(View.GONE);
                    }
                } else {
                    conversationAdapter.append(loadedPage);
                }
                conversationOffset = pageOffset + loadedPage.size();
                hasMoreConversations = loadedPage.size() == CONVERSATION_PAGE_SIZE;
            });
        });
    }

    private void showConversationMenu(ChatConversation conversation, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, conversation.isPinned
                ? R.string.unpin_conversation : R.string.pin_conversation);
        menu.getMenu().add(0, 2, 1, conversation.isMuted
                ? R.string.unmute_conversation_action : R.string.mute_conversation_action);
        menu.setOnMenuItemClickListener(item -> {
            boolean pinned = conversation.isPinned;
            boolean muted = conversation.isMuted;
            if (item.getItemId() == 1) {
                pinned = !pinned;
            } else if (item.getItemId() == 2) {
                muted = !muted;
            } else {
                return false;
            }
            boolean updatedPinned = pinned;
            boolean updatedMuted = muted;
            conversationExecutor.execute(() -> {
                try {
                    supabaseApi.updateConversation(
                            conversation.id, updatedPinned, updatedMuted);
                    chatDatabase.conversationDao().updateFlags(
                            conversation.id, updatedPinned, updatedMuted);
                    runOnUiThread(() -> loadConversationPage(true));
                } catch (Exception exception) {
                    runOnUiThread(() -> {
                        if (mainBinding != null) {
                            Snackbar.make(mainBinding.getRoot(),
                                    getString(R.string.backend_request_error,
                                            exception.getLocalizedMessage()),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });
                }
            });
            return true;
        });
        menu.show();
    }

    private int menuItemFor(Screen destination) {
        if (destination == Screen.CONTACTS) {
            return R.id.navigation_contacts;
        }
        if (destination == Screen.CALLS) {
            return R.id.navigation_calls;
        }
        if (destination == Screen.SETTINGS) {
            return R.id.navigation_settings;
        }
        return R.id.navigation_chats;
    }

    private void showMainPlaceholder(Screen destination, int messageResource) {
        showMainScreen(destination);
        mainBinding.screenTitle.setText(destination == Screen.CALLS
                ? R.string.nav_calls : R.string.nav_settings);
        mainBinding.addContactButton.setVisibility(View.GONE);
        mainBinding.searchContainer.setVisibility(View.GONE);
        mainBinding.chatList.setVisibility(View.GONE);
        mainBinding.conversationsLoading.setVisibility(View.GONE);
        mainBinding.emptyState.setVisibility(View.VISIBLE);
        mainBinding.emptyTitle.setText(destination == Screen.CALLS
                ? R.string.nav_calls : R.string.nav_settings);
        mainBinding.emptyMessage.setText(messageResource);
        mainBinding.emptyFindPeople.setVisibility(View.GONE);
    }

    private void showSettingsScreen() {
        showMainScreen(Screen.SETTINGS);
        mainBinding.screenTitle.setText(R.string.nav_settings);
        mainBinding.addContactButton.setVisibility(View.GONE);
        mainBinding.searchContainer.setVisibility(View.GONE);
        mainBinding.chatList.setVisibility(View.GONE);
        mainBinding.chatListingContent.setVisibility(View.GONE);
        mainBinding.emptyState.setVisibility(View.GONE);

        settingsBinding = ScreenSettingsBinding.inflate(
                getLayoutInflater(), mainBinding.mainContent, false);
        mainBinding.mainContent.addView(settingsBinding.getRoot());
        boolean isDarkTheme = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        settingsBinding.darkThemeSwitch.setChecked(isDarkTheme);
        settingsBinding.darkThemeSwitch.setOnCheckedChangeListener((button, checked) -> {
            int selectedMode = checked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE).edit()
                    .putInt(THEME_MODE, selectedMode)
                    .apply();
            AppCompatDelegate.setDefaultNightMode(selectedMode);
        });
    }

    private void openConversation(DeviceContact contact) {
        Screen sourceScreen = screen;
        if (contact.registeredUserId == null || contact.registeredUserId.isEmpty()) {
            Snackbar.make(contactsBinding.getRoot(),
                    R.string.contact_not_registered, Snackbar.LENGTH_SHORT).show();
            return;
        }
        conversationExecutor.execute(() -> {
            try {
                ConversationDao dao = chatDatabase.conversationDao();
                String conversationId = supabaseApi.openDirectConversation(
                        contact.registeredUserId);
                ChatConversation conversation = new ChatConversation(
                        conversationId,
                        contact.registeredUserId,
                        contact.name,
                        getString(R.string.no_messages_yet),
                        0,
                        0,
                        false,
                        false,
                        false,
                        contact.photoUri);
                org.json.JSONArray messages = supabaseApi.getMessages(conversationId);
                dao.save(conversation);
                dao.markRead(conversationId);
                conversation.unreadCount = 0;
                ChatConversation savedConversation = conversation;
                runOnUiThread(() -> {
                    previousMainScreen = sourceScreen == Screen.CHATS
                            ? Screen.CHATS : Screen.CONTACTS;
                    displayConversation(savedConversation, messages);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (contactsBinding != null) {
                        Snackbar.make(contactsBinding.getRoot(),
                                getString(R.string.backend_request_error,
                                        exception.getLocalizedMessage()),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void openConversation(ChatConversation conversation) {
        previousMainScreen = Screen.CHATS;
        conversationExecutor.execute(() -> {
            try {
                org.json.JSONArray messages = supabaseApi.getMessages(conversation.id);
                chatDatabase.conversationDao().markRead(conversation.id);
                conversation.unreadCount = 0;
                runOnUiThread(() -> displayConversation(conversation, messages));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (mainBinding != null) {
                        Snackbar.make(mainBinding.getRoot(),
                                getString(R.string.backend_request_error,
                                        exception.getLocalizedMessage()),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void displayConversation(
            ChatConversation conversation, org.json.JSONArray messages) {
        selectedConversation = conversation;
        screen = Screen.CONVERSATION;
        conversationBinding = ScreenConversationBinding.inflate(getLayoutInflater());
        setContentView(conversationBinding.getRoot());
        configureSystemBars(conversationBinding.getRoot());
        conversationBinding.conversationName.setText(conversation.displayName);
        conversationBinding.conversationSubtitle.setText(conversation.isGroup
                ? R.string.group_conversation : R.string.local_contact_label);
        conversationBinding.conversationBack.setOnClickListener(
                view -> returnFromConversation());
        conversationBinding.conversationCallButton.setVisibility(
                conversation.isGroup ? View.GONE : View.VISIBLE);
        conversationBinding.conversationCallButton.setOnClickListener(
                view -> requestOutgoingCall(conversation, "audio"));
        conversationBinding.conversationVideoCallButton.setVisibility(
                conversation.isGroup ? View.GONE : View.VISIBLE);
        conversationBinding.conversationVideoCallButton.setOnClickListener(
                view -> requestOutgoingCall(conversation, "video"));
        conversationBinding.sendButton.setOnClickListener(view -> sendMessage());
        TextView emptyState = conversationBinding.conversationEmpty;
        conversationBinding.messageList.removeAllViews();
        conversationBinding.messageList.addView(emptyState);
        for (int index = 0; index < messages.length(); index++) {
            renderMessage(messages.optJSONObject(index));
        }
        conversationBinding.conversationEmpty.setVisibility(
                messages.length() == 0 ? View.VISIBLE : View.GONE);
    }

    private void returnFromConversation() {
        if (previousMainScreen == Screen.CONTACTS) {
            showContacts();
        } else {
            showMainScreen(Screen.CHATS);
        }
    }

    private void requestOutgoingCall(ChatConversation conversation, String mediaType) {
        View root = findViewById(android.R.id.content);
        if (conversation.isGroup || conversation.peerKey == null
                || conversation.peerKey.isEmpty()) {
            Snackbar.make(root,
                    R.string.voice_call_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        if ("video".equals(mediaType)
                && !getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_CAMERA_ANY)) {
            Snackbar.make(root,
                    R.string.video_call_camera_missing, Snackbar.LENGTH_LONG).show();
            return;
        }
        boolean needsMicrophone = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
        boolean needsCamera = "video".equals(mediaType)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED;
        if (needsMicrophone || needsCamera) {
            pendingOutgoingMediaType = mediaType;
            requestCallPermissions(mediaType);
            return;
        }
        startOutgoingCall(conversation, mediaType);
    }

    private void requestCallPermissions(String mediaType) {
        if ("video".equals(mediaType)) {
            callPermissionLauncher.launch(new String[] {
                    Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA
            });
        } else {
            callPermissionLauncher.launch(new String[] { Manifest.permission.RECORD_AUDIO });
        }
    }

    private void startOutgoingCall(ChatConversation conversation, String mediaType) {
        callExecutor.execute(() -> {
            try {
                JSONObject response = supabaseApi.call(
                        "create",
                        new JSONObject().put("conversation_id", conversation.id)
                                .put("media_type", mediaType));
                JSONObject call = response.getJSONObject("call");
                runOnUiThread(() -> startVoiceCallUi(
                        call.optString("id"), true, conversation.displayName, mediaType));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    Snackbar.make(findViewById(android.R.id.content),
                            getString(R.string.voice_call_failed,
                                    exception.getLocalizedMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showIncomingCallIfPresent(JSONObject response) {
        if (response == null || activeCallId != null || isFinishing()) {
            return;
        }
        JSONArray calls = response.optJSONArray("calls");
        if (calls == null || calls.length() == 0) {
            promptedIncomingCallId = null;
            clearIncomingCallIntent();
            return;
        }
        JSONObject call = null;
        for (int index = 0; index < calls.length(); index++) {
            JSONObject candidate = calls.optJSONObject(index);
            if (candidate != null
                    && (notificationCallId == null
                    || notificationCallId.equals(candidate.optString("id")))) {
                call = candidate;
                break;
            }
        }
        if (call == null) {
            clearIncomingCallIntent();
            return;
        }
        JSONObject incomingCall = call;
        String callId = incomingCall.optString("id");
        if (callId.isEmpty() || callId.equals(promptedIncomingCallId)) {
            return;
        }
        promptedIncomingCallId = callId;
        boolean videoCall = "video".equals(incomingCall.optString("media_type"));
        if (ChatlyFirebaseMessagingService.ACTION_ACCEPT_CALL.equals(notificationCallAction)) {
            notificationCallAction = null;
            requestIncomingCallAcceptance(incomingCall, videoCall);
            return;
        }
        if (ChatlyFirebaseMessagingService.ACTION_REJECT_CALL.equals(notificationCallAction)) {
            notificationCallAction = null;
            updateCallStatus(callId, "reject");
            clearIncomingCallIntent();
            return;
        }
        String callerName = notificationCallId != null
                && notificationCallId.equals(callId)
                && notificationCallerName != null
                ? notificationCallerName : "Chatly contact";
        showIncomingCallScreen(incomingCall, callerName);
    }

    private void requestIncomingCallAcceptance(JSONObject call, boolean videoCall) {
        boolean needsMicrophone = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
        boolean needsCamera = videoCall && ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
        if (needsMicrophone || needsCamera) {
            pendingIncomingCall = call;
            requestCallPermissions(videoCall ? "video" : "audio");
        } else {
            acceptIncomingCall(call);
        }
    }

    private void acceptIncomingCall(JSONObject call) {
        String callId = call.optString("id");
        String callerName = notificationCallerName == null || notificationCallerName.isEmpty()
                ? voiceCallBinding == null
                        ? "Chatly contact" : voiceCallBinding.voiceCallPeer.getText().toString()
                : notificationCallerName;
        callExecutor.execute(() -> {
            try {
                supabaseApi.call("accept", new JSONObject().put("call_id", callId));
                runOnUiThread(() -> {
                    NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(this);
                    notificationManager.cancel(callId, 0);
                    notificationManager.cancel(callId.hashCode());
                    incomingCallAwaitingAnswer = false;
                    startVoiceCallUi(
                            callId, false, callerName, call.optString("media_type", "audio"));
                    clearIncomingCallIntent();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    promptedIncomingCallId = null;
                    Snackbar.make(
                            findViewById(android.R.id.content),
                            getString(R.string.voice_call_failed,
                                    exception.getLocalizedMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showIncomingCallScreen(JSONObject call, String callerName) {
        String callId = call.optString("id");
        if (callId.isEmpty()) {
            return;
        }
        activeCallId = callId;
        lastCallSignalId = 0;
        callEnded = false;
        incomingCallAwaitingAnswer = true;
        microphoneMuted = false;
        promptedIncomingCallId = null;
        mainHandler.removeCallbacks(incomingCallPoll);
        screen = Screen.VOICE_CALL;
        voiceCallBinding = ScreenVoiceCallBinding.inflate(getLayoutInflater());
        setContentView(voiceCallBinding.getRoot());
        configureSystemBars(voiceCallBinding.getRoot());
        boolean videoCall = "video".equals(call.optString("media_type"));
        voiceCallBinding.voiceCallTitle.setText(
                videoCall ? R.string.video_call_title : R.string.voice_call_title);
        voiceCallBinding.voiceCallPeer.setText(callerName);
        voiceCallBinding.videoContainer.setVisibility(videoCall ? View.VISIBLE : View.GONE);
        voiceCallBinding.voiceCallVideo.setVisibility(View.GONE);
        voiceCallBinding.voiceCallSwitchCamera.setVisibility(View.GONE);
        voiceCallBinding.voiceCallStatus.setText(R.string.voice_call_incoming);
        voiceCallBinding.voiceCallAnswer.setVisibility(View.VISIBLE);
        voiceCallBinding.voiceCallAnswer.setOnClickListener(view ->
                requestIncomingCallAcceptance(call, videoCall));
        voiceCallBinding.voiceCallMute.setVisibility(View.GONE);
        voiceCallBinding.voiceCallSpeaker.setVisibility(View.GONE);
        voiceCallBinding.voiceCallEnd.setText(R.string.reject_call);
        voiceCallBinding.voiceCallEnd.setOnClickListener(view -> declineIncomingCall());
        mainHandler.post(activeCallPoll);
    }

    private void declineIncomingCall() {
        String callId = activeCallId;
        incomingCallAwaitingAnswer = false;
        if (callId != null) {
            updateCallStatus(callId, "reject");
        }
        finishVoiceCall();
    }

    private void clearIncomingCallIntent() {
        notificationCallId = null;
        notificationCallerName = null;
        notificationMediaType = null;
        notificationCallAction = null;
    }

    private void updateCallStatus(String callId, String action) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(callId, 0);
        notificationManager.cancel(callId.hashCode());
        callExecutor.execute(() -> {
            try {
                supabaseApi.call(action, new JSONObject().put("call_id", callId));
            } catch (Exception exception) {
                android.util.Log.w("ChatlyCalls", "Could not " + action + " call", exception);
            }
        });
    }

    private void startVoiceCallUi(
            String callId, boolean caller, String peerName, String mediaType) {
        if (isFinishing()) {
            updateCallStatus(callId, "end");
            return;
        }
        boolean videoCall = "video".equals(mediaType);
        activeCallId = callId;
        lastCallSignalId = 0;
        callEnded = false;
        incomingCallAwaitingAnswer = false;
        microphoneMuted = false;
        speakerphoneEnabled = false;
        promptedIncomingCallId = null;
        mainHandler.removeCallbacks(incomingCallPoll);
        screen = Screen.VOICE_CALL;
        voiceCallBinding = ScreenVoiceCallBinding.inflate(getLayoutInflater());
        setContentView(voiceCallBinding.getRoot());
        configureSystemBars(voiceCallBinding.getRoot());
        voiceCallBinding.voiceCallTitle.setText(
                videoCall ? R.string.video_call_title : R.string.voice_call_title);
        voiceCallBinding.voiceCallPeer.setText(peerName);
        voiceCallBinding.videoContainer.setVisibility(videoCall ? View.VISIBLE : View.GONE);
        voiceCallBinding.voiceCallVideo.setVisibility(videoCall ? View.VISIBLE : View.GONE);
        voiceCallBinding.voiceCallSwitchCamera.setVisibility(
                videoCall ? View.VISIBLE : View.GONE);
        voiceCallBinding.voiceCallAnswer.setVisibility(View.GONE);
        voiceCallBinding.voiceCallMute.setVisibility(View.VISIBLE);
        voiceCallBinding.voiceCallSpeaker.setVisibility(View.VISIBLE);
        voiceCallBinding.voiceCallSpeaker.setText(R.string.speaker_off);
        voiceCallBinding.voiceCallStatus.setText(
                caller ? R.string.voice_call_ringing : R.string.voice_call_connecting);
        voiceCallBinding.voiceCallVideo.setOnClickListener(view -> {
            if (voiceCallSession != null) {
                boolean enabled = voiceCallSession.toggleVideo();
                voiceCallBinding.voiceCallVideo.setText(
                        enabled ? R.string.disable_video : R.string.enable_video);
            }
        });
        voiceCallBinding.voiceCallSwitchCamera.setOnClickListener(view -> {
            if (voiceCallSession != null) {
                voiceCallSession.switchCamera();
            }
        });
        voiceCallBinding.voiceCallMute.setOnClickListener(view -> {
            if (voiceCallSession != null) {
                microphoneMuted = voiceCallSession.toggleMute();
                voiceCallBinding.voiceCallMute.setText(microphoneMuted
                        ? R.string.unmute_microphone : R.string.mute_microphone);
            }
        });
        voiceCallBinding.voiceCallSpeaker.setOnClickListener(view -> {
            if (audioManager != null) {
                speakerphoneEnabled = !speakerphoneEnabled;
                audioManager.setSpeakerphoneOn(speakerphoneEnabled);
                voiceCallBinding.voiceCallSpeaker.setText(speakerphoneEnabled
                        ? R.string.speaker_on : R.string.speaker_off);
            }
        });
        voiceCallBinding.voiceCallEnd.setOnClickListener(view -> {
            if (incomingCallAwaitingAnswer) {
                declineIncomingCall();
            } else {
                endVoiceCall();
            }
        });
        configureCallAudio();
        try {
            ChatlyCallForegroundService.start(this, videoCall, peerName);
            callExecutor.execute(() -> {
                JSONArray iceServers = null;
                try {
                    JSONObject response = supabaseApi.call("ice-servers", null);
                    iceServers = response.optJSONArray("ice_servers");
                    if (iceServers == null) {
                        android.util.Log.w("ChatlyCalls",
                                "ICE server response was missing; using public STUN only");
                    }
                } catch (Exception exception) {
                    android.util.Log.w("ChatlyCalls",
                            "Could not load ICE servers; using public STUN only", exception);
                }
                JSONArray resolvedIceServers = iceServers;
                runOnUiThread(() -> {
                    if (callEnded || !callId.equals(activeCallId) || voiceCallBinding == null) {
                        return;
                    }
                    try {
                        voiceCallSession = new VoiceCallSession(
                                this,
                                new VoiceCallSession.Listener() {
                                    @Override
                                    public void onLocalSignal(JSONObject signal) {
                                        callExecutor.execute(() -> {
                                            try {
                                                supabaseApi.call(
                                                        "signal",
                                                        new JSONObject().put("call_id", callId)
                                                                .put("signal", signal));
                                            } catch (Exception exception) {
                                                android.util.Log.e("ChatlyCalls",
                                                        "Could not send call signaling data",
                                                        exception);
                                                runOnUiThread(() -> showCallFailure(
                                                        exception.getLocalizedMessage()));
                                            }
                                        });
                                    }

                                    @Override
                                    public void onConnectionState(String state) {
                                        runOnUiThread(() -> {
                                            if (voiceCallBinding == null || callEnded) {
                                                return;
                                            }
                                            if ("CONNECTED".equals(state)) {
                                                voiceCallBinding.voiceCallStatus.setText(
                                                        R.string.voice_call_connected);
                                            } else if ("FAILED".equals(state)) {
                                                showCallFailure(
                                                        "The peer-to-peer connection failed.");
                                            }
                                        });
                                    }

                                    @Override
                                    public void onFailure(String message) {
                                        runOnUiThread(() -> showCallFailure(message));
                                    }
                                },
                                videoCall,
                                voiceCallBinding.localVideo,
                                voiceCallBinding.remoteVideo,
                                resolvedIceServers);
                        if (caller) {
                            voiceCallSession.startOutgoingCall();
                        }
                        mainHandler.post(activeCallPoll);
                    } catch (RuntimeException exception) {
                        showCallFailure(exception.getLocalizedMessage());
                    }
                });
            });
        } catch (RuntimeException exception) {
            showCallFailure(exception.getLocalizedMessage());
        }
    }

    private void configureCallAudio() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        previousAudioMode = audioManager.getMode();
        previousSpeakerphoneState = audioManager.isSpeakerphoneOn();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(speakerphoneEnabled);
    }

    private void endVoiceCall() {
        String callId = activeCallId;
        if (callId == null) {
            finishVoiceCall();
            return;
        }
        if (endingCallRequestInFlight) {
            return;
        }
        endingCallRequestInFlight = true;
        if (voiceCallBinding != null) {
            voiceCallBinding.voiceCallEnd.setEnabled(false);
        }
        callExecutor.execute(() -> {
            try {
                JSONObject response = supabaseApi.call(
                        "end", new JSONObject().put("call_id", callId));
                runOnUiThread(() -> {
                    if (!callId.equals(activeCallId)) {
                        return;
                    }
                    endingCallRequestInFlight = false;
                    JSONObject call = response.optJSONObject("call");
                    String status = call == null ? "" : call.optString("status");
                    if (!"ended".equals(status) && !"rejected".equals(status)
                            && !"expired".equals(status)) {
                        voiceCallBinding.voiceCallEnd.setEnabled(true);
                        Snackbar.make(
                                voiceCallBinding.getRoot(),
                                R.string.voice_call_ended,
                                Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    JSONObject delivery = response.optJSONObject("notification_delivery");
                    if (delivery != null && delivery.optInt("failed") > 0) {
                        android.util.Log.e("ChatlyCalls",
                                "Call ended, but one or more cancellation notifications failed");
                    } else if (delivery != null && delivery.optInt("attempted") == 0) {
                        android.util.Log.w("ChatlyCalls",
                                "Call ended, but no cancellation push token is registered");
                    }
                    finishVoiceCall();
                });
            } catch (Exception exception) {
                android.util.Log.e("ChatlyCalls",
                        "Could not confirm that the call ended on the server", exception);
                runOnUiThread(() -> {
                    if (!callId.equals(activeCallId)) {
                        return;
                    }
                    endingCallRequestInFlight = false;
                    if (voiceCallBinding != null) {
                        voiceCallBinding.voiceCallEnd.setEnabled(true);
                        Snackbar.make(
                                voiceCallBinding.getRoot(),
                                getString(R.string.voice_call_failed,
                                        exception.getLocalizedMessage()),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showCallFailure(String message) {
        if (callEnded) {
            return;
        }
        Snackbar.make(findViewById(android.R.id.content),
                getString(R.string.voice_call_failed,
                        message == null || message.isEmpty() ? "unknown error" : message),
                Snackbar.LENGTH_LONG).show();
        endVoiceCall();
    }

    private void finishVoiceCall() {
        if (callEnded) {
            return;
        }
        String callId = activeCallId;
        callEnded = true;
        activeCallId = null;
        endingCallRequestInFlight = false;
        incomingCallAwaitingAnswer = false;
        mainHandler.removeCallbacks(activeCallPoll);
        closeVoiceCallSession();
        ChatlyCallForegroundService.stop(this);
        if (callId != null) {
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(this);
            notificationManager.cancel(callId, 0);
            notificationManager.cancel(callId.hashCode());
        }
        if (firebaseAuth.getCurrentUser() != null) {
            mainHandler.postDelayed(incomingCallPoll, 2_000);
        }
        returnFromVoiceCall();
    }

    private void closeVoiceCallSession() {
        if (voiceCallSession != null) {
            voiceCallSession.close();
            voiceCallSession = null;
        }
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(previousSpeakerphoneState);
            audioManager.setMode(previousAudioMode);
            audioManager = null;
        }
    }

    private void returnFromVoiceCall() {
        voiceCallBinding = null;
        activeCallId = null;
        if (selectedConversation != null) {
            openConversation(selectedConversation);
        } else {
            showMainScreen(Screen.CHATS);
        }
    }

    private void sendMessage() {
        Editable editable = conversationBinding.messageInput.getText();
        if (editable == null || editable.toString().trim().isEmpty()) {
            return;
        }
        String body = editable.toString().trim();
        String conversationId = selectedConversation.id;
        conversationBinding.sendButton.setEnabled(false);
        conversationExecutor.execute(() -> {
            try {
                org.json.JSONObject message = supabaseApi.sendMessage(
                        conversationId, body);
                long timestamp = java.time.Instant.parse(
                        message.getString("created_at")).toEpochMilli();
                chatDatabase.conversationDao().updateLastMessage(
                        conversationId,
                        body,
                        timestamp);
                runOnUiThread(() -> {
                    if (screen != Screen.CONVERSATION || conversationBinding == null
                            || selectedConversation == null
                            || !conversationId.equals(selectedConversation.id)) {
                        return;
                    }
                    renderMessage(message);
                    conversationBinding.conversationEmpty.setVisibility(View.GONE);
                    conversationBinding.messageInput.setText("");
                    conversationBinding.sendButton.setEnabled(true);
                    selectedConversation.lastMessagePreview = body;
                    selectedConversation.lastMessageAt = timestamp;
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (conversationBinding != null) {
                        conversationBinding.sendButton.setEnabled(true);
                        Snackbar.make(conversationBinding.getRoot(),
                                getString(R.string.backend_request_error,
                                        exception.getLocalizedMessage()),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
        conversationBinding.messageInput.clearFocus();
    }

    private void renderMessage(org.json.JSONObject messageData) {
        if (messageData == null || conversationBinding == null) {
            return;
        }
        TextView messageView = new TextView(this);
        String currentUserId = firebaseAuth.getCurrentUser() == null
                ? "" : firebaseAuth.getCurrentUser().getUid();
        boolean outgoing = currentUserId.equals(messageData.optString("sender_id"));
        messageView.setText(messageData.optString("body"));
        messageView.setTextColor(getColor(outgoing
                ? R.color.chatly_on_primary : R.color.chatly_text_primary));
        messageView.setTextSize(15);
        messageView.setMaxWidth(getResources().getDisplayMetrics().widthPixels * 3 / 4);
        messageView.setPadding(dp(14), dp(10), dp(14), dp(10));
        conversationBinding.messageList.setGravity(Gravity.TOP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(getColor(outgoing
                ? R.color.chatly_primary : R.color.chatly_surface));
        background.setCornerRadius(dp(18));
        messageView.setBackground(background);

        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = outgoing ? Gravity.END : Gravity.START;
        params.topMargin = dp(8);
        conversationBinding.messageList.addView(messageView, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(incomingCallPoll);
        mainHandler.removeCallbacks(activeCallPoll);
        closeVoiceCallSession();
        ChatlyCallForegroundService.stop(this);
        if (activeCallId != null && !callEnded) {
            String callId = activeCallId;
            callExecutor.execute(() -> {
                try {
                    supabaseApi.call("end", new JSONObject().put("call_id", callId));
                } catch (Exception exception) {
                    android.util.Log.w("ChatlyCalls",
                            "Could not end call on activity close", exception);
                }
            });
        }
        if (pendingConversationSearch != null) {
            mainHandler.removeCallbacks(pendingConversationSearch);
        }
        contactsExecutor.shutdownNow();
        conversationExecutor.shutdownNow();
        callExecutor.shutdown();
        super.onDestroy();
    }
}
