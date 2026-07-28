package com.azkar.i18n;

public final class Keys {

    private Keys() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final class Header {
        private Header() {}
        public static final String TITLE = "title";
        public static final String AZKAR = "azkar";
        public static final String YOUR_COMPANION = "yourCompanion";
        public static final String AZKAR_LIBRARY = "azkarLibrary";
        public static final String HOME = "home";
    }

    public static final class Main {
        private Main() {}
        public static final String GREETING_TITLE = "greetingTitle";
        public static final String GREETING_DATE = "greetingDate";
        public static final String DAILY_HADITH = "dailyHadith";
        public static final String READ_MORE = "readMore";
        public static final String DAILY_HADITH_DETAILS_TITLE = "dailyHadithDetailsTitle";
        public static final String DAILY_HADITH_DETAILS_HEADER = "dailyHadithDetailsHeader";
        public static final String DAILY_HADITH_DETAILS_ARABIC = "dailyHadithDetailsArabic";
        public static final String DAILY_HADITH_DETAILS_ENGLISH = "dailyHadithDetailsEnglish";
        public static final String DAILY_HADITH_DETAILS_SOURCE = "dailyHadithDetailsSource";
        public static final String HADITH_EXAMPLE = "hadithExample";
        public static final String EXPLANATION_EXAMPLE = "explanationExample";
        public static final String SOURCE_EXAMPLE = "sourceExample";
    }

    public static final class PrayerTimes {
        private PrayerTimes() {}
        public static final String TODAY_PRAYER_TIMES = "todayPrayerTimes";
        public static final String NEXT_PRAYER_IN = "nextPrayerIn";
        public static final String NEXT_PRAYER_LABEL = "nextPrayerLabel";
        public static final String PRAYER_TIMES_LOCATION = "prayerTimesLocation";
        public static final String PRAYER_TIME_REMAINING = "prayerTimeRemaining";
        public static final String PRAYER_NOTIFICATION = "prayerNotification";
        public static final String FAJR = "fajr";
        public static final String FAJR_TIME = "fajrTime";
        public static final String FAJR_VALUE = "fajrValue";
        public static final String DHUHR = "dhuhr";
        public static final String DHUHR_TIME = "dhuhrTime";
        public static final String DHUHR_VALUE = "dhuhrValue";
        public static final String ASR = "asr";
        public static final String ASR_TIME = "asrTime";
        public static final String ASR_VALUE = "asrValue";
        public static final String MAGHRIB = "maghrib";
        public static final String MAGHRIB_TIME = "maghribTime";
        public static final String MAGHRIB_VALUE = "maghribValue";
        public static final String ISHA = "isha";
        public static final String ISHA_TIME = "ishaTime";
        public static final String ISHA_VALUE = "ishaValue";
    }

    public static final class Favorites {
        private Favorites() {}
        public static final String FAVORITE_AZKAR = "favoriteAzkar";
        public static final String FAVORITE_AZKAR_ARABIC = "favoriteAzkarArabic";
        public static final String FAVORITE_AZKAR_ENGLISH = "favoriteAzkarEnglish";
        public static final String FAVORITE_AZKAR_SOURCE = "favoriteAzkarSource";
        public static final String FAVORED = "favorited";
        public static final String FAVORITE_PREV_LABEL = "favoritePrevLabel";
        public static final String FAVORITE_NEXT_LABEL = "favoriteNextLabel";
    }

    public static final class Library {
        private Library() {}

        public static final String TITLE = "libraryTitle";
        public static final String SUBTITLE = "librarySubtitle";
        public static final String ADD_NEW = "libraryAddNew";
        public static final String SEARCH_PROMPT = "librarySearchPrompt";

        public static final String FILTER_MORNING = "libraryFilterMorning";
        public static final String FILTER_EVENING = "libraryFilterEvening";
        public static final String FILTER_TRAVEL = "libraryFilterTravel";

        public static final String TAB_ALL = "libraryTabAll";
        public static final String TAB_MORNING = "libraryTabMorning";
        public static final String TAB_EVENING = "libraryTabEvening";
        public static final String TAB_FAVORITES = "libraryTabFavorites";

        public static final String VIEW_LIST = "libraryViewList";
        public static final String VIEW_GRID = "libraryViewGrid";

