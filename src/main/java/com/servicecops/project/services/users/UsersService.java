package com.servicecops.project.services.users;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.SystemRoleModel;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.SystemRoleRepository;
import com.servicecops.project.repositories.SystemUserRepository;
import com.servicecops.project.service.PartnerService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import com.servicecops.project.utils.PagedQuery;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UsersService extends BaseWebActionsService {
    private static final String PARTNER_ADMIN = "PARTNER_ADMIN";
    private static final String API_USER = "API_USER";
    private static final Set<String> PARTNER_ROLES = Set.of(PARTNER_ADMIN, API_USER);
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Set<String> SORT_FIELDS = Set.of(
            "username", "firstName", "lastName", "email", "roleCode", "isActive", "createdAt", "lastLoggedInAt"
    );

    private final SystemUserRepository systemUserRepository;
    private final SystemRoleRepository systemRoleRepository;
    private final PartnerService partnerService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    protected OperationReturnObject create(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        requires(request, "username", "password", "email", "firstName", "lastName", "roleCode");

        String username = requiredText(request, "username");
        String password = requiredText(request, "password");
        String email = requiredText(request, "email").toLowerCase();
        String firstName = requiredText(request, "firstName");
        String lastName = requiredText(request, "lastName");
        String roleCode = requiredText(request, "roleCode");
        ensureRoleAllowed(roleCode, scopedPartnerId);
        UUID partnerId = resolvePartnerId(request, scopedPartnerId, roleCode);

        validatePassword(password);
        ensureRoleExists(roleCode);
        ensureUsernameAvailable(username, null);
        ensureEmailAvailable(email, null);

        SystemUserModel user = SystemUserModel.builder()
                .username(username)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .password(passwordEncoder.encode(password))
                .roleCode(roleCode)
                .isActive(request.containsKey("isActive") ? request.getBoolean("isActive") : Boolean.TRUE)
                .partnerId(partnerId)
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .build();

        SystemUserModel saved = systemUserRepository.save(user);
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "User created", toPublicUser(saved));
        return res;
    }

    @Transactional
    protected OperationReturnObject update(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        requires(request, "id");

        Long id = request.getLong("id");
        if (id == null) {
            throw new IllegalArgumentException("id cannot be empty");
        }

        SystemUserModel user = loadScopedUser(id, scopedPartnerId);

        if (hasText(request, "firstName")) {
            user.setFirstName(request.getString("firstName").trim());
        }
        if (hasText(request, "lastName")) {
            user.setLastName(request.getString("lastName").trim());
        }
        if (hasText(request, "username")) {
            String username = request.getString("username").trim();
            ensureUsernameAvailable(username, user.getId());
            user.setUsername(username);
        }
        if (hasText(request, "email")) {
            String email = request.getString("email").trim().toLowerCase();
            ensureEmailAvailable(email, user.getId());
            user.setEmail(email);
        }
        if (hasText(request, "roleCode")) {
            String roleCode = request.getString("roleCode").trim();
            ensureRoleAllowed(roleCode, scopedPartnerId);
            ensureRoleExists(roleCode);
            user.setRoleCode(roleCode);
            if (API_USER.equals(roleCode) && user.getPartnerId() == null) {
                user.setPartnerId(resolvePartnerId(request, scopedPartnerId, roleCode));
            }
        }
        if (hasText(request, "partnerId") && scopedPartnerId == null) {
            user.setPartnerId(resolvePartnerId(request, null, user.getRoleCode()));
        }
        if (request.containsKey("isActive") && request.get("isActive") != null) {
            user.setIsActive(request.getBoolean("isActive"));
        }
        if (hasText(request, "password")) {
            String password = request.getString("password");
            validatePassword(password);
            user.setPassword(passwordEncoder.encode(password));
        }

        SystemUserModel saved = systemUserRepository.save(user);
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "User updated", toPublicUser(saved));
        return res;
    }

    protected OperationReturnObject get(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        requires(request, "id");
        Long id = request.getLong("id");
        SystemUserModel user = loadScopedUser(id, scopedPartnerId);
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "User found", toPublicUser(user));
        return res;
    }

    protected OperationReturnObject list(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        PagedQuery.Request pageRequest = PagedQuery.from(request, "createdAt", -1);
        Specification<SystemUserModel> spec = userSpec(pageRequest.query(), request, scopedPartnerId);
        Page<SystemUserModel> page = systemUserRepository.findAll(
                spec,
                PagedQuery.toPageable(pageRequest, SORT_FIELDS, "createdAt")
        );

        Map<String, Object> payload = PagedQuery.payload(
                page.getContent().stream().map(this::toPublicUser).toList(),
                page,
                pageRequest
        );
        if (scopedPartnerId != null) {
            payload.put("totalCount", systemUserRepository.countByPartnerId(scopedPartnerId));
            payload.put("activeCount", systemUserRepository.countByPartnerIdAndIsActive(scopedPartnerId, Boolean.TRUE));
            payload.put("inactiveCount", systemUserRepository.countByPartnerIdAndIsActive(scopedPartnerId, Boolean.FALSE));
        } else {
            payload.put("totalCount", systemUserRepository.count());
            payload.put("activeCount", systemUserRepository.countByIsActive(Boolean.TRUE));
            payload.put("inactiveCount", systemUserRepository.countByIsActive(Boolean.FALSE));
        }
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "Users found", payload);
        return res;
    }

    protected OperationReturnObject roles(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        List<SystemRoleModel> roles = scopedPartnerId != null
                ? systemRoleRepository.findByRoleCodeInOrderByRoleNameAsc(PARTNER_ROLES)
                : systemRoleRepository.findAllByOrderByRoleNameAsc();
        List<Map<String, Object>> payload = roles.stream()
                .map(role -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("roleCode", role.getRoleCode());
                    data.put("roleName", role.getRoleName());
                    return data;
                })
                .toList();
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, "Roles found", payload);
        return res;
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "create" -> create(request);
            case "update" -> update(request);
            case "get" -> get(request);
            case "list" -> list(request);
            case "roles" -> roles(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private UUID authorizeAndScope() {
        requiresAuth();
        SystemUserModel current = authenticatedUser();
        can(current.getUsername(), "MANAGE_USERS");
        return current.getPartnerId();
    }

    private SystemUserModel loadScopedUser(Long id, UUID scopedPartnerId) {
        SystemUserModel user = systemUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (scopedPartnerId != null && !scopedPartnerId.equals(user.getPartnerId())) {
            throw new IllegalStateException("NOT AUTHORISED");
        }
        return user;
    }

    private UUID resolvePartnerId(JSONObject request, UUID scopedPartnerId, String roleCode) {
        if (scopedPartnerId != null) {
            return scopedPartnerId;
        }
        if (!API_USER.equals(roleCode)) {
            return null;
        }
        String raw = optionalText(request, "partnerId");
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("API User accounts must be linked to a partner");
        }
        UUID partnerId;
        try {
            partnerId = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid partnerId");
        }
        partnerService.findById(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Partner not found"));
        return partnerId;
    }

    private String optionalText(JSONObject request, String field) {
        if (!hasText(request, field)) {
            return null;
        }
        return request.getString(field).trim();
    }

    private void ensureRoleAllowed(String roleCode, UUID scopedPartnerId) {
        if (scopedPartnerId != null && !PARTNER_ROLES.contains(roleCode)) {
            throw new IllegalArgumentException("Partners can only assign Partner Admin or API User");
        }
    }

    private void validatePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
    }

    private void ensureRoleExists(String roleCode) {
        Optional<SystemRoleModel> role = systemRoleRepository.findFirstByRoleCode(roleCode);
        if (role.isEmpty()) {
            throw new IllegalArgumentException("Unknown role code: " + roleCode);
        }
    }

    private void ensureUsernameAvailable(String username, Long currentUserId) {
        SystemUserModel existing = systemUserRepository.findFirstByUsername(username);
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new IllegalArgumentException("Username already in use");
        }
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        Optional<SystemUserModel> existing = systemUserRepository.findFirstByEmail(email);
        if (existing.isPresent() && !existing.get().getId().equals(currentUserId)) {
            throw new IllegalArgumentException("Email already in use");
        }
    }

    private String requiredText(JSONObject request, String field) {
        String value = request.getString(field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field.replace("_", " ") + " cannot be empty");
        }
        return value.trim();
    }

    private boolean hasText(JSONObject request, String field) {
        return request.containsKey(field) && StringUtils.hasText(request.getString(field));
    }

    private Specification<SystemUserModel> userSpec(String query, JSONObject request, UUID partnerId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (partnerId != null) {
                predicates.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (request != null && request.containsKey("isActive") && request.get("isActive") != null) {
                predicates.add(cb.equal(root.get("isActive"), request.getBoolean("isActive")));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("firstName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("lastName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("roleCode"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
        data.put("createdAt", user.getCreatedAt());
        data.put("lastLoggedInAt", user.getLastLoggedInAt());
        data.put("partnerId", user.getPartnerId());
        data.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        return data;
    }
}
