package com.borrowbuddy.web;

import com.borrowbuddy.model.*;
import com.borrowbuddy.repository.UserRepository;
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

// Feature 1: UST-L login (email or student ID) and student profile
@Controller
public class AuthController {
    private final UserRepository users;
    private final CurrentUser currentUser;

    public AuthController(UserRepository users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        User user = currentUser.get(session);
        if (user == null) return "login";
        return user.isAdmin() ? "redirect:/admin" : "redirect:/items";
    }

    @PostMapping("/login")
    public String login(@RequestParam String account, @RequestParam String password,
                        HttpSession session, Model model) {
        String a = account.trim();
        Optional<User> found = a.contains("@")
                ? users.findByUstlAccountIgnoreCase(a)
                : users.findByStudentId(a);
        if (found.isEmpty() || !found.get().checkPassword(password)) {
            model.addAttribute("error", "Wrong UST-L account or password.");
            return "login";
        }
        currentUser.login(session, found.get());
        return found.get().isAdmin() ? "redirect:/admin" : "redirect:/items";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new HashMap<String, String>());
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam Map<String, String> params, HttpSession session, Model model) {
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
            currentUser.login(session, user);
            return "redirect:/items";
        } catch (IllegalArgumentException e) {
            form.remove("password");
            model.addAttribute("form", form);
            model.addAttribute("error", e.getMessage());
            return "register";
        }
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
