package com.borrowbuddy.model;

import com.borrowbuddy.security.PasswordHasher;
import jakarta.persistence.*;
import java.util.regex.Pattern;

// INHERITANCE - parent class of Student, OrgOfficer, LabStaff
// ENCAPSULATION - private fields. no getter for the password on purpose, we only keep the hash
@Entity
@Table(name = "app_users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role")
public abstract class User {
    public static final String EMAIL_DOMAIN = "@ust-legazpi.edu.ph";

    // part before the @: letters/numbers, can be split by single dots, dashes or underscores
    // e.g. juan.delacruz, jdelacruz2 -> ok   |   .juan, juan..cruz, "asd fgh", !!! -> rejected
    private static final Pattern EMAIL_LOCAL = Pattern.compile("^[a-z0-9]+([._-][a-z0-9]+)*$");
    // letters (incl. ñ and accents), spaces, dots, apostrophes, dashes. e.g. "Ma. Clara O'Neil-Santos"
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'-]*[\\p{L}.]$");
    // UST-L student number: 7 digits, e.g. 4250487
    private static final Pattern STUDENT_ID = Pattern.compile("^\\d{7}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String ustlAccount;

    @Column(unique = true)
    private String studentId;

    private String passwordHash;

    @Embedded
    private Profile profile; // COMPOSITION, User HAS-A Profile

    // lab staff sets this when someone doesn't return an item. nullable so the column can be
    // added to the existing table, null just means not suspended
    private Boolean suspended;

    protected User() {}

    @SuppressWarnings("OverridableMethodCallInConstructor") // setters validate input; they stay non-final for Hibernate proxies

    protected User(String fullName, String ustlAccount, String studentId, String password, Profile profile) {
        setFullName(fullName);
        setUstlAccount(ustlAccount);
        setStudentId(studentId);
        setPassword(password);
        if (profile == null) {
            throw new IllegalArgumentException("Profile is required.");
        }
        this.profile = profile;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getUstlAccount() { return ustlAccount; }
    public String getStudentId() { return studentId; }
    public Profile getProfile() { return profile; }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        String name = fullName.trim().replaceAll("\\s+", " ");
        if (name.length() > 80 || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Full name can only have letters, spaces, dots, apostrophes and dashes.");
        }
        if (name.split(" ").length < 2) {
            throw new IllegalArgumentException("Enter your full name (first and last name).");
        }
        this.fullName = name;
    }

    public void setUstlAccount(String ustlAccount) {
        String email = ustlAccount == null ? "" : ustlAccount.trim().toLowerCase();
        if (!email.endsWith(EMAIL_DOMAIN)) {
            throw new IllegalArgumentException("Use your UST-L email (ending in " + EMAIL_DOMAIN + ").");
        }
        String local = email.substring(0, email.length() - EMAIL_DOMAIN.length());
        if (local.length() < 2 || local.length() > 64 || !EMAIL_LOCAL.matcher(local).matches()) {
            throw new IllegalArgumentException("That is not a valid UST-L email. Use the format name.surname"
                    + EMAIL_DOMAIN + " (letters, numbers, dots, dashes or underscores only).");
        }
        this.ustlAccount = email;
    }

    public void setStudentId(String studentId) {
        String sid = (studentId == null || studentId.isBlank()) ? null : studentId.trim();
        // lab staff use staff IDs (e.g. STAFF-001), everyone else needs a real student number
        if (!isAdmin()) {
            if (sid == null) {
                throw new IllegalArgumentException("Student ID is required.");
            }
            if (!STUDENT_ID.matcher(sid).matches()) {
                throw new IllegalArgumentException("Student ID must be 7 digits, e.g. 4250487.");
            }
        }
        this.studentId = sid;
    }

    public void setPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        if (!password.matches(".*\\p{L}.*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must have at least one letter and one number.");
        }
        this.passwordHash = PasswordHasher.hash(password);
    }

    public boolean checkPassword(String password) {
        return PasswordHasher.matches(password, passwordHash);
    }

    public boolean isSameUser(User other) {
        return other != null && id != null && id.equals(other.getId());
    }

    public boolean isSuspended() { return Boolean.TRUE.equals(suspended); }

    // ENCAPSULATION - suspension only changes thru these 2 methods, and only lab staff can call them
    public void suspend(User byAdmin) {
        requireAdmin(byAdmin);
        if (isAdmin()) {
            throw new IllegalArgumentException("Lab staff accounts cannot be suspended.");
        }
        this.suspended = true;
    }

    public void reinstate(User byAdmin) {
        requireAdmin(byAdmin);
        this.suspended = false;
    }

    private static void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) {
            throw new IllegalArgumentException("Only lab staff can change a borrower's suspension.");
        }
    }

    // LabStaff overrides this, that's how it becomes the admin
    public boolean isAdmin() { return false; }

    // child classes OVERRIDE these
    public abstract int getBorrowLimit();

    public abstract String getRole();
}
