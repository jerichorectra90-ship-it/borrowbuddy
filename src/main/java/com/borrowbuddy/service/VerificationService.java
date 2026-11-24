package com.borrowbuddy.service;

import com.borrowbuddy.model.User;
import com.borrowbuddy.repository.UserRepository;
import org.springframework.stereotype.Service;

// EMAIL VERIFICATION - proves the person registering really owns that UST-L email.
// a random 6-digit code is emailed; the account can't log in until the code is entered.
@Service
public class VerificationService {
    private final UserRepository users;
    private final EmailService email;

    public VerificationService(UserRepository users, EmailService email) {
        this.users = users;
        this.email = email;
    }

    // makes a new code, saves its hash, emails it. returns true if the email went out.
    // if email isn't set up (local / offline), the code is printed to the SERVER console only,
    // never shown on the web page, so it still works for testing on your own computer
    public boolean sendCode(User user) {
        long wait = user.secondsUntilResendAllowed();
        if (wait > 0) {
            throw new IllegalArgumentException("Please wait " + wait + " seconds before requesting another code.");
        }
        String code = user.startVerification();
        users.save(user);
        String html = "<p>Hi " + EmailService.esc(user.getFullName()) + ",</p>"
                + "<p>Your BorrowBuddy verification code is:</p>"
                + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">" + code + "</p>"
                + "<p>It expires in " + User.CODE_MINUTES_VALID + " minutes. "
                + "If you did not create a BorrowBuddy account, you can ignore this email.</p>";
        boolean sent = email.send(user.getUstlAccount(), user.getFullName(), "Your BorrowBuddy verification code", html);
        if (!sent) {
            System.out.println(">>> [Email not sent] Verification code for " + user.getUstlAccount() + ": " + code);
        }
        return sent;
    }

    public void verify(User user, String code) {
        try {
            user.verifyEmail(code);
        } finally {
            users.save(user); // save the attempt count even when the code is wrong
        }
    }
}
