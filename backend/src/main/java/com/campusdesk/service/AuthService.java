package com.campusdesk.service;

import com.campusdesk.config.CampusDeskProperties;
import com.campusdesk.error.ApiException;
import com.campusdesk.model.Role;
import com.campusdesk.model.User;
import com.campusdesk.repo.UserRepository;
import com.campusdesk.security.LoginThrottle;
import com.campusdesk.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/** Sign-in, student sign-up and the first-run admin setup. Sessions are handled by AuthController. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final Clock clock;
    private final CampusDeskProperties props;

    public AuthService(UserRepository users, PasswordHasher hasher, LoginThrottle throttle, Clock clock,
                       CampusDeskProperties props) {
        this.users = users;
        this.hasher = hasher;
        this.throttle = throttle;
        this.clock = clock;
        this.props = props;
    }

    /** True until the first account (the admin) exists. */
    public boolean setupRequired() {
        return users.count() == 0;
    }

    public boolean studentSignupAllowed() {
        return props.allowStudentSignup();
    }

    /**
     * Creates the very first account as ADMIN. @Transactional + the count check inside it
     * mean two simultaneous setup requests can't both succeed (SQLite allows one writer).
     */
    @Transactional
    public User setupAdmin(String fullName, String email, String password) {
        if (!setupRequired()) throw ApiException.conflict("CampusDesk is already set up - please sign in");
        long id = users.insert(Validation.email(email), Validation.name(fullName), Role.ADMIN, null,
                hasher.hash(Validation.password(password)), now());
        return users.findById(id).orElseThrow();
    }

    public User registerStudent(String fullName, String email, String studentNumber, String password) {
        if (!props.allowStudentSignup()) throw ApiException.forbidden("Sign-up is turned off - ask IT for an account");
        if (setupRequired()) throw ApiException.conflict("An administrator must set up CampusDesk first");
        String e = Validation.email(email);
        String sn = Validation.studentNumber(studentNumber);
        if (users.findByEmail(e).isPresent()) throw ApiException.conflict("An account with that email already exists");
        if (users.studentNumberTaken(sn)) throw ApiException.conflict("That student number is already registered");
        long id = users.insert(e, Validation.name(fullName), Role.STUDENT, sn, hasher.hash(Validation.password(password)), now());
        return users.findById(id).orElseThrow();
    }

    /**
     * Checks the password. The same vague error is used for "no such user" and "wrong
     * password" so the login form can't be used to find out which emails have accounts.
     */
    public User login(String email, String password) {
        String key = email == null ? "" : email.trim();
        Duration wait = throttle.retryAfter(key);
        if (!wait.isZero() && !wait.isNegative()) {
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }

        User user = users.findByEmail(key).orElse(null);
        boolean ok = user != null && password != null && hasher.matches(password, user.passwordHash());
        if (!ok) {
            throttle.recordFailure(key);
            throw ApiException.unauthorized("Email or password is incorrect");
        }
        if (!user.active()) throw ApiException.forbidden("This account has been deactivated - contact IT");

        throttle.recordSuccess(key);
        // If the hashing settings were strengthened since this password was saved, upgrade it now
        // (the only moment we have the plain password).
        if (hasher.needsRehash(user.passwordHash())) users.updatePassword(user.id(), hasher.hash(password));
        users.touchLogin(user.id(), now());
        return user;
    }

    public void changeOwnPassword(long userId, String current, String next) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
        if (current == null || !hasher.matches(current, user.passwordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        users.updatePassword(userId, hasher.hash(Validation.password(next)));
    }

    private String now() {
        return Times.format(clock.instant());
    }
}
