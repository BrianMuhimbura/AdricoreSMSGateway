package com.servicecops.project.repositories;

import com.servicecops.project.models.database.ApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {
}

