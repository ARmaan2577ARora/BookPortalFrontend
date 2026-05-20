package com.example.bookPortalFrontend;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CurrentUser {
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(HttpSession session) {
        Object user = session.getAttribute("loggedInUser");
        return user instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
    public String email(HttpSession session) {
        Map<String, Object> user = get(session);
        return user == null ? "" : String.valueOf(user.get("email"));
    }
    public String role(HttpSession session) {
        Map<String, Object> user = get(session);
        return user == null ? "" : String.valueOf(user.get("roleName"));
    }
    public boolean loggedIn(HttpSession session) { return get(session) != null; }
    public boolean admin(HttpSession session) { return "ADMIN".equalsIgnoreCase(role(session)); }
    public boolean customer(HttpSession session) { return "CUSTOMER".equalsIgnoreCase(role(session)); }
}
