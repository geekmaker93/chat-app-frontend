package com.gracie.mobile;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ConversationDaoTest {
    private ChatDatabase database;
    private ConversationDao dao;

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.conversationDao();
        dao.save(new ChatConversation("1", "peer1", "Alex", "Hey", 100, 0,
                false, false, true, null));
        dao.save(new ChatConversation("2", "peer2", "Jordan", "Hello", 300, 2,
                false, true, false, null));
        dao.save(new ChatConversation("3", null, "Weekend group", "See you soon", 200, 1,
                true, false, false, null));
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void pagesArePinnedFirstThenMostRecent() {
        List<ChatConversation> firstPage = dao.getPage("", 0, 2, 0);
        List<ChatConversation> secondPage = dao.getPage("", 0, 2, 2);

        assertEquals(2, firstPage.size());
        assertEquals("1", firstPage.get(0).id);
        assertEquals("2", firstPage.get(1).id);
        assertEquals(1, secondPage.size());
        assertEquals("3", secondPage.get(0).id);
    }

    @Test
    public void unreadAndGroupFiltersOnlyReturnMatchingRows() {
        List<ChatConversation> unread = dao.getPage("", 1, 20, 0);
        List<ChatConversation> groups = dao.getPage("", 2, 20, 0);

        assertEquals(2, unread.size());
        assertEquals("2", unread.get(0).id);
        assertEquals("3", unread.get(1).id);
        assertEquals(1, groups.size());
        assertEquals("3", groups.get(0).id);
    }

    @Test
    public void searchFiltersConversationNamesAndPreviews() {
        List<ChatConversation> byName = dao.getPage("weekend", 0, 20, 0);
        List<ChatConversation> byPreview = dao.getPage("hey", 0, 20, 0);

        assertEquals(1, byName.size());
        assertEquals("3", byName.get(0).id);
        assertEquals(1, byPreview.size());
        assertEquals("1", byPreview.get(0).id);
    }

    @Test
    public void unreadAndPinnedStateCanBeUpdatedLocally() {
        dao.updateFlags("2", true, false);
        dao.markRead("2");

        List<ChatConversation> conversations = dao.getPage("", 0, 20, 0);
        ChatConversation updated = dao.getById("2");

        assertEquals("2", conversations.get(0).id);
        assertEquals(0, updated.unreadCount);
        assertEquals(true, updated.isPinned);
        assertEquals(false, updated.isMuted);
    }
}
