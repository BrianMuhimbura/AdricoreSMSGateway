package com.servicecops.project.services.auth;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.config.ApplicationConf;
import com.servicecops.project.config.JwtUtility;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.PartnerRepository;
import com.servicecops.project.repositories.SystemRoleRepository;
import com.servicecops.project.repositories.SystemUserRepository;
import com.servicecops.project.service.CreditAccountService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.services.mail.MailService;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class AuthService extends BaseWebActionsService {
    private static final String PARTNER_ADMIN = "PARTNER_ADMIN";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final Set<String> CURRENCIES = Set.of("UGX", "KES", "TZS", "RWF", "USD");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final ApplicationConf userDetailService;
    private final JwtUtility jwtUtility;
    private final PartnerRepository partnerRepository;
    private final SystemUserRepository systemUserRepository;
    private final SystemRoleRepository systemRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final PlatformTransactionManager transactionManager;
    private final CreditAccountService creditAccountService;

    @Value("${app.portal-url:http://127.0.0.1:4200}")
    private String portalUrl;

    private OperationReturnObject login(JSONObject request) {
        requires(request, "username", "password");
        String username = request.getString("username");
        String password = request.getString("password");

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        final SystemUserModel userDetails = userDetailService.loadUserByUsername(username);
        final String token = jwtUtility.generateToken(userDetails);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("user", toPublicUser(userDetails));

        OperationReturnObject res = new OperationReturnObject();
        String message = Boolean.TRUE.equals(userDetails.getMustChangePassword())
                ? "Please choose a new password to continue"
                : "Welcome back " + userDetails.getUsername();
        res.setReturnCodeAndReturnMessage(0, message);
        res.setReturnObject(response);
        return res;
    }

    protected OperationReturnObject signup(JSONObject request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> createPartnerAccount(request));
    }

    private OperationReturnObject createPartnerAccount(JSONObject request) {
        requires(request, "partnerName", "firstName", "lastName", "email");

        String partnerName = requiredText(request, "partnerName");
        String firstName = requiredText(request, "firstName");
        String lastName = requiredText(request, "lastName");
        String email = requiredText(request, "email").toLowerCase();
        String phone = optionalText(request, "phone");
        String currency = hasText(request, "currency")
                ? normalizeCurrency(requiredText(request, "currency"))
                : "UGX";

        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Enter a valid email");
        }
        if (systemUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        systemRoleRepository.findFirstByRoleCode(PARTNER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Partner signup is not available yet"));

        String username = allocateUsername(email);
        String temporaryPassword = generateTemporaryPassword();

        Partner partner = new Partner();
        partner.setName(partnerName);
        partner.setEmail(email);
        partner.setPhone(phone);
        partner.setCurrency(currency);
        partner.setStatus("active");
        partner.setPartnerCode(nextPartnerCode());
        partner.setCreatedAt(Instant.now());
        partner.setUpdatedAt(Instant.now());
        Partner savedPartner = partnerRepository.saveAndFlush(partner);
        creditAccountService.ensureFor(savedPartner);

        SystemUserModel user = SystemUserModel.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(temporaryPassword))
                .roleCode(PARTNER_ADMIN)
                .isActive(Boolean.TRUE)
                .mustChangePassword(Boolean.TRUE)
                .partnerId(savedPartner.getId())
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .build();
        systemUserRepository.saveAndFlush(user);

        mailService.sendTemplate(
                email,
                "Your Adricore SMS Gateway account",
                "signup-credentials",
                Map.of(
                        "firstName", firstName,
                        "partnerName", savedPartner.getName(),
                        "partnerCode", savedPartner.getPartnerCode(),
                        "username", username,
                        "temporaryPassword", temporaryPassword,
                        "loginUrl", portalUrl.replaceAll("/+$", "") + "/login",
                        "year", Year.now().getValue()
                )
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("username", username);
        payload.put("partnerCode", savedPartner.getPartnerCode());
        payload.put("partnerName", savedPartner.getName());

        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "Account created. Check your email for sign-in details.", payload);
        return res;
    }

    protected OperationReturnObject changePassword(JSONObject request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> updatePassword(request));
    }

    private OperationReturnObject updatePassword(JSONObject request) {
        requiresAuth();
        requires(request, "currentPassword", "newPassword");

        String currentPassword = request.getString("currentPassword");
        String newPassword = request.getString("newPassword");
        if (!StringUtils.hasText(currentPassword) || !StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("Current password and new password cannot be empty");
        }
        if (newPassword.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from the temporary password");
        }

        SystemUserModel current = authenticatedUser();
        SystemUserModel user = systemUserRepository.findById(current.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        user.setMustChangePassword(Boolean.FALSE);
        systemUserRepository.save(user);

        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "Password updated", toPublicUser(user));
        return res;
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "login" -> login(request);
            case "signup" -> signup(request);
            case "changePassword" -> changePassword(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private String nextPartnerCode() {
        Long sequence = partnerRepository.nextPartnerCodeValue();
        if (sequence == null) {
            throw new IllegalStateException("Unable to allocate a partner code");
        }
        return "ADRS" + sequence;
    }

    private String allocateUsername(String email) {
        String local = email.substring(0, email.indexOf('@')).toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (local.length() < 3) {
            local = "user" + local;
        }
        if (local.length() > 24) {
            local = local.substring(0, 24);
        }
        String candidate = local;
        int suffix = 1;
        while (systemUserRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = local + suffix;
        }
        return candidate;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private String normalizeCurrency(String currency) {
        String value = currency.trim().toUpperCase();
        if (!CURRENCIES.contains(value)) {
            throw new IllegalArgumentException("Unsupported currency");
        }
        return value;
    }

    private String requiredText(JSONObject request, String field) {
        String value = request.getString(field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field.replace("_", " ") + " cannot be empty");
        }
        return value.trim();
    }

    private String optionalText(JSONObject request, String field) {
        if (!hasText(request, field)) {
            return null;
        }
        return request.getString(field).trim();
    }

    private boolean hasText(JSONObject request, String field) {
        return request.containsKey(field) && StringUtils.hasText(request.getString(field));
    }

    private Map<String, Object> toPublicUser(SystemUserModel user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("firstName", user.getFirstName());
        data.put("lastName", user.getLastName());
        data.put("email", user.getEmail());
        data.put("roleCode", user.getRoleCode());
        data.put("isActive", user.getIsActive());
        data.put("partnerId", user.getPartnerId());
        data.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        return data;
    }
}
