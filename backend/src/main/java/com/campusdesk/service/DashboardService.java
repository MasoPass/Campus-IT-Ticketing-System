package com.campusdesk.service;

import com.campusdesk.repo.DashboardRepository;
import com.campusdesk.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Staff dashboard: every chart in one response. */
@Service
public class DashboardService {

    private final DashboardRepository dashboard;
    private final Clock clock;

    public DashboardService(DashboardRepository dashboard, Clock clock) {
        this.dashboard = dashboard;
        this.clock = clock;
    }

    public Map<String, Object> dashboard(CurrentUser me) {
        me.requireStaff();
        Instant now = clock.instant();
        String nowText = Times.format(now);
        String since = Times.format(now.minus(Duration.ofDays(30)));

        // LinkedHashMap keeps insertion order, so the JSON keys come out in a predictable order.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", dashboard.summary(nowText, since));
        out.put("openByStatus", dashboard.openByStatus());
        out.put("openByPriority", dashboard.openByPriority());
        out.put("byCategory", dashboard.byCategory(since));
        out.put("perDay", dashboard.createdPerDay(nowText));
        out.put("technicians", dashboard.technicianWorkload(nowText, since));
        return out;
    }
}
