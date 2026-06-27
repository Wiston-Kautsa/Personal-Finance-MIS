package com.wk.pfmis.models;

public class Person {
    private final int id;
    private final String fullName;
    private final String phoneNumber;
    private final String relationship;
    private final String notes;

    public Person(int id, String fullName, String phoneNumber, String relationship, String notes) {
        this.id = id;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
