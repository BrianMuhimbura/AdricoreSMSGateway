package com.servicecops.project.repositories;

import com.servicecops.project.models.database.*;
import com.servicecops.project.models.jpahelpers.repository.JetRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemUserRepository extends JetRepository<SystemUserModel, Long>, JpaSpecificationExecutor<SystemUserModel> {
    @Query("select u from SystemUserModel u where u.username = :username")
    SystemUserModel findFirstByUsername(@Param("username") String username);

    Optional<SystemUserModel> findFirstByUsernameOrEmail(String username, String email);
    Optional<SystemUserModel> findFirstByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByIsActive(Boolean isActive);
    long countByPartnerId(java.util.UUID partnerId);
    long countByPartnerIdAndIsActive(java.util.UUID partnerId, Boolean isActive);
    @Query("select u.id from SystemUserModel u where u.partnerId = :partnerId")
    java.util.List<Long> findIdsByPartnerId(@Param("partnerId") java.util.UUID partnerId);
}
