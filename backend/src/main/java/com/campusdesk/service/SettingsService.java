package com.campusdesk.service;

import com.campusdesk.error.ApiException;
import com.campusdesk.model.Category;
import com.campusdesk.model.Priority;
import com.campusdesk.model.Role;
import com.campusdesk.model.SlaPolicy;
import com.campusdesk.repo.SettingsRepository;
import com.campusdesk.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SettingsService {

    private final SettingsRepository settings;

    public SettingsService(SettingsRepository settings) {
        this.settings = settings;
    }

    public List<Category> categories(boolean includeInactive) {
        return settings.categories(includeInactive);
    }

    public List<SlaPolicy> slaPolicies() {
        return settings.slaPolicies();
    }

    public List<String> knownBuildings() {
        return settings.knownBuildings();
    }

    public Category addCategory(CurrentUser me, String name) {
        me.require(Role.ADMIN);
        String n = Validation.text(name, "Category name", 60, true);
        long id = settings.insertCategory(n);
        return settings.findCategory(id).orElseThrow();
    }

    // never delete categories, old tickets still use them
    public Category updateCategory(CurrentUser me, long id, String name, boolean active) {
        me.require(Role.ADMIN);
        Category c = settings.findCategory(id).orElseThrow(() -> ApiException.notFound("Category", id));
        settings.updateCategory(id, Validation.text(name == null ? c.name() : name, "Category name", 60, true), active);
        return settings.findCategory(id).orElseThrow();
    }

    // only applies to new or re-prioritised tickets
    public SlaPolicy updateSla(CurrentUser me, Priority priority, int responseMinutes, int resolutionMinutes) {
        me.require(Role.ADMIN);
        SlaPolicy p;
        try {
            p = new SlaPolicy(priority, responseMinutes, resolutionMinutes);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        settings.updateSla(p);
        return settings.slaFor(priority);
    }
}
