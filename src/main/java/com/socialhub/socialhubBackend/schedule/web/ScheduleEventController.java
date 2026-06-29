package com.socialhub.socialhubBackend.schedule.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.AttachPostsRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.CreateScheduleEventRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleEventResponse;
import com.socialhub.socialhubBackend.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Schedule events: create, list, and attach posts (EXPLICIT or INTERVAL mode). */
@RestController
@RequestMapping("/api/v1/schedule-events")
@Tag(name = "Schedule events", description = "Group and schedule posts (two modes)")
public class ScheduleEventController {

    private final ScheduleService scheduleService;

    public ScheduleEventController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ApiResponse<List<ScheduleEventResponse>> list() {
        return ApiResponse.ok(scheduleService.list());
    }

    @PostMapping
    @Operation(summary = "Create a schedule event (EXPLICIT or INTERVAL)")
    public ApiResponse<ScheduleEventResponse> create(
            @Valid @RequestBody CreateScheduleEventRequest request) {
        return ApiResponse.ok(scheduleService.create(request), "Schedule event created");
    }

    @PostMapping("/{id}/posts")
    @Operation(summary = "Attach posts and apply the schedule (sets them SCHEDULED)")
    public ApiResponse<ScheduleEventResponse> attachPosts(
            @PathVariable Long id, @Valid @RequestBody AttachPostsRequest request) {
        return ApiResponse.ok(scheduleService.attachPosts(id, request), "Posts scheduled");
    }
}
