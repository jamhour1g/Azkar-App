package com.azkar.data.mapping;

import com.azkar.data.entity.DatabaseHadithGrade;
import com.azkar.domain.model.HadithGrade;

public final class HadithGradeMapper {

    private HadithGradeMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static HadithGrade toHadithGrade(DatabaseHadithGrade hadithGrade) {
        return HadithGrade.valueOf(hadithGrade.name());
    }

    public static DatabaseHadithGrade fromHadithGrade(HadithGrade hadithGrade) {
        return DatabaseHadithGrade.valueOf(hadithGrade.name());
    }
}
