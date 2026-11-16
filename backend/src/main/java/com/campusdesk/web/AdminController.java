package com.campusdesk.web;

import com.campusdesk.model.Category;
import com.campusdesk.model.DeviceType;
import com.campusdesk.model.Priority;
import com.campusdesk.model.Role;
import com.campusdesk.model.SlaPolicy;
import com.campusdesk.model.TicketStatus;
import com.campusdesk.model.User;
import com.campusdesk.security.CurrentUser;
import com.campusdesk.service.DashboardService;
import com.campusdesk.service.SettingsService;
import com.campusdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class AdminController {

    private final UserService users;
    private final SettingsService settings;
    private final DashboardService dashboard;

    public AdminController(UserService users, SettingsService settings, DashboardService dashboard) {
        this.users = users;
        this.settings = settings;
        this.dashboard = dashboard;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("app", "campusdesk", "version", "1.0.0");
    }

    // categories, SLA targets, enums and buildings for the forms
    @GetMapping("/api/meta")
    public Map<String, Object> meta(CurrentUser me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", settings.categories(me.isAdmin()));
        out.put("sla", settings.slaPolicies());
        out.put("priorities", Priority.values());
        out.put("statuses", TicketStatus.values());
        out.put("deviceTypes", DeviceType.values());
        out.put("buildings", settings.knownBuildings());
        return out;
    }

    @GetMapping("/api/dashboard")
    public Map<String, Object> dashboard(CurrentUser me) {
        return dashboard.dashboard(me);
    }


    @GetMapping("/api/users")
    public List<User> listUsers(CurrentUser me, @RequestParam(required = false) String role) {
        return users.list(me, role == null || role.isBlank() ? null : Role.valueOf(role.toUpperCase(Locale.ROOT)));
    }

    @GetMapping("/api/users/assignable")
    public List<User> assignable(CurrentUser me) {
        return users.assignable(me);
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(CurrentUser me, @Valid @RequestBody Requests.NewUser body) {
        return users.create(me, body.fullName(), body.email(), body.role(), body.studentNumber(), body.password());
    }

    @PatchMapping("/api/users/{id}")
    public User updateUser(CurrentUser me, @PathVariable long id, @Valid @RequestBody Requests.UpdateUser body) {
        return users.update(me, id, body.fullName(), body.role(), body.studentNumber(), body.active());
    }

    @PostMapping("/api/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(CurrentUser me, @PathVariable long id, @RequestBody Requests.ResetPassword body) {
        users.resetPassword(me, id, body.password());
    }


    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Category addCategory(CurrentUser me, @RequestBody Requests.CategoryBody body) {
        return settings.addCategory(me, body.name());
    }

    @PatchMapping("/api/categories/{id}")
    public Category updateCategory(CurrentUser me, @PathVariable long id, @RequestBody Requests.CategoryBody body) {
        return settings.updateCategory(me, id, body.name(), body.active() == null || body.active());
    }

    @PutMapping("/api/sla/{priority}")
    public SlaPolicy updateSla(CurrentUser me, @PathVariable String priority, @RequestBody Requests.SlaBody body) {
        return settings.updateSla(me, Priority.valueOf(priority.toUpperCase(Locale.ROOT)),
                body.responseMinutes(), body.resolutionMinutes());
    }
}
