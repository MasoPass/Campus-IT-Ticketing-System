package com.campusdesk;

import com.campusdesk.desktop.DesktopBootstrap;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Optional;

/**
 * Entry point. Before Spring starts we do the desktop housekeeping (see DesktopBootstrap):
 * reuse an already-running instance, choose the data folder and a free port.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusDeskApplication {

    public static void main(String[] args) {
        boolean desktop = DesktopBootstrap.isDesktopMode();

        if (desktop) {
            Optional<Integer> running = DesktopBootstrap.runningInstancePort();
            if (running.isPresent()) {
                DesktopBootstrap.openWindow("http://127.0.0.1:" + running.get());
                return;
            }
            if (System.getProperty("campusdesk.data-dir") == null && System.getenv("CAMPUSDESK_DATA_DIR") == null) {
                System.setProperty("campusdesk.data-dir", DesktopBootstrap.appHome().resolve("data").toString());
            }
            System.setProperty("logging.file.name", DesktopBootstrap.appHome().resolve("logs/campusdesk.log").toString());
        }

        if (System.getProperty("server.port") == null && System.getenv("SERVER_PORT") == null) {
            System.setProperty("server.port", String.valueOf(DesktopBootstrap.choosePort(DesktopBootstrap.preferredPort())));
        }

        new SpringApplicationBuilder(CampusDeskApplication.class)
                .headless(!desktop)   // the tray icon needs AWT, which Spring disables by default
                .run(args);
    }
}
