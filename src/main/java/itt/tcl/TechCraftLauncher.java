package itt.tcl;

import itt.tcl.ui.App;
import itt.tcl.cli.CLI;
import java.io.*;

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

        // 其它参数：交由 CLI 处理（包含单次命令和交互式历史）
        CLI.run(args);
    }
}
