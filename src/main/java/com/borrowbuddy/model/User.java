package com.borrowbuddy.model;

import com.borrowbuddy.security.PasswordHasher;
import jakarta.persistence.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
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

    // EMAIL VERIFICATION - a new account must enter the 6-digit code sent to its UST-L email.
    // nullable so the columns can be added to the existing table: null = an account made before
    // verification existed, those count as verified so they keep working
    private Boolean emailVerified;
    private String verificationCodeHash; // hashed like a password, the plain code is never saved
    private Instant verificationExpiresAt;
    private Instant verificationSentAt;
    private Integer verificationAttempts;

    // ACCOUNT LOCKOUT - stops password guessing. nullable for the existing table, null = 0 / not locked
    private Integer failedLoginAttempts;
    private Instant lockedUntil;

    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES = 15;

    public static final int CODE_MINUTES_VALID = 10;
    public static final int MAX_CODE_ATTEMPTS = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    // STRONG PASSWORD - checked here on the server, so it applies to the website AND the terminal
    // version and can't be skipped by editing the HTML form
    public static final String PASSWORD_RULE =
            "at least 8 characters, with a letter, a number and a special character (e.g. ! @ # $ %)";

    public void setPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        if (password.length() > 128) {
            throw new IllegalArgumentException("Password must be at most 128 characters.");
        }
        boolean hasLetter = password.matches(".*\\p{L}.*");
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[^\\p{L}\\d\\s].*"); // anything not a letter, digit or space
        if (!hasLetter || !hasNumber || !hasSpecial) {
            throw new IllegalArgumentException("Password must have " + PASSWORD_RULE + ".");
        }
        this.passwordHash = PasswordHasher.hash(password);
    }

    public boolean checkPassword(String password) {
        return PasswordHasher.matches(password, passwordHash);
    }

    // ===== account lockout =====

    public boolean isLocked() { return lockedUntil != null && Instant.now().isBefore(lockedUntil); }

    // whole minutes left on the lock, rounded up (0 = not locked)
    public long minutesUntilUnlocked() {
        if (!isLocked()) return 0;
        long seconds = Duration.between(Instant.now(), lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    // call after a wrong password. returns true if this attempt locked the account
    public boolean recordFailedLogin() {
        if (lockedUntil != null && !isLocked()) { // an old lock ran out -> start counting again
            lockedUntil = null;
            failedLoginAttempts = 0;
        }
        int attempts = (failedLoginAttempts == null ? 0 : failedLoginAttempts) + 1;
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            lockedUntil = Instant.now().plus(Duration.ofMinutes(LOCKOUT_MINUTES));
            failedLoginAttempts = 0;
            return true;
        }
        failedLoginAttempts = attempts;
        return false;
    }

    public int loginAttemptsLeft() {
        return MAX_LOGIN_ATTEMPTS - (failedLoginAttempts == null ? 0 : failedLoginAttempts);
    }

    // call after a correct password
    public void resetFailedLogins() {
        failedLoginAttempts = null;
        lockedUntil = null;
    }

    // ===== email verification =====

    public boolean isEmailVerified() { return !Boolean.FALSE.equals(emailVerified); }

    // makes a new random 6-digit code, keeps only its hash, and returns the plain code so it can be emailed
    public String startVerification() {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        this.emailVerified = false;
        this.verificationCodeHash = PasswordHasher.hash(code);
        this.verificationSentAt = now;
        this.verificationExpiresAt = now.plus(Duration.ofMinutes(CODE_MINUTES_VALID));
        this.verificationAttempts = 0;
        return code;
    }

    // seconds left before another code can be sent (0 = can send now). stops email spamming
    public long secondsUntilResendAllowed() {
        if (verificationSentAt == null) return 0;
        long passed = Duration.between(verificationSentAt, Instant.now()).getSeconds();
        return Math.max(0, RESEND_COOLDOWN_SECONDS - passed);
    }

    // throws with the reason if the code is wrong, expired or tried too many times
    public void verifyEmail(String code) {
        if (isEmailVerified()) return;
        if (verificationCodeHash == null || verificationExpiresAt == null) {
            throw new IllegalArgumentException("No verification code yet. Request a new code.");
        }
        int attempts = verificationAttempts == null ? 0 : verificationAttempts;
        if (attempts >= MAX_CODE_ATTEMPTS) {
            throw new IllegalArgumentException("Too many wrong codes. Request a new code.");
        }
        if (Instant.now().isAfter(verificationExpiresAt)) {
            throw new IllegalArgumentException("That code has expired. Request a new code.");
        }
        String typed = code == null ? "" : code.trim();
        if (!typed.matches("\\d{6}") || !PasswordHasher.matches(typed, verificationCodeHash)) {
            verificationAttempts = attempts + 1;
            int left = MAX_CODE_ATTEMPTS - verificationAttempts;
            throw new IllegalArgumentException(left > 0
                    ? "Wrong code. " + left + (left == 1 ? " try" : " tries") + " left."
                    : "Too many wrong codes. Request a new code.");
        }
        this.emailVerified = true;
        this.verificationCodeHash = null;
        this.verificationExpiresAt = null;
        this.verificationAttempts = null;
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
