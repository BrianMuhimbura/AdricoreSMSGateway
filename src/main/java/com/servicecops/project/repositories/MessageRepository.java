package com.servicecops.project.repositories;

import com.servicecops.project.models.database.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
}

