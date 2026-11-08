package com.spendwise.auth.service;

import com.spendwise.common.exception.ApiException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class AccountOidcUserService extends OidcUserService {

    private final AccountService accountService;

    public AccountOidcUserService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(userRequest);
        try {
            accountService.registerGoogleAccount(user);
            return user;
        } catch (ApiException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(exception.getCode()), exception.getMessage(), exception);
        }
    }
}
