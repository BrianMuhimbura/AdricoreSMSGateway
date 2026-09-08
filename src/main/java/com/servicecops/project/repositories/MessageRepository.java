package com.servicecops.project.repositories;

import com.servicecops.project.models.database.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID>, JpaSpecificationExecutor<Message> {
    @EntityGraph(attributePaths = {"partner"})
    Page<Message> findAll(Specification<Message> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"partner"})
    Optional<Message> findWithPartnerById(UUID id);
}
