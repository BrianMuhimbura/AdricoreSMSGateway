package com.servicecops.project.integrations.sasula.payments.dto;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "requestStatus")
@XmlEnum
public enum RequestStatus {
    PENDING,
    REQUESTED,
    SUCCESSFUL,
    FAILED
}
