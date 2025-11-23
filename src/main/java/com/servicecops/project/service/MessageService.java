package com.servicecops.project.service;

import com.servicecops.project.models.database.Message;
import com.servicecops.project.repositories.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessageService {
    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
    }

    public List<Message> findAll() { return repository.findAll(); }
    public Optional<Message> findById(UUID id) { return repository.findById(id); }
    public Message save(Message message) { return repository.save(message); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

