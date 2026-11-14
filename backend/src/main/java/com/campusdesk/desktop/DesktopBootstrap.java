package com.campusdesk.desktop;

import java.awt.Desktop;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything that has to happen BEFORE Spring starts, in plain Java:
 *  - decide desktop vs dev mode,
 *  - detect an already-running CampusDesk (so double-clicking the icon twice just
 *    focuses the app instead of starting a second server),
 *  - pick a free port,
 *  - publish/read the runtime file (server.json) that tells the MCP server which port
 *    the app is on,
 *  - open the app window.
 */
public final class DesktopBootstrap {
    private DesktopBootstrap() {}

    public static final int DEFAULT_PORT = 47130;
    private static final Pattern PORT_IN_JSON = Pattern.compile("\"port\"\\s*:\\s*(\\d+)");

    // ------------------------------------------------------------------ modes & folders

    /** Desktop mode = installed app or run.ps1: own window, tray icon, data in AppData. */
    public static boolean isDesktopMode() {
        String v = System.getProperty("campusdesk.desktop", System.getenv("CAMPUSDESK_DESKTOP"));
        return "true".equalsIgnoreCase(v == null ? "" : v.trim());
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** %LOCALAPPDATA%\CampusDesk on Windows, ~/.campusdesk elsewhere. */
    public static Path appHome() {
        String local = System.getenv("LOCALAPPDATA");
        if (isWindows() && local != null && !local.isBlank()) return Path.of(local, "CampusDesk");
        return Path.of(System.getProperty("user.home"), ".campusdesk");
    }

    /** The MCP server reads this file to find the running app's port. */
    public static Path runtimeFile() {
        return appHome().resolve("server.json");
    }

    // ------------------------------------------------------------------ ports

    public static int preferredPort() {
        String v = System.getProperty("campusdesk.port", System.getenv("CAMPUSDESK_PORT"));
        try {
            return v == null ? DEFAULT_PORT : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    /**
     * Returns the preferred port if it's free, otherwise any free port the OS gives us
     * (binding port 0 asks the OS to pick one). There's a tiny window where another program
     * could grab the port before Spring binds it, which is acceptable for a desktop app.
     */
    public static int choosePort(int preferred) {
        if (isFree(preferred)) return preferred;
        try (ServerSocket s = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        } catch (IOException e) {
            return preferred;   // let Spring report the real error
        }
    }

    static boolean isFree(int port) {
        try (ServerSocket s = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
            s.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ runtime file

    public static void writeRuntimeFile(int port) {
        try {
            Files.createDirectories(runtimeFile().getParent());
            String json = "{\n  \"app\": \"campusdesk\",\n  \"port\": " + port
                    + ",\n  \"url\": \"http://127.0.0.1:" + port + "\",\n  \"pid\": " + ProcessHandle.current().pid() + "\n}\n";
            Files.writeString(runtimeFile(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("CampusDesk: could not write " + runtimeFile() + ": " + e.getMessage());
        }
    }

    /** Only deletes the file if it still describes THIS instance's port. */
    public static void deleteRuntimeFile(int port) {
        try {
            if (readRuntimePort().orElse(-1) == port) Files.deleteIfExists(runtimeFile());
        } catch (IOException ignored) {
            // best effort
        }
    }

    static Optional<Integer> readRuntimePort() {
        try {
            Matcher m = PORT_IN_JSON.matcher(Files.readString(runtimeFile()));
            return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Is another CampusDesk already running? We read its port from server.json and ask its
     * /api/health endpoint. A stale file (app crashed) simply fails the health check.
     */
    public static Optional<Integer> runningInstancePort() {
        Optional<Integer> port = readRuntimePort();
        if (port.isEmpty()) return Optional.empty();
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create("http://127.0.0.1:" + port.get() + "/api/health")
                    .toURL().openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(1500);
            if (c.getResponseCode() != 200) return Optional.empty();
            String body = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return body.contains("campusdesk") ? port : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ app window

    /**
     * Opens CampusDesk in a chromeless "app mode" window (no tabs or address bar) using
     * Microsoft Edge, which every Windows 10/11 PC has. A separate browser profile folder
     * keeps it independent from the user's normal browsing. Falls back to Chrome, then to
     * the default browser.
     */
    public static void openWindow(String url) {
        for (Path browser : appModeBrowsers()) {
            if (!Files.isRegularFile(browser)) continue;
            try {
                new ProcessBuilder(browser.toString(),
                        "--app=" + url,
                        "--user-data-dir=" + appHome().resolve("window-profile"),
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--window-size=1440,920").start();
                return;
            } catch (IOException e) {
                // try the next option
            }
        }
        openInDefaultBrowser(url);
    }

    static List<Path> appModeBrowsers() {
        List<Path> out = new ArrayList<>();
        if (!isWindows()) return out;
        for (String env : new String[]{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"}) {
            String base = System.getenv(env);
            if (base == null) continue;
            out.add(Path.of(base, "Microsoft", "Edge", "Application", "msedge.exe"));
        }
        for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) {
            String base = System.getenv(env);
            if (base == null) continue;
            out.add(Path.of(base, "Google", "Chrome", "Application", "chrome.exe"));
        }
        return out;
    }

    static void openInDefaultBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else if (isWindows()) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("Open " + url + " in your browser.");
        }
    }
}
