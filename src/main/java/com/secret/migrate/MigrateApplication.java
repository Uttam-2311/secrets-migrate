package com.secret.migrate;


import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import com.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MigrateApplication {

    // project Id where all the secrets are stored
    private static final String defaultProjectId = "project_id";
    //sample CSV file with ',' seperated value of orgName and projectId.
    private static final String sampleCsv = "sample.csv";


    public static void main(String[] args) throws Exception {
        MigrateApplication start = new MigrateApplication();
        start.quickstart(defaultProjectId, sampleCsv);
    }


    public void quickstart(String defaultProjectId, String filepath) throws Exception {
        List<Secret> secrets = start(defaultProjectId);
        migrateSecrets(secrets, filepath, defaultProjectId);
    }

    public List<Secret> start(String projectId) throws Exception {
        List<Secret> secretId = new ArrayList<>();

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretManagerServiceClient.ListSecretsPagedResponse pagedResponse = client.listSecrets(projectId);
            //add all the secrets into a list.
            pagedResponse
                    .iterateAll()
                    .forEach(
                            secret -> {
                                secretId.add(secret);
                                System.out.printf("Secret %s\n", secret.getName());
                            });
        }
        return secretId;
    }

    public void migrateSecrets(List<Secret> secretList, String fileName, String defaultProjectId) throws Exception {
        try {
            //read the csv file and create a mapping of orgname and projectId.
            Map<String, String> orgVsproId = getOrgtoProjectIDMapping(fileName);
            for (Secret secret : secretList) {
                try {
                    boolean global;
                    try {
                        String isGlobal = secret.getLabelsMap().get("global");
                        global = Boolean.getBoolean(isGlobal);
                    } catch (Exception ex) {
                        System.out.println("failed to fetch global label for secret key" + secret.getName());
                        global = false;
                    }

                    //only move non-global secrets.
                    if (!global) {
                        //from the secret split the name by '-' and take the first word.
                        String[] parts = secret.getName().split("-");
                        String orgName = parts[0];
                        //id the first word matched with orgName from csv move the secret to its respective project.
                        if (!StringUtils.isEmpty(orgVsproId.get(orgName))) {
                            move(orgVsproId.get(orgName), secret, defaultProjectId);
                        } else {
                            System.out.println("Cannot find a projectId for the orgName" + orgName);
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("failed to migrate the secret with key:" + secret.getName());
                    throw new RuntimeException(ex);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getOrgtoProjectIDMapping(String filepath) throws IOException {
        Map<String, String> orgVsProjectId = new HashMap<>();
        FileReader filereader = new FileReader(filepath);

        // create csvReader object passing
        // file reader as a parameter
        CSVReader csvReader = new CSVReader(filereader);
        String[] nextRecord;

        while ((nextRecord = csvReader.readNext()) != null) {
            String org = nextRecord[0];
            String proId = nextRecord[1];
            orgVsProjectId.put(org, proId);
        }
        return orgVsProjectId;
    }

    public void move(String projectId, Secret secret, String defaultProjectId) {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            //fetch the secret from the default project.
            String value = getSecret(secret.getName(), defaultProjectId);

            //create a new secret in the specified projectId .
            ProjectName projectName = ProjectName.of(projectId);
            client.createSecret(projectName, secret.getName(), buildSecret(secret.getLabelsMap()));

            SecretName secretName = SecretName.of(projectId, secret.getName());
            SecretPayload payload = SecretPayload.newBuilder()
                    .setData(ByteString.copyFrom(value.getBytes(StandardCharsets.UTF_8)))
                    .build();

            client.addSecretVersion(secretName, payload);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getSecret(String key, String projectId) {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, key, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return new String(response.getPayload().getData().toByteArray());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Secret buildSecret(Map<String, String> lables) {
        return Secret.newBuilder()
                .putAllLabels(lables)
                .setReplication(Replication.newBuilder()
                        .setAutomatic(Replication.Automatic.newBuilder().build())
                        .build())
                .build();
    }

}
