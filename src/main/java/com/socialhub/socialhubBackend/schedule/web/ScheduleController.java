package com.socialhub.socialhubBackend.schedule.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.QuickPostActionRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ReschedulePostRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleTemplateRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleTemplateResponse;
import com.socialhub.socialhubBackend.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
@Tag(name = "Schedules", description = "Reusable content plans and their linked posts")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @Operation(summary = "List schedules for the current user")
    public ApiResponse<List<ScheduleResponse>> list() {
        return ApiResponse.ok(scheduleService.listSchedules());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one owned schedule")
    public ApiResponse<ScheduleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.getSchedule(id));
    }

    @PostMapping
    @Operation(summary = "Create a schedule and its linked posts")
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(scheduleService.createSchedule(request), "Schedule created");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a schedule and its linked posts")
    public ApiResponse<ScheduleResponse> update(
            @PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(scheduleService.updateSchedule(id, request), "Schedule updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a schedule")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ApiResponse.ok(null, "Schedule deleted");
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplicate a schedule as a draft")
    public ApiResponse<ScheduleResponse> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.duplicateSchedule(id), "Schedule duplicated");
    }

    @PostMapping("/{id}/toggle-pause")
    @Operation(summary = "Pause or resume a schedule")
    public ApiResponse<ScheduleResponse> togglePause(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.togglePause(id), "Schedule updated");
    }

    @PostMapping("/{scheduleId}/posts/{postId}/quick-action")
    @Operation(summary = "Apply a quick reschedule/skip/retry action to a schedule post")
    public ApiResponse<ScheduleResponse> quickAction(
            @PathVariable Long scheduleId,
            @PathVariable Long postId,
            @Valid @RequestBody QuickPostActionRequest request) {
        return ApiResponse.ok(scheduleService.quickPostAction(scheduleId, postId, request), "Post updated");
    }

    @PostMapping("/{scheduleId}/posts/{postId}/reschedule")
    @Operation(summary = "Move a schedule post to an exact time")
    public ApiResponse<ScheduleResponse> reschedule(
            @PathVariable Long scheduleId,
            @PathVariable Long postId,
            @Valid @RequestBody ReschedulePostRequest request) {
        return ApiResponse.ok(scheduleService.reschedulePost(scheduleId, postId, request), "Post rescheduled");
    }

    @GetMapping("/templates")
    @Operation(summary = "List built-in and custom schedule templates")
    public ApiResponse<List<ScheduleTemplateResponse>> templates() {
        return ApiResponse.ok(scheduleService.listTemplates());
    }

    @PostMapping("/templates")
    @Operation(summary = "Create a custom schedule template")
    public ApiResponse<ScheduleTemplateResponse> createTemplate(
            @Valid @RequestBody ScheduleTemplateRequest request) {
        return ApiResponse.ok(scheduleService.createTemplate(request), "Schedule template saved");
    }
}

