package com.campusdesk.service;

import com.campusdesk.error.ApiException;
import com.campusdesk.model.Role;
import com.campusdesk.model.User;
import com.campusdesk.repo.UserRepository;
import com.campusdesk.security.CurrentUser;
import com.campusdesk.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public UserService(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    public List<User> list(CurrentUser me, Role role) {
        me.require(Role.ADMIN);
        return users.findAll(role);
    }

    public List<User> assignable(CurrentUser me) {
        me.requireStaff();
        return users.findAssignable();
    }

    public User create(CurrentUser me, String fullName, String email, Role role, String studentNumber, String password) {
        me.require(Role.ADMIN);
        String e = Validation.email(email);
        if (users.findByEmail(e).isPresent()) throw ApiException.conflict("An account with that email already exists");
        String sn = role == Role.STUDENT ? Validation.studentNumber(studentNumber) : null;
        if (sn != null && users.studentNumberTaken(sn)) throw ApiException.conflict("That student number is already registered");
        long id = users.insert(e, Validation.name(fullName), role, sn, hasher.hash(Validation.password(password)),
                Times.format(clock.instant()));
        return users.findById(id).orElseThrow();
    }

    @Transactional
    public User update(CurrentUser me, long id, String fullName, Role role, String studentNumber, boolean active) {
        me.require(Role.ADMIN);
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
        boolean losesAdmin = u.role() == Role.ADMIN && u.active() && (role != Role.ADMIN || !active);
        if (losesAdmin && users.countActiveAdmins() <= 1) {
            throw ApiException.conflict("CampusDesk needs at least one active administrator");
        }
        String sn = role == Role.STUDENT
                ? Validation.studentNumber(studentNumber != null ? studentNumber : u.studentNumber())
                : null;
        if (sn != null && !sn.equals(u.studentNumber()) && users.studentNumberTaken(sn)) {
            throw ApiException.conflict("That student number is already registered");
        }
        users.update(id, Validation.name(fullName), role, sn, active);
        return users.findById(id).orElseThrow();
    }

    public void resetPassword(CurrentUser me, long id, String password) {
        me.require(Role.ADMIN);
        users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
        users.updatePassword(id, hasher.hash(Validation.password(password)));
    }
}
