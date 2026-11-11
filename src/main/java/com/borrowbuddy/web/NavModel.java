package com.borrowbuddy.web;

import com.borrowbuddy.model.User;
import com.borrowbuddy.service.BorrowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Adds the number of requests waiting for the logged-in owner, shown as a badge in the top navigation.
@ControllerAdvice
public class NavModel {
    private final BorrowService service;
    private final CurrentUser currentUser;

    public NavModel(BorrowService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @ModelAttribute("navPending")
    public Integer navPending(HttpSession session) {
        User user = currentUser.get(session);
        if (user == null || user.isAdmin()) return 0;
        return service.incomingRequests(user).size();
    }
}
