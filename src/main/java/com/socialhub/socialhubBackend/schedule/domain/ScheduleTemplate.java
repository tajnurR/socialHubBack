package com.socialhub.socialhubBackend.schedule.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "schedule_templates")
public class ScheduleTemplate extends TenantBaseEntity {

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

    @Column(name = "schedule_type", nullable = false, length = 30)
    private String scheduleType;

    @Column(name = "days_of_week", length = 100)
    private String daysOfWeek;

    @Column(name = "posting_time", nullable = false)
    private LocalTime postingTime;

    @Column(nullable = false, length = 100)
    private String timezone;

    @Column(name = "daily_post_limit")
    private Integer dailyPostLimit;

    @Column(name = "notify_success", nullable = false)
    private boolean notifySuccess = true;

    @Column(name = "notify_failure", nullable = false)
    private boolean notifyFailure = true;

    @Column(name = "notify_next_reminder", nullable = false)
    private boolean notifyNextReminder = true;
}

