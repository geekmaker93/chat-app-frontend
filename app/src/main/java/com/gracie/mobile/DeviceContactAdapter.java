package com.gracie.mobile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DeviceContactAdapter
        extends RecyclerView.Adapter<DeviceContactAdapter.ContactViewHolder> {
    interface OnContactClickListener {
        void onContactClick(DeviceContact contact);
    }

    private final List<DeviceContact> contacts = new ArrayList<>();
    private final List<DeviceContact> visibleContacts = new ArrayList<>();
    private final OnContactClickListener clickListener;
    @StringRes
    private final int actionLabel;
    private String query = "";

    DeviceContactAdapter(OnContactClickListener clickListener) {
        this(clickListener, R.string.contact_on_chatly);
    }

    DeviceContactAdapter(OnContactClickListener clickListener, @StringRes int actionLabel) {
        this.clickListener = clickListener;
        this.actionLabel = actionLabel;
    }

    void submitList(List<DeviceContact> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
        filter(query);
    }

    void filter(String newQuery) {
        query = newQuery == null ? "" : newQuery.trim().toLowerCase(Locale.ROOT);
        visibleContacts.clear();
        for (DeviceContact contact : contacts) {
            if (query.isEmpty()
                    || contact.name.toLowerCase(Locale.ROOT).contains(query)
                    || contact.phoneNumber.toLowerCase(Locale.ROOT).contains(query)) {
                visibleContacts.add(contact);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        DeviceContact contact = visibleContacts.get(position);
        holder.bind(contact, clickListener, actionLabel);
    }

    @Override
    public int getItemCount() {
        return visibleContacts.size();
    }

    static final class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView initials;
        private final TextView name;
        private final TextView number;
        private final TextView status;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            initials = itemView.findViewById(R.id.contact_initials);
            name = itemView.findViewById(R.id.contact_name);
            number = itemView.findViewById(R.id.contact_number);
            status = itemView.findViewById(R.id.contact_status);
        }

        void bind(
                DeviceContact contact,
                OnContactClickListener clickListener,
                @StringRes int actionLabel) {
            initials.setText(contact.initials());
            name.setText(contact.name);
            number.setText(contact.phoneNumber);
            status.setText(actionLabel);
            itemView.setOnClickListener(view -> clickListener.onContactClick(contact));
        }
    }
}
