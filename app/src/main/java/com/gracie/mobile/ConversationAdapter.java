package com.gracie.mobile;

import android.content.Context;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gracie.mobile.databinding.ItemConversationBinding;

import java.util.ArrayList;
import java.util.List;

final class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
    interface Listener {
        void onConversationSelected(ChatConversation conversation);

        void onConversationLongPressed(ChatConversation conversation, View anchor);
    }

    private final Listener listener;
    private final List<ChatConversation> conversations = new ArrayList<>();

    ConversationAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatConversation conversation = conversations.get(position);
        holder.bind(conversation);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    void replace(List<ChatConversation> page) {
        conversations.clear();
        conversations.addAll(page);
        notifyDataSetChanged();
    }

    void append(List<ChatConversation> page) {
        int start = conversations.size();
        conversations.addAll(page);
        notifyItemRangeInserted(start, page.size());
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;

        ViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatConversation conversation) {
            Context context = binding.getRoot().getContext();
            binding.conversationName.setText(conversation.displayName);
            binding.lastMessage.setText(conversation.lastMessagePreview);
            if (conversation.lastMessageAt > 0) {
                binding.timestamp.setVisibility(View.VISIBLE);
                binding.timestamp.setText(DateUtils.getRelativeTimeSpanString(
                        conversation.lastMessageAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE));
            } else {
                binding.timestamp.setVisibility(View.INVISIBLE);
            }

            String initials = initials(conversation.displayName);
            if (conversation.avatarUri != null && !conversation.avatarUri.isEmpty()) {
                binding.avatarImage.setVisibility(View.VISIBLE);
                binding.avatarInitials.setVisibility(View.GONE);
                binding.avatarImage.setImageURI(Uri.parse(conversation.avatarUri));
            } else {
                binding.avatarImage.setVisibility(View.GONE);
                binding.avatarInitials.setVisibility(View.VISIBLE);
                binding.avatarInitials.setText(conversation.isGroup ? "✦" : initials);
            }

            binding.groupIcon.setVisibility(conversation.isGroup ? View.VISIBLE : View.GONE);
            binding.pinnedIcon.setVisibility(conversation.isPinned ? View.VISIBLE : View.GONE);
            binding.mutedIcon.setVisibility(conversation.isMuted ? View.VISIBLE : View.GONE);
            if (conversation.unreadCount > 0) {
                binding.unreadBadge.setVisibility(View.VISIBLE);
                binding.unreadBadge.setText(conversation.unreadCount > 99
                        ? "99+" : String.valueOf(conversation.unreadCount));
                binding.lastMessage.setTextColor(
                        context.getColor(R.color.chatly_text_primary));
            } else {
                binding.unreadBadge.setVisibility(View.GONE);
                binding.lastMessage.setTextColor(
                        context.getColor(R.color.chatly_text_secondary));
            }

            binding.getRoot().setContentDescription(context.getString(
                    R.string.conversation_accessibility, conversation.displayName,
                    conversation.lastMessagePreview));
            binding.getRoot().setOnClickListener(
                    view -> listener.onConversationSelected(conversation));
            binding.getRoot().setOnLongClickListener(view -> {
                listener.onConversationLongPressed(conversation, view);
                return true;
            });
        }

        private String initials(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "?";
            }
            String[] words = name.trim().split("\\s+");
            if (words.length == 1) {
                return words[0].substring(0, 1).toUpperCase();
            }
            return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1))
                    .toUpperCase();
        }
    }
}
