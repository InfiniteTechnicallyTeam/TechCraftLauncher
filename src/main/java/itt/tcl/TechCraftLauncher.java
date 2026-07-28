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

        // 当没有任何参数时，默认直接启动 GUI（便于双击启动）
        if (args.length == 0) {
            App.launchGUI(false);
            return;
        }

        // 仍然支持显式 --gui 参数
        if (args.length > 0 && "--gui".equals(args[0])) {
            App.launchGUI(false);
            return;
        }

        // 其它参数：按原有逻辑作为命令行单次执行
        if (args.length > 0) {
            handleCommand(String.join(" ", args), false);
            return;
        }

        // 保留交互式 CLI（正常情况下不会到这里，因为上面已经处理了 args.length==0 的情况）
        System.out.println("TechCraftLauncher v1.0 - CLI mode");
        System.out.println("Type 'help' for help, 'exit' to quit.");
        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
        while (true) {
            System.out.print("\nTCL> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                App.shutdownGUI();
                System.out.println("Goodbye!");
                break;
            }
            handleCommand(line, true);
        }
        scanner.close();
    }

    private static void handleCommand(String input, boolean interactive) {
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
                case "gui" -> App.launchGUI(interactive);
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
