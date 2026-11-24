package com.foldermania.i18n;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Translations of the user interface. Texts live in /i18n/messages_xx.properties (UTF-8).
 * The current language is an observable property, so labels bound with {@link #binding} change
 * instantly when the user picks another language. A missing key falls back to English, then to the key.
 */
public final class I18n {

    public enum Language {
        EN("en", "English"),
        IT("it", "Italiano"),
        FR("fr", "Fran\u00e7ais"),
        ES("es", "Espa\u00f1ol");

        private final String code;
        private final String nativeName;

        Language(String code, String nativeName) {
            this.code = code;
            this.nativeName = nativeName;
        }

        public String code() {
            return code;
        }

        /** The language's own name, shown in the language menu (never translated). */
        public String nativeName() {
            return nativeName;
        }

        public Locale locale() {
            return Locale.forLanguageTag(code);
        }

        /** The language with this code, or the fallback if the code is null or unknown. */
        public static Language fromCode(String code, Language fallback) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code)) {
                    return language;
                }
            }
            return fallback;
        }
    }

    private static final ObjectProperty<Language> LANGUAGE = new SimpleObjectProperty<>(
            Language.fromCode(Locale.getDefault().getLanguage(), Language.EN));
    private static final Map<Language, Properties> BUNDLES = new EnumMap<>(Language.class);

    private I18n() {}

    public static ObjectProperty<Language> languageProperty() {
        return LANGUAGE;
    }

    public static Language language() {
        return LANGUAGE.get();
    }

    public static void setLanguage(Language language) {
        LANGUAGE.set(language);
    }

    /** Translates a key. {0}, {1}... in the text are replaced by the arguments. */
    public static String tr(String key, Object... args) {
        String text = lookup(language(), key);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /** Translates a text that depends on a number: uses key.one when n is 1, key.other otherwise. {0} is n. */
    public static String trn(String key, long n, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = n;
        System.arraycopy(args, 0, all, 1, args.length);
        return tr(key + (n == 1 ? ".one" : ".other"), all);
    }

    /** A text that follows the current language. */
    public static StringBinding binding(String key, Object... args) {
        return Bindings.createStringBinding(() -> tr(key, args), LANGUAGE);
    }

    private static String lookup(Language language, String key) {
        String text = bundle(language).getProperty(key);
        if (text == null && language != Language.EN) {
            text = bundle(Language.EN).getProperty(key);
        }
        return text != null ? text : key;
    }

    private static synchronized Properties bundle(Language language) {
        return BUNDLES.computeIfAbsent(language, l -> {
            Properties props = new Properties();
            try (InputStream in = I18n.class.getResourceAsStream("/i18n/messages_" + l.code() + ".properties")) {
                if (in != null) {
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        props.load(reader);
                    }
                }
            } catch (IOException ignored) {
                // A missing bundle just means keys are shown as they are
            }
            return props;
        });
    }
}
