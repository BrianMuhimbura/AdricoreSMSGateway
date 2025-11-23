package com.servicecops.project.repositories;

import com.servicecops.project.models.database.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
}

