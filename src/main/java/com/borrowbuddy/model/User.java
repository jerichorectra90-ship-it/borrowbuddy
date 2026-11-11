package com.borrowbuddy.model;

import com.borrowbuddy.security.PasswordHasher;
import jakarta.persistence.*;

// INHERITANCE (parent of Student, OrgOfficer, LabStaff).
// ENCAPSULATION: private fields; the password has no getter and is only stored as a hash.
@Entity
@Table(name = "app_users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role")
public abstract class User {
    public static final String EMAIL_DOMAIN = "@ust-legazpi.edu.ph";

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
    private Profile profile; // COMPOSITION

    protected User() {}

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
        this.fullName = fullName.trim();
    }

    public void setUstlAccount(String ustlAccount) {
        String email = ustlAccount == null ? "" : ustlAccount.trim().toLowerCase();
        if (!email.endsWith(EMAIL_DOMAIN) || email.length() <= EMAIL_DOMAIN.length()) {
            throw new IllegalArgumentException("Use your UST-L email (ending in " + EMAIL_DOMAIN + ").");
        }
        this.ustlAccount = email;
    }

    public void setStudentId(String studentId) {
        this.studentId = (studentId == null || studentId.isBlank()) ? null : studentId.trim();
    }

    public void setPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        this.passwordHash = PasswordHasher.hash(password);
    }

    public boolean checkPassword(String password) {
        return PasswordHasher.matches(password, passwordHash);
    }

    public boolean isSameUser(User other) {
        return other != null && id != null && id.equals(other.getId());
    }

    // Lab staff override this to become the admin role.
    public boolean isAdmin() { return false; }

    // OVERRIDDEN by each child class
    public abstract int getBorrowLimit();

    public abstract String getRole();
}
