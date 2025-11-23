/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.servicecops.project.services.sms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.servicecops.project.config.AfricasTalkingProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AfricasTalkingSmsService {

    private final RestTemplate restTemplate;
    private final AfricasTalkingProperties properties;

    @Data
    public static class SmsRequest {

        @JsonProperty("username")
        private String username;

        @JsonProperty("to")
        private String to;

        @JsonProperty("message")
        private String message;

        @JsonProperty("from")
        private String from;
    }

    @Data
    public static class SmsResponse {

        @JsonProperty("SMSMessageData")
        private SmsMessageData smsMessageData;
    }

    @Data
    public static class SmsMessageData {

        @JsonProperty("Message")
        private String message;

        @JsonProperty("Recipients")
        private List<Recipient> recipients;
    }

    @Data
    public static class Recipient {

        @JsonProperty("number")
        private String number;

        @JsonProperty("status")
        private String status;

        @JsonProperty("messageId")
        private String messageId;

        @JsonProperty("cost")
        private String cost;
    }

    public boolean sendSms(String phoneNumber, String message) {
        if (!StringUtils.hasText(phoneNumber) || !StringUtils.hasText(message)) {
            log.error("Phone number or message empty - aborting sendSms");
            return false;
        }
        try {
            String apiKey = properties.getApiKey();
            String username = properties.getUsername();
            String senderId = properties.getSenderId();
            String endpoint = properties.getEndpoint();

            log.info("Africa's Talking configuration: username='{}', senderId='{}', endpoint='{}'", username, senderId, endpoint);
            if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(username) || !StringUtils.hasText(endpoint)) {
                log.error("Africa's Talking properties not fully configured (apiKey/username/endpoint missing)");
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("apiKey", apiKey);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("username", username);
            formData.add("to", phoneNumber);
            formData.add("message", message);
            if (StringUtils.hasText(senderId)) {
                formData.add("from", senderId);
            }

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);
            log.info("Sending Africa's Talking SMS: to='{}' messageLength={} formData={} headers=[Content-Type={}, apiKey=***{}]", phoneNumber, message.length(), formData, headers.getContentType(), apiKey.substring(Math.max(0, apiKey.length() - 4)));

            ResponseEntity<SmsResponse> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, SmsResponse.class);
            HttpStatusCode status = response.getStatusCode();
            log.info("Africa's Talking response status: {}", status);
            if (status.is2xxSuccessful() && response.getBody() != null && response.getBody().getSmsMessageData() != null) {
                SmsMessageData data = response.getBody().getSmsMessageData();
                if (data.getRecipients() == null || data.getRecipients().isEmpty()) {
                    log.error("No recipients returned in SMS response");
                    return false;
                }
                boolean allSuccess = true;
                for (Recipient r : data.getRecipients()) {
                    log.info("Recipient number='{}' status='{}' messageId='{}' cost='{}'", r.getNumber(), r.getStatus(), r.getMessageId(), r.getCost());
                    if (!"Success".equalsIgnoreCase(r.getStatus())) {
                        allSuccess = false;
                    }
                }
                if (allSuccess) {
                    log.info("SMS dispatch successful to {}", phoneNumber);
                } else {
                    log.warn("One or more recipients reported failure for phone {}", phoneNumber);
                }
                return allSuccess;
            } else {
                log.error("Non-success HTTP status from Africa's Talking: {} body={}", status, response.getBody());
                return false;
            }
        } catch (HttpClientErrorException e) {
            log.error("HTTP error sending SMS: status={} body={} message={}", e.getStatusCode(), e.getResponseBodyAsString(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error sending SMS to {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }
}
