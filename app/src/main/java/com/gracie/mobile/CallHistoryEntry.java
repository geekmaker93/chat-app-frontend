package com.gracie.mobile;

final class CallHistoryEntry {
    final String peerName;
    final String peerId;
    final boolean outgoing;
    final boolean missed;
    final boolean declined;
    final boolean video;
    final long timestamp;

    CallHistoryEntry(
            String peerName,
            String peerId,
            boolean outgoing,
            boolean missed,
            boolean declined,
            boolean video,
            long timestamp) {
        this.peerName = peerName;
        this.peerId = peerId;
        this.outgoing = outgoing;
        this.missed = missed;
        this.declined = declined;
        this.video = video;
        this.timestamp = timestamp;
    }
}
