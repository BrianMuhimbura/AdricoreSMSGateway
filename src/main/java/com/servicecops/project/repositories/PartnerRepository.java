package com.servicecops.project.repositories;

import com.servicecops.project.models.database.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    Optional<Partner> findByPartnerCode(String partnerCode);
}

