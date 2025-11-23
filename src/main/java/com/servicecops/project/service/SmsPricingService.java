package com.servicecops.project.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SmsPricingProfile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SmsPricingService {
    public double calculateSmsCost(Partner partner, String countryCode) {
        SmsPricingProfile profile = partner.getSmsPricingProfile();
        if (profile == null || profile.getRules() == null) {
            throw new IllegalStateException("No pricing profile or rules attached to partner");
        }
        JSONObject rules = JSON.parseObject(profile.getRules());
        // Example: rules = {"UG": 10.0, "KE": 12.0}
        Double price = rules.getDouble(countryCode);
        if (price == null) {
            throw new IllegalArgumentException("No SMS price defined for country: " + countryCode);
        }
        return price;
    }
}

