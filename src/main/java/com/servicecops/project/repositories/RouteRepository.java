package com.servicecops.project.repositories;

import com.servicecops.project.models.database.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {
}

