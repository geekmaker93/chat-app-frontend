package com.gracie.mobile;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "conversations", indices = {
        @Index(value = {"peerKey"}, unique = true),
        @Index(value = {"isPinned", "lastMessageAt"})
})
public class ChatConversation {
    @PrimaryKey
    @NonNull
    public String id;
    public String peerKey;
    public String displayName;
    public String lastMessagePreview;
    public long lastMessageAt;
    public int unreadCount;
    public boolean isGroup;
    public boolean isMuted;
    public boolean isPinned;
    public String avatarUri;

    public ChatConversation(String id, String peerKey, String displayName,
            String lastMessagePreview, long lastMessageAt, int unreadCount,
            boolean isGroup, boolean isMuted, boolean isPinned, String avatarUri) {
        this.id = id;
        this.peerKey = peerKey;
        this.displayName = displayName;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
        this.isGroup = isGroup;
        this.isMuted = isMuted;
        this.isPinned = isPinned;
        this.avatarUri = avatarUri;
    }
}
