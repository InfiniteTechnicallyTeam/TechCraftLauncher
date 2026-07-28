package itt.tcl.cli;

import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.launch.GameLauncher;
import itt.tcl.ui.App;
import itt.tcl.version.VersionInstaller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class CLI {
    private static final boolean HISTORY_ENABLED = !"true".equalsIgnoreCase(System.getenv("TCL_DISABLE_HISTORY"));
    private static final int MAX_HISTORY = 1000;
    private static final Path HISTORY_FILE = TCLPaths.TCL_DIR.resolve(".tcl_history");
    private static final List<String> history = Collections.synchronizedList(new ArrayList<>());

    public static void run(String[] args) {
        // single command mode
        if (args != null && args.length > 0) {
            handleCommand(String.join(" ", args), false);
            return;
        }
        // interactive mode
        loadHistory();
        runInteractive();
    }

    public static void runInteractive() {
        System.out.println("TechCraftLauncher v1.0 - CLI mode");
        System.out.println("Type 'help' for help, 'exit' to quit.");
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("\nTCL> ");
            String line;
            try {
                if (!scanner.hasNextLine()) break; // EOF
                line = scanner.nextLine();
            } catch (Exception e) {
                break;
            }
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                App.shutdownGUI();
                System.out.println("Goodbye!");
                break;
            }
            if (HISTORY_ENABLED) appendHistory(line);
            handleCommand(line, true);
        }
        scanner.close();
    }

    private static void loadHistory() {
        if (!HISTORY_ENABLED) return;
        try {
            if (Files.exists(HISTORY_FILE)) {
                List<String> lines = Files.readAllLines(HISTORY_FILE, StandardCharsets.UTF_8);
                for (String l : lines) {
                    if (l != null && !l.isBlank()) history.add(l);
                }
                // trim if too long
                trimHistory();
            } else {
                try {
                    Files.createDirectories(HISTORY_FILE.getParent());
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {
            // ignore history load failures
        }
    }

    private static void appendHistory(String cmd) {
        if (!HISTORY_ENABLED || cmd == null || cmd.isBlank()) return;
        history.add(cmd);
        trimHistory();
        try {
            Files.write(HISTORY_FILE, (cmd + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // ignore write failures
        }
    }

    private static void trimHistory() {
        synchronized (history) {
            if (history.size() > MAX_HISTORY) {
                int from = history.size() - MAX_HISTORY;
                List<String> trimmed = new ArrayList<>(history.subList(from, history.size()));
                history.clear();
                history.addAll(trimmed);
            }
        }
    }

    public static void handleCommand(String input, boolean interactive) {
        String[] parts = input.split("\\s+");
        String cmd = parts[0].toLowerCase();
        try {
            switch (cmd) {
                case "launch" -> {
                    if (parts.length < 2) { System.out.println("Usage: launch <version>"); return; }
                    GameLauncher.launch(parts[1]);
                    System.out.println("Game exited.");
                }
                case "install" -> {
                    if (parts.length < 2) { System.out.println("Usage: install <version>"); return; }
                    VersionInstaller.installVersion(parts[1]);
                    System.out.println("Installation complete.");
                }
                case "update" -> {
                    try { Files.deleteIfExists(TCLPaths.VERSION_MANIFEST); } catch (Exception ignored) {}
                    VersionInstaller.fetchVersionManifest();
                    System.out.println("Version list updated.");
                }
                case "list" -> {
                    try (var s = Files.list(TCLPaths.VERSIONS_DIR)) {
                        s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).forEach(n -> System.out.println(" - " + n));
                    }
                }
                case "login" -> AuthManager.login();
                case "logout" -> AuthManager.logout();
                case "gui" -> App.launchGUI(interactive);
                case "whoami" -> {
                    if (AuthManager.isLoggedIn()) {
                        System.out.println(AuthManager.getUsername());
                    } else {
                        System.out.println("Not logged in. Use 'login' to sign in.");
                    }
                }
                case "history" -> {
                    int n = 20;
                    if (parts.length >= 2) {
                        try { n = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                    }
                    showHistory(n);
                }
                case "clear-history" -> {
                    history.clear();
                    try { Files.deleteIfExists(HISTORY_FILE); } catch (IOException ignored) {}
                    System.out.println("History cleared.");
                }
                case "help", "" -> System.out.println(
                        "launch <version>   - Start game\n" +
                                "install <version>  - Download version\n" +
                                "gui                - Open GUI mode\n" +
                                "login              - Microsoft account login\n" +
                                "logout             - Sign out\n" +
                                "whoami             - Show current account\n" +
                                "update             - Refresh version list\n" +
                                "list               - List installed versions\n" +
                                "history [n]        - Show last n commands (default 20)\n" +
                                "clear-history      - Clear saved command history\n" +
                                "exit               - Quit"
                );
                default -> System.out.println("Unknown command.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void showHistory(int n) {
        if (!HISTORY_ENABLED) {
            System.out.println("History is disabled. Set TCL_DISABLE_HISTORY=false to enable.");
            return;
        }
        List<String> snapshot;
        synchronized (history) {
            snapshot = new ArrayList<>(history);
        }
        int size = snapshot.size();
        if (size == 0) {
            System.out.println("(no history)");
            return;
        }
        int from = Math.max(0, size - n);
        List<String> sub = snapshot.subList(from, size);
        int index = from + 1;
        for (String line : sub) {
            System.out.printf("%4d  %s%n", index++, line);
        }
    }
}