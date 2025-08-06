package com.bayoumi.models;

import com.bayoumi.util.LoggerWrapper;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class Muezzin {

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(Muezzin.class);

    public static final Muezzin NO_SOUND = new Muezzin("Silent", "بدون صوت", "");

    private static final List<Muezzin> VALUES = List.of(
            new Muezzin("Abdulbasit Abdusamad", "عبد الباسط عبد الصمد", "adhan-abdulbasit-abdusamad.mp3"),
            new Muezzin("Abul Ainain Shuaisha", "أبو العنين شعيشع", "adhan-abul-ainain-shuaisha.mp3"),
            new Muezzin("Ali Ibn Ahmad Mala", "علي بن أحمد ملا", "adhan-ali-ibn-ahmad-mala.mp3"),
            new Muezzin("Mahmoud Ali Al Banna", "محمود علي البنا", "adhan-mahmoud-ali-al-banna.mp3"),
            new Muezzin("Muhammad Refaat", "محمد رفعت", "adhan-muhammad-refaat.mp3"),
            new Muezzin("Mustafa Ismail", "مصطفى إسماعيل", "adhan-mustafa-ismail.mp3"),
            new Muezzin("Nasser Al Qatami", "ناصر القطامي", "adhan-nasser-al-qatami.mp3"),
            NO_SOUND
    );

    private final String englishName;
    private final String arabicName;
    private final String fileName;
    private final URL url;

    public Muezzin(String englishName, String arabicName, String fileName) {
        this.englishName = englishName;
        this.arabicName = arabicName;
        this.fileName = fileName;
        this.url = fileName == null ? null : Muezzin.class.getResource("/audio/adhan/" + fileName);
    }

    public static List<Muezzin> getMuezzinList() {
        return VALUES;
    }

    public static List<String> getMuezzinFilesNames() {
        return getMuezzinList().stream()
                .map(Muezzin::getFileName)
                .toList();
    }

    public static StringConverter<Muezzin> arabicConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Muezzin object) {
                return object.getArabicName();
            }

            @Override
            public Muezzin fromString(String string) {
                return null;
            }
        };
    }

    public static StringConverter<Muezzin> englishConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Muezzin object) {
                return object.getEnglishName();
            }

            @Override
            public Muezzin fromString(String string) {
                return null;
            }
        };
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getArabicName() {
        return arabicName;
    }

    public String getFileName() {
        return fileName;
    }

    public Optional<URL> getAudioFileURL() {
        return Optional.ofNullable(url);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Muezzin) obj;
        return Objects.equals(this.englishName, that.englishName) &&
               Objects.equals(this.arabicName, that.arabicName) &&
               Objects.equals(this.fileName, that.fileName) &&
               Objects.equals(this.url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(englishName, arabicName, fileName, url);
    }

    @Override
    public String toString() {
        return "Muezzin[" +
               "englishName=" + englishName + ", " +
               "arabicName=" + arabicName + ", " +
               "fileName=" + fileName + ", " +
               "url=" + url + ']';
    }


}