        public static final String RESULT_COUNT_PREFIX = "libraryResultCountPrefix";
        public static final String SORT_LABEL = "librarySortLabel";
        public static final String SORT_RECENT = "librarySortRecent";
        public static final String SORT_CATEGORY = "librarySortCategory";
        public static final String SORT_SOURCE = "librarySortSource";
        public static final String SORT_ARABIC = "librarySortArabic";
        public static final String SORT_TEXT = "librarySortText";

        public static final String COLLECTION_ALL = "libraryCollectionAll";
        public static final String COLLECTION_FAVORITES = "libraryCollectionFavorites";

        public static final String COL_CATEGORY = "libraryColCategory";
        public static final String COL_ARABIC = "libraryColArabic";
        public static final String COL_ENGLISH = "libraryColEnglish";
        public static final String COL_SOURCE = "libraryColSource";
        public static final String COL_ACTIONS = "libraryColActions";

        public static final String ACTION_READ = "libraryActionRead";
        public static final String ACTION_FAVORITE = "libraryActionFavorite";
        public static final String ACTION_UNFAVORITE = "libraryActionUnfavorite";
        public static final String ACTION_ADD_COLLECTION = "libraryActionAddCollection";

        public static final String PREV_PAGE = "libraryPrevPage";
        public static final String NEXT_PAGE = "libraryNextPage";
        public static final String GRID_PAGE = "libraryGridPage";

        public static final String DETAIL_TITLE = "libraryDetailTitle";
        public static final String DETAIL_EMPTY = "libraryDetailEmpty";

        public static final String CATEGORY_MORNING = "libraryCategoryMorning";
        public static final String CATEGORY_EVENING = "libraryCategoryEvening";
        public static final String CATEGORY_TRAVEL = "libraryCategoryTravel";
        public static final String CATEGORY_UNCATEGORIZED = "libraryCategoryUncategorized";

        public static final String SOURCE_LABEL = "librarySourceLabel";
        public static final String SOURCE_UNKNOWN = "librarySourceUnknown";
        public static final String SOURCE_MORNING = "librarySourceMorning";
        public static final String SOURCE_EVENING = "librarySourceEvening";
        public static final String SOURCE_TRAVEL = "librarySourceTravel";

        public static final String MISSING_TEXT = "libraryMissingText";

        public static final String FAVORITES_EMPTY_TITLE = "libraryFavoritesEmptyTitle";
        public static final String FAVORITES_EMPTY_HINT = "libraryFavoritesEmptyHint";

        public static final String REFRESH = "libraryRefresh";
        public static final String TOGGLE_FAVORITES_ONLY = "libraryToggleFavoritesOnly";
        public static final String TOGGLE_FAVORITES_ONLY_ON = "libraryToggleFavoritesOnlyOn";

        public static final String LOADING = "libraryLoading";
        public static final String LOAD_FAILED = "libraryLoadFailed";
        public static final String INFO_REFRESHED_AT = "libraryInfoRefreshedAt";

        public static final String DIALOG_COLLECTION_TITLE = "libraryDialogCollectionTitle";
        public static final String DIALOG_COLLECTION_HEADER = "libraryDialogCollectionHeader";
        public static final String DIALOG_COLLECTION_PROMPT = "libraryDialogCollectionPrompt";
        public static final String DIALOG_COLLECTION_PICK = "libraryDialogCollectionPick";
        public static final String DIALOG_COLLECTION_OR_CREATE = "libraryDialogCollectionOrCreate";
        public static final String DIALOG_ADD_TO_REMINDER_COLLECTION = "libraryDialogAddToReminderCollection";
        public static final String DIALOG_ADD_TO_REMINDER_FAVORITES = "libraryDialogAddToReminderFavorites";
        public static final String DIALOG_COLLECTION_VALIDATION_REQUIRED = "libraryDialogCollectionValidationRequired";

        public static final String READ_DIALOG_TITLE = "libraryReadDialogTitle";

        public static final class Reminder {
            private Reminder() {}

