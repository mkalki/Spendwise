package com.spendwise.auth.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @Column(length = 120, nullable = false)
    private String id;

    @Column(length = 320, nullable = false, unique = true)
    private String email;

    @Column(length = 32, unique = true)
    private String username;

    @Column(name = "password_hash", length = 200)
    private String passwordHash;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "picture_url", columnDefinition = "text")
    private String pictureUrl;

    @Column(name = "google_subject", length = 120, unique = true)
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private Provider provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    private UserAccount(
            String id,
            String email,
            String username,
            String passwordHash,
            String displayName,
            String pictureUrl,
            String googleSubject,
            Provider provider) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.pictureUrl = pictureUrl;
        this.googleSubject = googleSubject;
        this.provider = provider;
    }

    public static UserAccount passwordAccount(
            String id, String email, String username, String passwordHash) {
        return new UserAccount(id, email, username, passwordHash, username, null, null, Provider.PASSWORD);
    }

    public static UserAccount googleAccount(
            String subject, String email, String displayName, String pictureUrl) {
        return new UserAccount(
                subject, email, null, null, displayName, pictureUrl, subject, Provider.GOOGLE);
    }

    public void updateGoogleProfile(String email, String displayName, String pictureUrl) {
        this.email = email;
        this.displayName = displayName;
        this.pictureUrl = pictureUrl;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getPictureUrl() { return pictureUrl; }
    public String getGoogleSubject() { return googleSubject; }
    public Provider getProvider() { return provider; }

    public enum Provider {
        PASSWORD,
        GOOGLE
    }
}
