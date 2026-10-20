package com.gracie.mobile;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class CallHistoryAdapter
        extends RecyclerView.Adapter<CallHistoryAdapter.CallViewHolder> {
    private final List<CallHistoryEntry> calls = new ArrayList<>();
    private final Listener listener;
    private int expandedPosition = RecyclerView.NO_POSITION;

    interface Listener {
        void onCallRequested(CallHistoryEntry call);
        void onMessageRequested(CallHistoryEntry call);
    }

    CallHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    void submitList(List<CallHistoryEntry> entries) {
        calls.clear();
        calls.addAll(entries);
        expandedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_history, parent, false);
        return new CallViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        holder.bind(calls.get(position), position == expandedPosition);
        holder.itemView.setOnClickListener(view -> toggleActions(position));
        holder.menuButton.setOnClickListener(view -> toggleActions(position));
    }

    @Override
    public int getItemCount() {
        return calls.size();
    }

    private void toggleActions(int position) {
        int previousPosition = expandedPosition;
        expandedPosition = expandedPosition == position ? RecyclerView.NO_POSITION : position;
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition);
        }
        if (expandedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(expandedPosition);
        }
    }

    static final class CallViewHolder extends RecyclerView.ViewHolder {
        private final TextView initials;
        private final TextView name;
        private final TextView details;
        private final TextView timestamp;
        private final ImageButton menuButton;
        private final LinearLayout actions;
        private final View callAction;
        private final View messageAction;
        private final Listener listener;

        CallViewHolder(@NonNull View itemView, Listener listener) {
            super(itemView);
            this.listener = listener;
            initials = itemView.findViewById(R.id.call_history_initials);
            name = itemView.findViewById(R.id.call_history_name);
            details = itemView.findViewById(R.id.call_history_details);
            timestamp = itemView.findViewById(R.id.call_history_timestamp);
            menuButton = itemView.findViewById(R.id.call_history_menu);
            actions = itemView.findViewById(R.id.call_history_actions);
            callAction = itemView.findViewById(R.id.call_history_call_action);
            messageAction = itemView.findViewById(R.id.call_history_message_action);
        }

        void bind(CallHistoryEntry call, boolean expanded) {
            actions.setVisibility(expanded ? View.VISIBLE : View.GONE);
            menuButton.setContentDescription(itemView.getContext().getString(expanded
                    ? R.string.call_history_hide_actions
                    : R.string.call_history_show_actions));
            callAction.setOnClickListener(view -> listener.onCallRequested(call));
            messageAction.setOnClickListener(view -> listener.onMessageRequested(call));
            name.setText(call.peerName);
            initials.setText(call.peerName.isEmpty()
                    ? "?" : call.peerName.substring(0, 1).toUpperCase());
            int detailResource;
            if (call.missed) {
                if (call.outgoing) {
                    detailResource = R.string.call_history_outgoing_no_answer;
                } else {
                    detailResource = call.video
                            ? R.string.call_history_missed_video
                            : R.string.call_history_missed_voice;
                }
            } else if (call.declined) {
                detailResource = call.outgoing
                        ? R.string.call_history_outgoing_declined
                        : R.string.call_history_incoming_declined;
            } else {
                detailResource = call.outgoing
                        ? (call.video ? R.string.call_history_outgoing_video
                                : R.string.call_history_outgoing_voice)
                        : (call.video ? R.string.call_history_incoming_video
                                : R.string.call_history_incoming_voice);
            }
            details.setText(detailResource);
            timestamp.setText(DateUtils.getRelativeTimeSpanString(
                    call.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE));
        }
    }
}
