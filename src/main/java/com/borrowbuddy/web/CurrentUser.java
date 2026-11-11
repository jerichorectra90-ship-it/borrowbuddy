package com.borrowbuddy.web;

import com.borrowbuddy.model.User;
import com.borrowbuddy.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

// Keeps track of who is logged in for this browser session.
@Component
public class CurrentUser {
    private static final String KEY = "userId";
    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get(HttpSession session) {
        Object id = session.getAttribute(KEY);
        if (!(id instanceof Long)) {
            return null;
        }
        return users.findById((Long) id).orElse(null);
    }

    public void login(HttpSession session, User user) {
        session.setAttribute(KEY, user.getId());
    }
}
