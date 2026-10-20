package com.gracie.mobile;

import android.content.Context;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ChatConversation.class}, version = 2, exportSchema = false)
public abstract class ChatDatabase extends RoomDatabase {
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE conversations ADD COLUMN peerKey TEXT");
            database.execSQL("UPDATE conversations SET peerKey = id");
            database.execSQL("UPDATE conversations SET id = "
                    + "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || "
                    + "lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || "
                    + "lower(hex(randomblob(6)))");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_conversations_peerKey ON conversations (peerKey)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "index_conversations_isPinned_lastMessageAt "
                    + "ON conversations (isPinned, lastMessageAt)");
        }
    };

    private static volatile ChatDatabase instance;

    public abstract ConversationDao conversationDao();

    static ChatDatabase getInstance(Context context) {
        ChatDatabase database = instance;
        if (database == null) {
            synchronized (ChatDatabase.class) {
                database = instance;
                if (database == null) {
                    database = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ChatDatabase.class,
                            "chatly-conversations.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                    instance = database;
                }
            }
        }
        return database;
    }
}
