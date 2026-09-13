package com.servicecops.project.repositories;

import com.servicecops.project.models.database.SystemRoleModel;
import com.servicecops.project.models.jpahelpers.repository.JetRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemRoleRepository extends JetRepository<SystemRoleModel, Long> {
    Optional<SystemRoleModel> findFirstByRoleCode(String code);

    List<SystemRoleModel> findAllByOrderByRoleNameAsc();

    List<SystemRoleModel> findByRoleCodeInOrderByRoleNameAsc(Collection<String> roleCodes);
}
