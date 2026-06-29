package com.socialhub.socialhubBackend.schedule.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Groups posts under a schedule and applies a {@link ScheduleMode}. Posts link
 * back via {@code Post.scheduleEventId}; attaching posts computes/sets their
 * {@code scheduledAt} and flips them to {@code SCHEDULED}.
 */
@Getter
@Setter
@Entity
@Table(name = "schedule_events")
public class ScheduleEvent extends TenantBaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 20)
    private String color;

    @Column(length = 500)
    private String platforms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleMode mode;

    /** Start time for INTERVAL mode. */
    @Column(name = "start_time")
    private Instant startTime;

    /** Hours between posts for INTERVAL mode. */
    @Column(name = "interval_hours")
    private Integer intervalHours;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "schedule_type", length = 30)
    private String scheduleType;

    @Column(name = "days_of_week", length = 100)
    private String daysOfWeek;

    @Column(name = "posting_time")
    private LocalTime postingTime;

    @Column(length = 100)
    private String timezone;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "daily_post_limit")
    private Integer dailyPostLimit;

    @Column(name = "notify_success", nullable = false)
    private boolean notifySuccess = true;

    @Column(name = "notify_failure", nullable = false)
    private boolean notifyFailure = true;

    @Column(name = "notify_next_reminder", nullable = false)
    private boolean notifyNextReminder = true;
}
