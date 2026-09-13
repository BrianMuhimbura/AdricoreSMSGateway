package com.servicecops.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Data
@ConfigurationProperties(prefix = "africas-talking")
public class AfricasTalkingProperties {
    /** API key provided by Africa's Talking */
    private String apiKey;
    /** Username (sandbox or production) */
    private String username;
    /** Optional registered sender id */
    private String senderId;
    /** Messaging endpoint, e.g. https://api.africastalking.com/version1/messaging */
    private String endpoint;
    /** Alias used in application properties (`africas-talking.api-url`). */
    private String apiUrl;
    private boolean enabled = true;

    public String messagingUrl() {
        if (StringUtils.hasText(endpoint)) {
            return endpoint.trim();
        }
        return StringUtils.hasText(apiUrl) ? apiUrl.trim() : null;
    }
}
