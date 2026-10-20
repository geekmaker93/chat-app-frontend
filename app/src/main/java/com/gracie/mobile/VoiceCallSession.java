package com.gracie.mobile;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class VoiceCallSession {
    private static final String TAG = "ChatlyVideo";
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;

    interface Listener {
        void onLocalSignal(JSONObject signal);
        void onConnectionState(String state);
        void onFailure(String message);
    }

    private static boolean factoryInitialized;

    private final Listener listener;
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();
    private final boolean videoEnabled;
    private final JSONArray iceServerConfig;
    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule audioDeviceModule;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private VideoTrack remoteVideoTrack;
    private CameraVideoCapturer cameraCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private EglBase eglBase;
    private final SurfaceViewRenderer localRenderer;
    private final SurfaceViewRenderer remoteRenderer;
    private boolean remoteDescriptionSet;
    private boolean closed;
    private boolean cameraCapturing;

    VoiceCallSession(
            Context context,
            Listener listener,
            boolean videoEnabled,
            SurfaceViewRenderer localRenderer,
            SurfaceViewRenderer remoteRenderer,
            JSONArray iceServerConfig) {
        this.listener = listener;
        this.videoEnabled = videoEnabled;
        this.localRenderer = localRenderer;
        this.remoteRenderer = remoteRenderer;
        this.iceServerConfig = iceServerConfig;
        initializeFactory(context.getApplicationContext());
    }

    private synchronized void initializeFactory(Context context) {
        if (!factoryInitialized) {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .createInitializationOptions());
            factoryInitialized = true;
        }

        audioDeviceModule =
                JavaAudioDeviceModule.builder(context).createAudioDeviceModule();
        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(
                createIceServers(iceServerConfig));
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState state) { }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                listener.onConnectionState(state.name());
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) { }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                JSONObject signal = new JSONObject();
                try {
                    signal.put("type", "candidate");
                    signal.put("sdpMid", candidate.sdpMid);
                    signal.put("sdpMLineIndex", candidate.sdpMLineIndex);
                    signal.put("candidate", candidate.sdp);
                    listener.onLocalSignal(signal);
                } catch (JSONException exception) {
                    listener.onFailure("Could not encode an ICE candidate.");
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) { }

            @Override
            public void onAddStream(org.webrtc.MediaStream stream) { }

            @Override
            public void onRemoveStream(org.webrtc.MediaStream stream) { }

            @Override
            public void onDataChannel(org.webrtc.DataChannel channel) { }

            @Override
            public void onRenegotiationNeeded() { }

            @Override
            public void onAddTrack(
                    org.webrtc.RtpReceiver receiver, org.webrtc.MediaStream[] streams) {
                attachRemoteTrack(receiver.track());
            }

            @Override
            public void onTrack(org.webrtc.RtpTransceiver transceiver) {
                attachRemoteTrack(transceiver.getReceiver().track());
            }
        });
        if (peerConnection == null) {
            throw new IllegalStateException("Could not initialize the WebRTC peer connection.");
        }

        audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("chatly-audio", audioSource);
        audioTrack.setEnabled(true);
        peerConnection.addTrack(audioTrack);
        if (videoEnabled) {
            initializeVideo(context);
        }
    }

    private void attachRemoteTrack(org.webrtc.MediaStreamTrack remoteTrack) {
        if (remoteTrack instanceof AudioTrack) {
            ((AudioTrack) remoteTrack).setEnabled(true);
        } else if (videoEnabled && remoteTrack instanceof VideoTrack) {
            VideoTrack receivedVideoTrack = (VideoTrack) remoteTrack;
            receivedVideoTrack.setEnabled(true);
            if (remoteVideoTrack != receivedVideoTrack) {
                if (remoteVideoTrack != null) {
                    remoteVideoTrack.removeSink(remoteRenderer);
                }
                remoteVideoTrack = receivedVideoTrack;
                remoteVideoTrack.addSink(remoteRenderer);
            }
        }
    }

    private static List<PeerConnection.IceServer> createIceServers(JSONArray config) {
        if (config == null) {
            return Collections.singletonList(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                            .createIceServer());
        }
        List<PeerConnection.IceServer> servers = new ArrayList<>();
        try {
            for (int index = 0; index < config.length(); index++) {
                JSONObject server = config.getJSONObject(index);
                Object rawUrls = server.opt("urls");
                List<String> urls = new ArrayList<>();
                if (rawUrls instanceof String) {
                    urls.add((String) rawUrls);
                } else if (rawUrls instanceof JSONArray) {
                    JSONArray urlArray = (JSONArray) rawUrls;
                    for (int urlIndex = 0; urlIndex < urlArray.length(); urlIndex++) {
                        Object rawUrl = urlArray.get(urlIndex);
                        if (!(rawUrl instanceof String)) {
                            throw new IllegalArgumentException(
                                    "ICE server URLs must be strings.");
                        }
                        urls.add((String) rawUrl);
                    }
                } else {
                    throw new IllegalArgumentException("ICE server URLs are missing.");
                }
                if (urls.isEmpty()) {
                    throw new IllegalArgumentException("ICE server URLs are empty.");
                }

                PeerConnection.IceServer.Builder builder =
                        PeerConnection.IceServer.builder(urls);
                String username = server.optString("username", "");
                String credential = server.optString(
                        "credential", server.optString("password", ""));
                if (username.isEmpty() != credential.isEmpty()) {
                    throw new IllegalArgumentException(
                            "ICE server username and credential must both be provided.");
                }
                if (!username.isEmpty()) {
                    builder.setUsername(username).setPassword(credential);
                }
                servers.add(builder.createIceServer());
            }
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid ICE server configuration.", exception);
        }
        if (servers.isEmpty()) {
            throw new IllegalArgumentException("ICE server configuration is empty.");
        }
        return servers;
    }

    private void initializeVideo(Context context) {
        eglBase = EglBase.create();
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setMirror(true);
        localRenderer.setZOrderMediaOverlay(true);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setEnableHardwareScaler(true);

        CameraEnumerator enumerator;
        CameraVideoCapturer capturer = null;
        if (Camera2Enumerator.isSupported(context)) {
            enumerator = new Camera2Enumerator(context);
            capturer = findCameraCapturer(enumerator);
            if (capturer == null) {
                Log.w(TAG, "Camera2 could not create a capturer; trying Camera1");
            }
        } else {
            enumerator = null;
        }
        if (capturer == null) {
            enumerator = new Camera1Enumerator(true);
            capturer = findCameraCapturer(enumerator);
        }
        if (capturer == null) {
            throw new IllegalStateException("No camera is available for this call.");
        }
        cameraCapturer = capturer;
        surfaceTextureHelper = SurfaceTextureHelper.create(
                "ChatlyCameraCapture", eglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(false);
        cameraCapturer.initialize(
                surfaceTextureHelper, context, videoSource.getCapturerObserver());
        cameraCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
        cameraCapturing = true;
        videoTrack = factory.createVideoTrack("chatly-video", videoSource);
        videoTrack.setEnabled(true);
        videoTrack.addSink(localRenderer);
        peerConnection.addTrack(videoTrack);
    }

    private CameraVideoCapturer findCameraCapturer(CameraEnumerator enumerator) {
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) {
                    Log.i(TAG, "Using WebRTC camera capturer: "
                            + enumerator.getClass().getSimpleName() + " (" + name + ")");
                    return capturer;
                }
            }
        }
        for (String name : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) {
                    Log.i(TAG, "Using WebRTC camera capturer: "
                            + enumerator.getClass().getSimpleName() + " (" + name + ")");
                    return capturer;
                }
            }
        }
        return null;
    }

    void startOutgoingCall() {
        if (closed) {
            return;
        }
        peerConnection.createOffer(new EmptySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                peerConnection.setLocalDescription(new EmptySdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        emitDescription("offer", offer);
                    }

                    @Override
                    public void onSetFailure(String error) {
                        listener.onFailure("Could not set the local call offer: " + error);
                    }
                }, offer);
            }

            @Override
            public void onCreateFailure(String error) {
                listener.onFailure("Could not create the call offer: " + error);
            }
        }, new MediaConstraints());
    }

    void handleRemoteSignal(JSONObject signal) {
        if (closed || signal == null) {
            return;
        }
        String type = signal.optString("type");
        if ("candidate".equals(type)) {
            IceCandidate candidate = new IceCandidate(
                    signal.optString("sdpMid", ""),
                    signal.optInt("sdpMLineIndex"),
                    signal.optString("candidate", ""));
            if (remoteDescriptionSet) {
                peerConnection.addIceCandidate(candidate);
            } else {
                pendingCandidates.add(candidate);
            }
            return;
        }
        if (!"offer".equals(type) && !"answer".equals(type)) {
            listener.onFailure("Received an unsupported call signal.");
            return;
        }

        SessionDescription.Type descriptionType =
                SessionDescription.Type.fromCanonicalForm(type);
        SessionDescription description = new SessionDescription(
                descriptionType, signal.optString("sdp", ""));
        peerConnection.setRemoteDescription(new EmptySdpObserver() {
            @Override
            public void onSetSuccess() {
                remoteDescriptionSet = true;
                for (IceCandidate candidate : pendingCandidates) {
                    peerConnection.addIceCandidate(candidate);
                }
                pendingCandidates.clear();
                if ("offer".equals(type)) {
                    createAnswer();
                }
            }

            @Override
            public void onSetFailure(String error) {
                listener.onFailure("Could not apply the remote call description: " + error);
            }
        }, description);
    }

    private void createAnswer() {
        peerConnection.createAnswer(new EmptySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription answer) {
                peerConnection.setLocalDescription(new EmptySdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        emitDescription("answer", answer);
                    }

                    @Override
                    public void onSetFailure(String error) {
                        listener.onFailure("Could not set the local call answer: " + error);
                    }
                }, answer);
            }

            @Override
            public void onCreateFailure(String error) {
                listener.onFailure("Could not create the call answer: " + error);
            }
        }, new MediaConstraints());
    }

    private void emitDescription(String type, SessionDescription description) {
        JSONObject signal = new JSONObject();
        try {
            signal.put("type", type);
            signal.put("sdp", description.description);
            listener.onLocalSignal(signal);
        } catch (JSONException exception) {
            listener.onFailure("Could not encode the call description.");
        }
    }

    boolean toggleMute() {
        if (audioTrack == null || closed) {
            return false;
        }
        audioTrack.setEnabled(!audioTrack.enabled());
        return !audioTrack.enabled();
    }

    boolean toggleVideo() {
        if (videoTrack == null || closed) {
            return false;
        }
        if (videoTrack.enabled()) {
            videoTrack.setEnabled(false);
            try {
                cameraCapturer.stopCapture();
                cameraCapturing = false;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                listener.onFailure("Could not stop the camera.");
            }
            return false;
        }
        try {
            cameraCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
            cameraCapturing = true;
            videoTrack.setEnabled(true);
            return true;
        } catch (RuntimeException exception) {
            listener.onFailure("Could not start the camera: " + exception.getMessage());
            return false;
        }
    }

    void switchCamera() {
        if (cameraCapturer instanceof CameraVideoCapturer && !closed) {
            ((CameraVideoCapturer) cameraCapturer).switchCamera(
                    new CameraVideoCapturer.CameraSwitchHandler() {
                        @Override
                        public void onCameraSwitchDone(boolean isFrontCamera) {
                            localRenderer.setMirror(isFrontCamera);
                        }

                        @Override
                        public void onCameraSwitchError(String errorDescription) {
                            listener.onFailure("Could not switch camera: " + errorDescription);
                        }
                    });
        }
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        if (cameraCapturer != null && cameraCapturing) {
            try {
                cameraCapturer.stopCapture();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (cameraCapturer != null) {
            cameraCapturer.dispose();
            cameraCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeSink(remoteRenderer);
            remoteVideoTrack = null;
        }
        if (videoTrack != null) {
            videoTrack.removeSink(localRenderer);
            videoTrack.dispose();
            videoTrack = null;
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        if (audioDeviceModule != null) {
            audioDeviceModule.release();
            audioDeviceModule = null;
        }
        if (eglBase != null) {
            localRenderer.release();
            remoteRenderer.release();
            eglBase.release();
            eglBase = null;
        }
    }

    private abstract static class EmptySdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription description) { }

        @Override
        public void onSetSuccess() { }

        @Override
        public void onCreateFailure(String error) { }

        @Override
        public void onSetFailure(String error) { }
    }
}
