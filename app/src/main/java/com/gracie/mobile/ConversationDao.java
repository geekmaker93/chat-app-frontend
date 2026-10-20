package com.gracie.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ConversationDao {
    @Query("SELECT * FROM conversations "
            + "WHERE (:search = '' OR displayName LIKE '%' || :search || '%' "
            + "OR lastMessagePreview LIKE '%' || :search || '%') "
            + "AND (:filter = 0 OR (:filter = 1 AND unreadCount > 0) "
            + "OR (:filter = 2 AND isGroup = 1)) "
            + "ORDER BY isPinned DESC, lastMessageAt DESC, "
            + "displayName COLLATE NOCASE, id COLLATE NOCASE "
            + "LIMIT :limit OFFSET :offset")
    List<ChatConversation> getPage(String search, int filter, int limit, int offset);

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    ChatConversation getById(String id);

    @Query("SELECT * FROM conversations WHERE peerKey = :peerKey LIMIT 1")
    ChatConversation getByPeerKey(String peerKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(ChatConversation conversation);

    @Query("UPDATE conversations SET lastMessagePreview = :preview, "
            + "lastMessageAt = :timestamp WHERE id = :id")
    void updateLastMessage(String id, String preview, long timestamp);

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    void markRead(String id);

    @Query("UPDATE conversations SET isPinned = :pinned, isMuted = :muted WHERE id = :id")
    void updateFlags(String id, boolean pinned, boolean muted);
}
