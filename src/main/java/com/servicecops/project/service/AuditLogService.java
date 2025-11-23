package com.servicecops.project.service;

import com.servicecops.project.models.database.AuditLog;
import com.servicecops.project.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public List<AuditLog> findAll() { return repository.findAll(); }
    public Optional<AuditLog> findById(UUID id) { return repository.findById(id); }
    public AuditLog save(AuditLog log) { return repository.save(log); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

