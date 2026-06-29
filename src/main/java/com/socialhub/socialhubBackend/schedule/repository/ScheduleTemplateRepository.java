package com.socialhub.socialhubBackend.schedule.repository;

import com.socialhub.socialhubBackend.schedule.domain.ScheduleTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {

    List<ScheduleTemplate> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(Long organizationId, Long userId);

    Optional<ScheduleTemplate> findByIdAndOrganizationIdAndUserId(Long id, Long organizationId, Long userId);
}

