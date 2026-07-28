package itt.tcl;

import itt.tcl.ui.App;
import itt.tcl.cli.CLI;
import java.io.*;

public class TechCraftLauncher {
    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));

        // 无参默认启动 GUI（便于双击启动）
        if (args == null || args.length == 0) {
            App.launchGUI(false);
            return;
        }

        // 支持显式 --gui 参数
        if (args.length > 0 && "--gui".equals(args[0])) {
            App.launchGUI(false);
            return;
        }

        // 支持显式 --cli 参数进入交互式 CLI
        if (args.length > 0 && "--cli".equals(args[0])) {
            CLI.run(new String[0]);
            return;
        }

        // 其它参数：交由 CLI 处理（单次命令）
        CLI.run(args);
    }
}
