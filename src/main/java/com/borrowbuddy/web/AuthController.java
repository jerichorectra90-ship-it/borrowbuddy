package com.borrowbuddy.web;

import com.borrowbuddy.model.*;
import com.borrowbuddy.repository.UserRepository;
import com.borrowbuddy.service.VerificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Feature 1 - UST-L login (email or student ID) + email verification + the student profile
@Controller
public class AuthController {
    // the account waiting for its email code. NOT logged in yet, can only use the /verify page
    private static final String PENDING = "pendingVerifyUserId";

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final VerificationService verification;

    public AuthController(UserRepository users, CurrentUser currentUser, VerificationService verification) {
        this.users = users;
        this.currentUser = currentUser;
        this.verification = verification;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        User user = currentUser.get(session);
        if (user == null) return "login";
        return user.isAdmin() ? "redirect:/admin" : "redirect:/items";
    }

    @PostMapping("/login")
    public String login(@RequestParam String account, @RequestParam String password,
                        HttpSession session, Model model, RedirectAttributes flash) {
        String a = account.trim();
        Optional<User> found = a.contains("@")
                ? users.findByUstlAccountIgnoreCase(a)
                : users.findByStudentId(a);
        if (found.isEmpty() || !found.get().checkPassword(password)) {
            model.addAttribute("error", "Wrong UST-L account or password.");
            return "login";
        }
        User user = found.get();
        // right password but email never verified -> no login, send them to enter the code
        if (!user.isEmailVerified()) {
            session.setAttribute(PENDING, user.getId());
            String message;
            try {
                verification.sendCode(user);
                message = "Your email is not verified yet. We sent a new code to " + user.getUstlAccount() + ".";
            } catch (IllegalArgumentException e) { // a code was sent less than a minute ago
                message = "Your email is not verified yet. Enter the code we sent to " + user.getUstlAccount() + ".";
            }
            flash.addFlashAttribute("message", message);
            return "redirect:/verify";
        }
        currentUser.login(session, user);
        return user.isAdmin() ? "redirect:/admin" : "redirect:/items";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new HashMap<String, String>());
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam Map<String, String> params, HttpSession session, Model model,
                           RedirectAttributes flash) {
        Map<String, String> form = new HashMap<>(params);
        try {
            String email = form.getOrDefault("email", "").trim();
            String studentId = form.getOrDefault("studentId", "").trim();
            if (studentId.isEmpty()) {
                throw new IllegalArgumentException("Student ID is required.");
            }
            if (users.findByUstlAccountIgnoreCase(email).isPresent()) {
                throw new IllegalArgumentException("That UST-L email is already registered.");
            }
            if (users.findByStudentId(studentId).isPresent()) {
                throw new IllegalArgumentException("That student ID is already registered.");
            }
            Profile profile = new Profile(form.get("course"), parseYear(form.get("yearLevel")),
                    form.get("block"), form.get("contactInfo"));
            User user = "OrgOfficer".equals(form.get("role"))
                    ? new OrgOfficer(form.get("fullName"), email, studentId, form.get("password"),
                            profile, form.get("organization"))
                    : new Student(form.get("fullName"), email, studentId, form.get("password"), profile);
            user = users.save(user);
            // not logged in yet - they have to enter the code sent to their UST-L email first
            session.setAttribute(PENDING, user.getId());
            verification.sendCode(user);
            flash.addFlashAttribute("message", "Account created. We sent a 6-digit code to "
                    + user.getUstlAccount() + ". Enter it below to activate your account.");
            return "redirect:/verify";
        } catch (IllegalArgumentException e) {
            form.remove("password");
            model.addAttribute("form", form);
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    // ===== email verification =====

    private User pendingUser(HttpSession session) {
        Object id = session.getAttribute(PENDING);
        return id instanceof Long ? users.findById((Long) id).orElse(null) : null;
    }

    @GetMapping("/verify")
    public String verifyForm(HttpSession session, Model model) {
        User user = pendingUser(session);
        if (user == null) return "redirect:/";
        model.addAttribute("email", user.getUstlAccount());
        model.addAttribute("minutes", User.CODE_MINUTES_VALID);
        return "verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String code, HttpSession session, Model model) {
        User user = pendingUser(session);
        if (user == null) return "redirect:/";
        try {
            verification.verify(user, code);
        } catch (IllegalArgumentException e) {
            model.addAttribute("email", user.getUstlAccount());
            model.addAttribute("minutes", User.CODE_MINUTES_VALID);
            model.addAttribute("error", e.getMessage());
            return "verify";
        }
        session.removeAttribute(PENDING);
        currentUser.login(session, user);
        return user.isAdmin() ? "redirect:/admin" : "redirect:/items";
    }

    @PostMapping("/verify/resend")
    public String resend(HttpSession session, RedirectAttributes flash) {
        User user = pendingUser(session);
        if (user == null) return "redirect:/";
        try {
            verification.sendCode(user);
            flash.addFlashAttribute("message", "We sent a new code to " + user.getUstlAccount() + ".");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/verify";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String course, @RequestParam String yearLevel,
                                @RequestParam String block, @RequestParam String contactInfo,
                                HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        try {
            Profile p = user.getProfile();
            p.setCourse(course);
            p.setYearLevel(parseYear(yearLevel));
            p.setBlock(block);
            p.setContactInfo(contactInfo);
            users.save(user);
            flash.addFlashAttribute("message", "Profile updated.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/profile";
    }

    private static int parseYear(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Year level must be a number from 1 to 5.");
        }
    }
}
