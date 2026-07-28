package itt.tcl.ui;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public final class LanguageManager {
    private static final String BUNDLE_NAME = "itt.tcl.ui.i18n.messages";
    private static final String PREFERENCE_KEY = "language";
    private static final Preferences PREFERENCES = createPreferences();

    private static Language currentLanguage = loadLanguage();

    private LanguageManager() {}

    public static Language getLanguage() {
        return currentLanguage;
    }

    public static List<Language> getAvailableLanguages() {
        return List.of(Language.CHINESE, Language.ENGLISH);
    }

    public static boolean setLanguage(Language language) {
        Objects.requireNonNull(language, "language");
        if (language == currentLanguage) {
            return false;
        }

        currentLanguage = language;
        if (PREFERENCES != null) {
            try {
                PREFERENCES.put(PREFERENCE_KEY, language.name());
            } catch (SecurityException ignored) {
                // The selection still applies to the current session.
            }
        }
        return true;
    }

    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle(BUNDLE_NAME, currentLanguage.locale());
    }

    public static String text(String key, Object... arguments) {
        String pattern = bundle().getString(key);
        return arguments.length == 0
                ? pattern
                : new MessageFormat(pattern, currentLanguage.locale()).format(arguments);
    }

    public static Language fromDisplayName(String displayName) {
        return getAvailableLanguages().stream()
                .filter(language -> language.displayName().equals(displayName))
                .findFirst()
                .orElse(Language.ENGLISH);
    }

    private static Language loadLanguage() {
        if (PREFERENCES == null) {
            return Language.ENGLISH;
        }
        try {
            return Language.valueOf(PREFERENCES.get(PREFERENCE_KEY, Language.ENGLISH.name()));
        } catch (IllegalArgumentException | SecurityException e) {
            return Language.ENGLISH;
        }
    }

    private static Preferences createPreferences() {
        try {
            return Preferences.userNodeForPackage(LanguageManager.class);
        } catch (SecurityException e) {
            return null;
        }
    }

    public enum Language {
        CHINESE(Locale.SIMPLIFIED_CHINESE, "中文"),
        ENGLISH(Locale.ENGLISH, "English");

        private final Locale locale;
        private final String displayName;

        Language(Locale locale, String displayName) {
            this.locale = locale;
            this.displayName = displayName;
        }

        public Locale locale() {
            return locale;
        }

        public String displayName() {
            return displayName;
        }
    }
}
