package com.bayoumi.services.reminders;


import com.bayoumi.util.LoggerWrapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ReminderUtil {
    private static ReminderUtil instance = null;
    private final List<Reminder> reminderList;
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(ReminderUtil.class);


    private ReminderUtil() {
        reminderList = new ArrayList<>();
    }


    public static ReminderUtil getInstance() {
        if (instance == null) {
            instance = new ReminderUtil();
        }
        return instance;
    }

    public void add(Reminder reminder) {
        reminderList.add(reminder);
    }

    public void addAll(List<Reminder> reminders) {
        reminderList.addAll(reminders);
    }

    public void validate(Date date) {
        reminderList.forEach(reminder -> {
            if (isEqualIgnoreMillis(date, reminder.getDate())) {
                LOGGER.info("reminder: " + reminder);
                reminder.getCallback().run();
            }
        });
    }

    public void clear() {
        reminderList.clear();
    }

    private boolean isEqualIgnoreMillis(Date a, Date b) {
        return ((a.getTime() / 1000) * 1000) == ((b.getTime() / 1000) * 1000);
    }
}
