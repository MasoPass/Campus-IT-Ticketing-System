package com.campusdesk.web;

import com.campusdesk.model.User;
import com.campusdesk.repo.UserRepository;
import com.campusdesk.security.AuthInterceptor;
import com.campusdesk.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    // setup screen, login screen, or the signed-in user
    @GetMapping("/state")
    public Map<String, Object> state(HttpServletRequest request) {
        Map<String, Object> out = new HashMap<>();
        out.put("setupRequired", auth.setupRequired());
        out.put("signupAllowed", auth.studentSignupAllowed());
        currentUser(request).ifPresent(u -> out.put("user", u));
        return out;
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public User setup(@RequestBody Requests.Setup body, HttpServletRequest request) {
        User u = auth.setupAdmin(body.fullName(), body.email(), body.password());
        startSession(request, u);
        return u;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public User register(@RequestBody Requests.Register body, HttpServletRequest request) {
        User u = auth.registerStudent(body.fullName(), body.email(), body.studentNumber(), body.password());
        startSession(request, u);
        return u;
    }

    @PostMapping("/login")
    public User login(@RequestBody Requests.Login body, HttpServletRequest request) {
        User u = auth.login(body.email(), body.password());
        startSession(request, u);
        return u;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) s.invalidate();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody Requests.ChangePassword body, HttpServletRequest request) {
        User me = currentUser(request)
                .orElseThrow(() -> com.campusdesk.error.ApiException.unauthorized("Please sign in"));
        auth.changeOwnPassword(me.id(), body.currentPassword(), body.newPassword());
    }

    // new session id on login (session fixation)
    private static void startSession(HttpServletRequest request, User user) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.id());
    }

    private java.util.Optional<User> currentUser(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object uid = s == null ? null : s.getAttribute(AuthInterceptor.SESSION_USER_ID);
        if (!(uid instanceof Long id)) return java.util.Optional.empty();
        return users.findById(id).filter(User::active);
    }
}
