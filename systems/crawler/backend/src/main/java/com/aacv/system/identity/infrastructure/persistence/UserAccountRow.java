package com.aacv.system.identity.infrastructure.persistence;

import java.time.Instant;

public class UserAccountRow {

    private Long id;
    private String username;
    private String passwordHash;
    private String status;
    private Long version;
    private Instant credentialsChangedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    public void setCredentialsChangedAt(Instant credentialsChangedAt) {
        this.credentialsChangedAt = credentialsChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    private String realName;

    public String getRealName() { return realName; }

    public void setRealName(String value) { realName = value; }

    private String email;

    public String getEmail() { return email; }

    public void setEmail(String value) { email = value; }

    private String phone;

    public String getPhone() { return phone; }

    public void setPhone(String value) { phone = value; }

    private String organization;

    public String getOrganization() { return organization; }

    public void setOrganization(String value) { organization = value; }

    private String department;

    public String getDepartment() { return department; }

    public void setDepartment(String value) { department = value; }

    private String remark;

    public String getRemark() { return remark; }

    public void setRemark(String value) { remark = value; }

    private Long securityVersion;

    public Long getSecurityVersion() { return securityVersion; }

    public void setSecurityVersion(Long value) { securityVersion = value; }

}
