package com.campusdesk.desktop;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.net.URL;

/**
 * The Spring side of desktop mode: once the web server is up, publish its port, open the
 * app window and put an icon in the system tray (Open / Quit). Closing the window leaves
 * CampusDesk running in the tray, so Claude/Codex can still reach it.
 */
@Component
public class DesktopIntegration {
    private static final Logger log = LoggerFactory.getLogger(DesktopIntegration.class);

    private final ConfigurableApplicationContext context;
    private volatile int port = -1;
    private TrayIcon trayIcon;

    public DesktopIntegration(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /** Fires when Tomcat is listening; the event tells us the actual port. */
    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
        DesktopBootstrap.writeRuntimeFile(port);
        log.info("CampusDesk is running at {}", url());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!DesktopBootstrap.isDesktopMode()) return;
        installTrayIcon();
        DesktopBootstrap.openWindow(url());
    }

    public String url() {
        return "http://127.0.0.1:" + port;
    }

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) {
            log.info("System tray not supported here; close CampusDesk by closing its console/process.");
            return;
        }
        URL iconUrl = getClass().getResource("/static/icon-64.png");
        Image image = iconUrl == null ? null : Toolkit.getDefaultToolkit().getImage(iconUrl);

        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open CampusDesk");
        open.addActionListener(e -> DesktopBootstrap.openWindow(url()));
        MenuItem quit = new MenuItem("Quit CampusDesk");
        quit.addActionListener(e -> quit());
        menu.add(open);
        menu.addSeparator();
        menu.add(quit);

        trayIcon = new TrayIcon(image, "CampusDesk", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> DesktopBootstrap.openWindow(url()));   // double-click
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            log.warn("Could not add tray icon: {}", e.getMessage());
        }
    }

    /** Shut down on a separate thread: AWT's event thread must not block on Spring's shutdown. */
    private void quit() {
        Thread t = new Thread(() -> System.exit(SpringApplication.exit(context, () -> 0)), "campusdesk-quit");
        t.start();
    }

    @PreDestroy
    public void cleanup() {
        if (port > 0) DesktopBootstrap.deleteRuntimeFile(port);
        if (trayIcon != null && SystemTray.isSupported()) SystemTray.getSystemTray().remove(trayIcon);
    }
}