            public static final String TITLE = "libraryReminderTitle";
            public static final String SUBTITLE = "libraryReminderSubtitle";
            public static final String MODE_LABEL = "libraryReminderModeLabel";
            public static final String MODE_SINGLE = "libraryReminderModeSingle";
            public static final String MODE_COLLECTIONS = "libraryReminderModeCollections";
            public static final String MODE_CUSTOM = "libraryReminderModeCustom";
            public static final String SINGLE_ITEM_LABEL = "libraryReminderSingleItemLabel";
            public static final String PRIORITY_LABEL = "libraryReminderPriorityLabel";
            public static final String APPLY_SINGLE = "libraryReminderApplySingle";
            public static final String SINGLE_NOT_SET = "libraryReminderSingleNotSet";
            public static final String SINGLE_SELECTED_PREFIX = "libraryReminderSingleSelectedPrefix";
            public static final String COLLECTION_LABEL = "libraryReminderCollectionLabel";
            public static final String ADD_COLLECTION = "libraryReminderAddCollection";
            public static final String CUSTOM_LABEL = "libraryReminderCustomLabel";
            public static final String CUSTOM_PROMPT = "libraryReminderCustomPrompt";
            public static final String ADD_CUSTOM = "libraryReminderAddCustom";
            public static final String DELIVERY_LABEL = "libraryReminderDeliveryLabel";
            public static final String NO_SELECTIONS = "libraryReminderNoSelections";

            public static final String STATUS_SELECTION_UPDATED = "libraryReminderStatusSelectionUpdated";
            public static final String STATUS_COLLECTION_EMPTY = "libraryReminderStatusCollectionEmpty";
            public static final String STATUS_CUSTOM_NAME_REQUIRED = "libraryReminderStatusCustomNameRequired";
            public static final String STATUS_CUSTOM_NEEDS_SELECTION = "libraryReminderStatusCustomNeedsSelection";
            public static final String STATUS_LEGACY_DIALOG_SELECTION = "libraryReminderStatusLegacyDialogSelection";

            public static final String PRIORITY_HIGH = "libraryReminderPriorityHigh";
            public static final String PRIORITY_MEDIUM = "libraryReminderPriorityMedium";
            public static final String PRIORITY_LOW = "libraryReminderPriorityLow";

            public static final String SCHEDULE_BUTTON = "libraryScheduleButton";
            public static final String STOP_SCHEDULE_BUTTON = "libraryStopScheduleButton";

            public static final String COLLECTION_COUNT = "libraryReminderCollectionCount";
            public static final String FAVORITES_COUNT = "libraryReminderFavoritesCount";

            public static final String CUSTOM_NAME = "customName";
            public static final String PLAN_TITLE = "libraryReminderPlanTitle";

            public static final String CADENCE_EVERY_1_MINUTES = "libraryCadenceEvery1Minutes";
            public static final String CADENCE_EVERY_30_MINUTES = "libraryCadenceEvery30Minutes";
            public static final String CADENCE_EVERY_2_HOURS = "libraryCadenceEvery2Hours";
            public static final String CADENCE_DAILY = "libraryCadenceDaily";

            public static final String NOTIFICATION_TITLE = "libraryNotificationTitle";
            public static final String NOTIFICATION_FALLBACK = "libraryNotificationFallback";
        }

        public static final class Scheduler {
            private Scheduler() {}

            public static final String STATUS_IDLE = "librarySchedulerStatusIdle";
            public static final String STATUS_RUNNING = "librarySchedulerStatusRunning";
            public static final String STATUS_STOPPED = "librarySchedulerStatusStopped";
            public static final String STATUS_NO_SELECTION = "librarySchedulerStatusNoSelection";
            public static final String PREVIEW_EMPTY = "librarySchedulerPreviewEmpty";
            public static final String PREVIEW_PREFIX = "librarySchedulerPreviewPrefix";

            public static final String RANDOM_ORDER = "libraryRandomOrder";
            public static final String RANDOM_ORDER_ON = "libraryRandomOrderOn";
        }
    }

    public static final class Settings {
        private Settings() {}
        public static final String TITLE = "settingsTitle";
        public static final String LANGUAGE_HEADER = "settingsLanguageHeader";
        public static final String LANGUAGE_BODY = "settingsLanguageBody";
        public static final String LANGUAGE_ENGLISH = "settingsLanguageEnglish";
        public static final String LANGUAGE_ARABIC = "settingsLanguageArabic";
    }
}