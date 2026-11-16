package com.campusdesk.security;

import com.campusdesk.error.ApiException;
import com.campusdesk.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// checks the user is logged in before every API call
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER_ID = "uid";
    public static final String REQUEST_ATTRIBUTE = "campusdesk.currentUser";

    private final UserRepository users;

    public AuthInterceptor(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false); // don't create a session
        Object uid = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(uid instanceof Long id)) {
            throw ApiException.unauthorized("Please sign in");
        }
        var user = users.findById(id).filter(u -> u.active()).orElse(null);
        if (user == null) {
            session.invalidate();
            throw ApiException.unauthorized("Your account is no longer active");
        }
        request.setAttribute(REQUEST_ATTRIBUTE, CurrentUser.of(user));
        return true;
    }
}
