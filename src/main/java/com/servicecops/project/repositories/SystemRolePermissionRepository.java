package com.servicecops.project.repositories;

import com.servicecops.project.models.database.SystemRolePermissionAssignmentModel;
import com.servicecops.project.models.jpahelpers.repository.JetRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface SystemRolePermissionRepository extends JetRepository<SystemRolePermissionAssignmentModel, Long> {
    Optional<SystemRolePermissionAssignmentModel> findFirstByRoleCodeAndPermissionCode(String roleCode, String permissionCode);
    Collection<SystemRolePermissionAssignmentModel> findAllByRoleCode(String roleCode);
}
