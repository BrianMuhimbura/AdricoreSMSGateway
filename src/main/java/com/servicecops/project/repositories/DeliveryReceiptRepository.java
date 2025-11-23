package com.servicecops.project.repositories;

import com.servicecops.project.models.database.DeliveryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, UUID> {
}

