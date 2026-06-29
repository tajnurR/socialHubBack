package com.socialhub.socialhubBackend.schedule.domain;

/** How a schedule event assigns publish times to its posts. */
public enum ScheduleMode {
    /** A specific date/time is given per post. */
    EXPLICIT,
    /** Posts publish every {@code intervalHours} starting at {@code startTime}. */
    INTERVAL
}
