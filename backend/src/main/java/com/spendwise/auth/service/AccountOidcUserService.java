package com.spendwise.auth.service;

import com.spendwise.common.exception.ApiException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import java.util.Collection;
import java.util.Map;
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
            return new SpendwiseOidcUser(user, accountService.registerGoogleAccount(user));
        } catch (ApiException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(exception.getCode()), exception.getMessage(), exception);
        }
    }

    private record SpendwiseOidcUser(OidcUser delegate, String accountId) implements OidcUser {
        @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
        @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
        @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
        @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
        @Override public String getName() { return accountId; }
    }
}
