package com.servicecops.project.integrations.sasula.payments;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Data
@ConfigurationProperties(prefix = "sasula.payments")
public class SasulaPaymentsProperties {
    /**
     * MMBridge WSDL URL for the target environment.
     */
    private String wsdlUrl = "https://sasulatest.servicecops.com/sasulauat/MMBridgeWS?wsdl";
    private String channelCode = "";
    private String channelPassword = "";
    private String callbackUrl = "";
    /**
     * When true, SOAP is skipped. Pay now and status checks succeed locally with returnCode 0.
     */
    private boolean simulate = false;

    public String endpointUrl() {
        if (wsdlUrl == null || wsdlUrl.isBlank()) {
            throw new IllegalStateException("sasula.payments.wsdl-url is not configured");
        }
        String url = wsdlUrl.trim();
        int query = url.indexOf('?');
        return query > 0 ? url.substring(0, query) : url;
    }

    public void requireChannel() {
        if (simulate) {
            return;
        }
        if (!StringUtils.hasText(channelCode) || !StringUtils.hasText(channelPassword)) {
            throw new IllegalArgumentException("Mobile money payments are not configured. Set SASULA_CHANNEL_CODE and SASULA_CHANNEL_PASSWORD.");
        }
    }
}
