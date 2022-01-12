package com.secret.migrate;

import java.util.Map;

public class Credential {
    private String key;
    private String orgId;
    private String value;
    private boolean global;
    private Map<String,String> labels;

    public Credential() {
    }

    public Credential(String key, String orgId, String value, boolean global,Map<String,String> labels) {
        this.key = key;
        this.value = value;
        this.orgId = orgId;
        this.global = global;
        this.labels=labels;
    }


    public String getOrganizationId() {
        return this.orgId;
    }

    public void setOrganizationId(String orgId) {
        this.orgId = orgId;
    }

    public String getSecretKey() {
        return this.key;
    }

    public void setSecretKey(String secretKey) {
        this.key = secretKey;
    }

    public String getSecretValue() {
        return this.value;
    }

    public void setSecretValue(String secretValue) {
        this.value = secretValue;
    }

    public boolean isSecretGlobal() {
        return this.global;
    }

    public void setSecretGlobal(boolean global) {
        this.global = global;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String,String> labels){
        this.labels=labels;
    }
}
