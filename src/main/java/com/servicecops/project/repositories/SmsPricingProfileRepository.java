package com.servicecops.project.repositories;

import com.servicecops.project.models.database.SmsPricingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SmsPricingProfileRepository extends JpaRepository<SmsPricingProfile, UUID> {
}

