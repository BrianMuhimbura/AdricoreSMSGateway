package com.servicecops.project.service;

import com.servicecops.project.models.database.Route;
import com.servicecops.project.repositories.RouteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RouteService {
    private final RouteRepository repository;

    public RouteService(RouteRepository repository) {
        this.repository = repository;
    }

    public List<Route> findAll() { return repository.findAll(); }
    public Optional<Route> findById(UUID id) { return repository.findById(id); }
    public Route save(Route route) { return repository.save(route); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

