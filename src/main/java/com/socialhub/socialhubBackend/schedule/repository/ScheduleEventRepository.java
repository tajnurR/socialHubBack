package com.socialhub.socialhubBackend.schedule.repository;

import com.socialhub.socialhubBackend.schedule.domain.ScheduleEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, Long> {

    List<ScheduleEvent> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(
            Long organizationId, Long userId);

    Optional<ScheduleEvent> findByIdAndOrganizationIdAndUserId(
            Long id, Long organizationId, Long userId);
}
