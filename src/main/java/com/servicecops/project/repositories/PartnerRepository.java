package com.servicecops.project.repositories;

import com.servicecops.project.models.database.Partner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID>, JpaSpecificationExecutor<Partner> {
    Optional<Partner> findByPartnerCode(String partnerCode);
    Optional<Partner> findByPartnerCodeIgnoreCase(String partnerCode);
    boolean existsByPartnerCode(String partnerCode);
    List<Partner> findAllByOrderByCreatedAtDesc();
    long countByParentPartner_IdAndStatusIgnoreCase(UUID parentPartnerId, String status);
    long countByStatusIgnoreCase(String status);
    List<Partner> findTop100ByStatusIgnoreCaseOrderByNameAsc(String status);

    @EntityGraph(attributePaths = {"organization", "parentPartner"})
    Page<Partner> findAll(Specification<Partner> spec, Pageable pageable);

    @Query(value = "select nextval('partner_code_seq')", nativeQuery = true)
    Long nextPartnerCodeValue();
}
