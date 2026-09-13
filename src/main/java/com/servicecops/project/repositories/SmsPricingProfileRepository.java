package com.servicecops.project.repositories;

import com.servicecops.project.models.database.SmsPricingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SmsPricingProfileRepository extends JpaRepository<SmsPricingProfile, Long>, JpaSpecificationExecutor<SmsPricingProfile> {
    Optional<SmsPricingProfile> findByProfileCodeIgnoreCase(String profileCode);
    boolean existsByProfileCodeIgnoreCase(String profileCode);
    Optional<SmsPricingProfile> findFirstByIsDefaultTrueAndIsActiveTrue();
    Optional<SmsPricingProfile> findFirstByIsActiveTrueOrderByMinimumSmsVolumeAsc();
    List<SmsPricingProfile> findAllByIsActiveTrueOrderByMinimumSmsVolumeAscProfileNameAsc();
    long countByIsActiveTrue();
    long countByIsActiveFalse();

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SmsPricingProfile p set p.isDefault = false where p.isDefault = true and p.id <> :id")
    void clearDefaultExcept(@Param("id") Long id);
}
