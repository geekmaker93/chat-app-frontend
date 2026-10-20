package com.gracie.mobile;

final class DeviceContact {
    final String name;
    final String phoneNumber;
    final String photoUri;
    final String e164PhoneNumber;
    String registeredUserId;

    DeviceContact(String name, String phoneNumber, String photoUri, String e164PhoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.photoUri = photoUri;
        this.e164PhoneNumber = e164PhoneNumber;
    }

    String initials() {
        String[] words = name.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return "?";
        }
        if (words.length == 1) {
            return words[0].substring(0, 1).toUpperCase();
        }
        return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1))
                .toUpperCase();
    }
}
