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

public final class DesktopBootstrap {
    private DesktopBootstrap() {}

    public static final int DEFAULT_PORT = 47130;
    private static final Pattern PORT_IN_JSON = Pattern.compile("\"port\"\\s*:\\s*(\\d+)");


    // installed app or run.ps1
    public static boolean isDesktopMode() {
        String v = System.getProperty("campusdesk.desktop", System.getenv("CAMPUSDESK_DESKTOP"));
        return "true".equalsIgnoreCase(v == null ? "" : v.trim());
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // %LOCALAPPDATA%\CampusDesk on Windows, ~/.campusdesk elsewhere
    public static Path appHome() {
        String local = System.getenv("LOCALAPPDATA");
        if (isWindows() && local != null && !local.isBlank()) return Path.of(local, "CampusDesk");
        return Path.of(System.getProperty("user.home"), ".campusdesk");
    }

    // a second launch reads this to find the running app
    public static Path runtimeFile() {
        return appHome().resolve("server.json");
    }


    public static int preferredPort() {
        String v = System.getProperty("campusdesk.port", System.getenv("CAMPUSDESK_PORT"));
        try {
            return v == null ? DEFAULT_PORT : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    // use the preferred port if it's free, otherwise any free one
    public static int choosePort(int preferred) {
        if (isFree(preferred)) return preferred;
        try (ServerSocket s = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        } catch (IOException e) {
            return preferred; // let Spring report the real error
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

    // only delete it if it's still ours
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

    // is another CampusDesk already running?
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


    // open as an app window (no tabs or address bar)
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
