package com.borrowbuddy.model;

import jakarta.persistence.Embeddable;

// COMPOSITION: a User HAS-A Profile (course, year level, block, contact info).
// ENCAPSULATION: private fields that can only change through validated setters.
@Embeddable
public class Profile {
    private String course;
    private int yearLevel;
    private String block;
    private String contactInfo;

    protected Profile() {}

    public Profile(String course, int yearLevel, String block, String contactInfo) {
        setCourse(course);
        setYearLevel(yearLevel);
        setBlock(block);
        setContactInfo(contactInfo);
    }

    public String getCourse() { return course; }
    public int getYearLevel() { return yearLevel; }
    public String getBlock() { return block; }
    public String getContactInfo() { return contactInfo; }

    public void setCourse(String course) {
        this.course = required(course, "Course").toUpperCase();
    }

    public void setYearLevel(int yearLevel) {
        if (yearLevel < 1 || yearLevel > 5) {
            throw new IllegalArgumentException("Year level must be 1 to 5.");
        }
        this.yearLevel = yearLevel;
    }

    public void setBlock(String block) {
        this.block = required(block, "Block").toUpperCase();
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = required(contactInfo, "Contact info");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return course + " " + yearLevel + " · Block " + block;
    }
}
