package itt.tcl;

import itt.tcl.auth.AuthManager;
import itt.tcl.config.TCLPaths;
import itt.tcl.launch.GameLauncher;
import itt.tcl.ui.App;
import itt.tcl.version.VersionInstaller;
import java.io.*;
import java.nio.file.*;
import java.util.Scanner;

public class TechCraftLauncher {
    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));

        // GUI mode
        if (args.length > 0 && "--gui".equals(args[0])) {
            App.launchGUI();
            return;
        }

        if (args.length > 0) { handleCommand(String.join(" ", args)); return; }

        System.out.println("TechCraftLauncher v1.0 - CLI mode");
        System.out.println("Type 'help' for help, 'exit' to quit.");
        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
        while (true) {
            System.out.print("\nTCL> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                System.out.println("Goodbye!"); break;
            }
            handleCommand(line);
        }
        scanner.close();
    }

    private static void handleCommand(String input) {
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
                    Files.deleteIfExists(TCLPaths.VERSION_MANIFEST);
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
                case "gui" -> App.launchGUI();
                case "whoami" -> {
                    if (AuthManager.isLoggedIn()) {
                        System.out.println(AuthManager.getUsername());
                    } else {
                        System.out.println("Not logged in. Use 'login' to sign in.");
                    }
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
                                "exit               - Quit"
                );
                default -> System.out.println("Unknown command.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
