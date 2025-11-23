package com.servicecops.project.services.sms;

import com.servicecops.project.config.AfricasTalkingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

public class AfricasTalkingSmsServiceTest {

    private RestTemplate restTemplate;
    private AfricasTalkingProperties properties;
    private AfricasTalkingSmsService service;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        properties = new AfricasTalkingProperties();
        properties.setApiKey("TEST_KEY");
        properties.setUsername("testUser");
        properties.setSenderId("SENDER");
        properties.setEndpoint("https://api.africastalking.com/version1/messaging");
        service = new AfricasTalkingSmsService(restTemplate, properties);
    }

    @Test
    void sendSms_success_allRecipients() {
        AfricasTalkingSmsService.Recipient recipient = new AfricasTalkingSmsService.Recipient();
        recipient.setNumber("+1234567890");
        recipient.setStatus("Success");
        recipient.setMessageId("msgid123");
        recipient.setCost("KES 1.0000");
        AfricasTalkingSmsService.SmsMessageData data = new AfricasTalkingSmsService.SmsMessageData();
        data.setMessage("Sent to 1/1 Total Cost: KES 1.0000");
        data.setRecipients(List.of(recipient));
        AfricasTalkingSmsService.SmsResponse responseBody = new AfricasTalkingSmsService.SmsResponse();
        responseBody.setSmsMessageData(data);
        ResponseEntity<AfricasTalkingSmsService.SmsResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(AfricasTalkingSmsService.SmsResponse.class)))
                .thenReturn(responseEntity);

        assertTrue(service.sendSms("+1234567890", "Test message"));
    }

    @Test
    void sendSms_partial_failure() {
        AfricasTalkingSmsService.Recipient r1 = new AfricasTalkingSmsService.Recipient();
        r1.setNumber("+111"); r1.setStatus("Success"); r1.setMessageId("id1"); r1.setCost("KES 1.0000");
        AfricasTalkingSmsService.Recipient r2 = new AfricasTalkingSmsService.Recipient();
        r2.setNumber("+222"); r2.setStatus("Failed" ); r2.setMessageId("id2"); r2.setCost("KES 0.0000");
        AfricasTalkingSmsService.SmsMessageData data = new AfricasTalkingSmsService.SmsMessageData();
        data.setMessage("Sent to 1/2 Total Cost: KES 1.0000");
        data.setRecipients(List.of(r1, r2));
        AfricasTalkingSmsService.SmsResponse body = new AfricasTalkingSmsService.SmsResponse();
        body.setSmsMessageData(data);
        ResponseEntity<AfricasTalkingSmsService.SmsResponse> resp = new ResponseEntity<>(body, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(AfricasTalkingSmsService.SmsResponse.class)))
                .thenReturn(resp);
        assertFalse(service.sendSms("+111,+222", "Test partial"));
    }

    @Test
    void sendSms_failure_httpError() {
        Mockito.when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(AfricasTalkingSmsService.SmsResponse.class)))
                .thenThrow(new RuntimeException("Network down"));
        assertFalse(service.sendSms("+1234567890", "Test message"));
    }

    @Test
    void sendSms_invalid_input() {
        assertFalse(service.sendSms("", "msg"));
        assertFalse(service.sendSms("+123", ""));
    }

    @Test
    void sendSms_missing_config() {
        AfricasTalkingProperties badProps = new AfricasTalkingProperties();
        AfricasTalkingSmsService badService = new AfricasTalkingSmsService(restTemplate, badProps);
        assertFalse(badService.sendSms("+123", "Hello"));
    }
}
