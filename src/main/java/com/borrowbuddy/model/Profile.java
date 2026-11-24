package com.borrowbuddy.model;

import com.borrowbuddy.security.EncryptedStringConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

// COMPOSITION - a User HAS-A Profile (course, year level, block, contact info)
// ENCAPSULATION - fields are private, you can only change them thru the setters that check the input
@Embeddable
public class Profile {
    private String course;
    private int yearLevel;
    private String block;
    @Convert(converter = EncryptedStringConverter.class)
    private String contactInfo;

    protected Profile() {}

    @SuppressWarnings("OverridableMethodCallInConstructor") // setters validate input; they stay non-final for Hibernate proxies

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
        String c = required(course, "Course").replaceAll("\\s+", " ");
        // e.g. BSIT, BS Civil Engineering, AB-Psych
        if (c.length() < 2 || c.length() > 40 || !c.matches("^[A-Za-z][A-Za-z .&-]*[A-Za-z]$")) {
            throw new IllegalArgumentException("Course should be letters only, e.g. BSIT or BSCS.");
        }
        this.course = c.toUpperCase();
    }

    public void setYearLevel(int yearLevel) {
        if (yearLevel < 1 || yearLevel > 5) {
            throw new IllegalArgumentException("Year level must be 1 to 5.");
        }
        this.yearLevel = yearLevel;
    }

    public void setBlock(String block) {
        String b = required(block, "Block");
        // e.g. 2G, 3A, A, LAB
        if (b.length() > 6 || !b.matches("^[A-Za-z0-9-]+$")) {
            throw new IllegalArgumentException("Block should be short letters/numbers only, e.g. 2G.");
        }
        this.block = b.toUpperCase();
    }

    // either a PH mobile number (09XX XXX XXXX / +63 9XX XXX XXXX) or a Messenger name / link
    public void setContactInfo(String contactInfo) {
        String c = required(contactInfo, "Contact info").replaceAll("\\s+", " ");
        if (c.length() > 100) {
            throw new IllegalArgumentException("Contact info is too long (max 100 characters).");
        }
        String compact = c.replaceAll("[\\s()-]", "");
        if (compact.matches("^\\+?\\d+$")) {
            if (!compact.matches("^(09\\d{9}|\\+639\\d{9})$")) {
                throw new IllegalArgumentException("Phone number must be a PH mobile number, e.g. 0917 123 4567.");
            }
        } else if (c.replaceAll("[^\\p{L}]", "").length() < 3) {
            throw new IllegalArgumentException("Contact info must be a PH mobile number or your Messenger name.");
        }
        this.contactInfo = c;
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
