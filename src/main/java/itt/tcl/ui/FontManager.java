package itt.tcl.ui;

import javafx.scene.Parent;
import javafx.scene.text.Font;

import java.net.URL;

public final class FontManager {
    public static final String CHINESE_FONT_RESOURCE =
            "/itt/tcl/ui/font/fzlt.ttf";

    private static boolean loadAttempted;
    private static String chineseFontFamily;

    private FontManager() {}

    public static void applyLanguageFont(Parent root) {
        if (LanguageManager.getLanguage() != LanguageManager.Language.CHINESE) {
            return;
        }

        String fontFamily = getChineseFontFamily();
        if (fontFamily == null) {
            return;
        }

        String escapedFamily = fontFamily
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        String fontStyle = "-fx-font-family: \"" + escapedFamily + "\";";
        root.setStyle(appendStyle(root.getStyle(), fontStyle));
    }

    public static boolean isChineseFontAvailable() {
        return getChineseFontFamily() != null;
    }

    private static synchronized String getChineseFontFamily() {
        if (loadAttempted) {
            return chineseFontFamily;
        }
        loadAttempted = true;

        URL fontResource = FontManager.class.getResource(CHINESE_FONT_RESOURCE);
        if (fontResource == null) {
            System.err.println("Chinese UI font not found: " + CHINESE_FONT_RESOURCE);
            return null;
        }

        Font font;
        try {
            font = Font.loadFont(fontResource.toExternalForm(), 12);
        } catch (RuntimeException e) {
            System.err.println("Unable to load Chinese UI font: " + e.getMessage());
            return null;
        }
        if (font == null) {
            System.err.println("Unable to load Chinese UI font: " + CHINESE_FONT_RESOURCE);
            return null;
        }

        chineseFontFamily = font.getFamily();
        return chineseFontFamily;
    }

    private static String appendStyle(String existingStyle, String newStyle) {
        if (existingStyle == null || existingStyle.isBlank()) {
            return newStyle;
        }
        return existingStyle.endsWith(";")
                ? existingStyle + " " + newStyle
                : existingStyle + "; " + newStyle;
    }
}
