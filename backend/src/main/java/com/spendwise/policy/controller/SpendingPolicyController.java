package com.spendwise.policy.controller;

import com.spendwise.policy.dto.SpendingPolicyRequest;
import com.spendwise.policy.dto.SpendingPolicyResponse;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.service.SpendingPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies/active")
public class SpendingPolicyController {

    private final SpendingPolicyService service;

    public SpendingPolicyController(SpendingPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public SpendingPolicyResponse getActive(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return SpendingPolicyResponse.from(service.getActive(ownerId));
    }

    @PutMapping
    public SpendingPolicyResponse save(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody SpendingPolicyRequest request) {
        SpendingPolicy policy = service.save(ownerId, request);
        return SpendingPolicyResponse.from(policy);
    }
}
