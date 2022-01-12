package com.secret.migrate;


import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import com.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


public class MigrateApplication {

    // project Id where all the secrets are stored
    private static final String defaultProjectId = "my_project";
    //sample CSV file with ',' separated value of orgName and projectId.
    private static final String sampleCsv = "src/main/resources/sample.csv";


    //Application constants
    private static final String LABEL_KEY_GLOBAL = "global";
    private static final String LABEL_KEY_TENANT = "tenant-name";
    private static final String LABEL_KEY_DISPLAYNAME = "display-name";

    public static void main(String[] args) throws Exception {

        MigrateApplication start = new MigrateApplication();
        SecretManagerServiceClient client = start.createClient();
        start.quickstart(client, defaultProjectId, sampleCsv);
    }

    public SecretManagerServiceClient createClient() throws IOException {
        return SecretManagerServiceClient.create();
    }

    public void quickstart(SecretManagerServiceClient client, String defaultProjectId, String filepath) throws Exception {
        List<Credential> secrets = getProjectSecrets(client, defaultProjectId);
        migrateSecrets(client, secrets, filepath);
    }

    /**
     *
      * @param client
     * @param projectId
     * @return
     * @throws Exception
     */

    public List<Credential> getProjectSecrets(SecretManagerServiceClient client, String projectId) throws Exception {

        try {
            List<Credential> creds = listSecrets(client, projectId).stream()
                    .filter(secret -> secret.getLabelsMap().containsKey(LABEL_KEY_DISPLAYNAME))
                    .map(s -> {
                        try {
                            Map<String, String> labels = s.getLabelsMap();
                            return getCred(client, s.getLabelsMap().get(LABEL_KEY_DISPLAYNAME), s.getLabelsMap().get(LABEL_KEY_TENANT), s.getLabelsMap().get(LABEL_KEY_GLOBAL), projectId, labels);

                        } catch (Exception e) {
                            System.out.println("failed to convert secrets to cred for this secret name: " + s.getName());
                            return null;
                        }
                    }).filter(Objects::nonNull).collect(Collectors.toList());
            return creds;
        } catch (Exception ex) {
            throw new RuntimeException("failed to fetch creds for project"+projectId,ex);

        }
    }

    /**
     *
     * @param client
     * @param secretList
     * @param fileName  path to a CSV file with ',' separated value of orgName and projectId.
     *
     * @throws Exception
     */
    public void migrateSecrets(SecretManagerServiceClient client, List<Credential> secretList, String fileName) throws Exception {
        try {
            //read the csv file and create a mapping of orgname and projectId.
            Map<String, String> orgVsproId = getOrgtoProjectIDMapping(fileName);
            for (Credential secret : secretList) {
                try {
                    boolean global;
                    try {
                        global = secret.isSecretGlobal();
                    } catch (Exception ex) {
                        System.out.println("failed to fetch global label for secret key" + secret.getSecretKey());
                        global = false;
                    }

                    //only move non-global secrets.
                    if (!global) {
                        String orgName = secret.getOrganizationId();
                        if (!StringUtils.isEmpty(orgVsproId.get(orgName))) {
                            move(client, orgVsproId.get(orgName), secret);
                        } else {
                            System.out.println("Cannot find a projectId for the orgName" + orgName);
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("failed to migrate the secret with key:" + secret.getSecretKey());
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

    /**
     *
     * @param client
     * @param projectId
     * @param secret
     */
    public void move(SecretManagerServiceClient client, String projectId, Credential secret) {
        try {

            //check if this secret already exists
            if (listSecrets(client, projectId).stream()
                    .filter(s -> secret.getSecretKey().equals(s.getLabelsMap().get(LABEL_KEY_DISPLAYNAME))
                            &&
                            ((secret.getOrganizationId() != null && secret.getOrganizationId().equals(s.getLabelsMap().get(LABEL_KEY_TENANT)))))
                    .findFirst().isPresent()) {
                throw new RuntimeException("Key with duplicate labels already exists");
            }

            //create a new secret in the specified projectId .
            ProjectName projectName = ProjectName.of(projectId);
            String id=getUid(secret.getSecretKey(), secret.getOrganizationId());
            client.createSecret(projectName, id, buildSecret(secret.getLabels()));

            SecretName secretName = SecretName.of(projectId, id);
            SecretPayload payload = SecretPayload.newBuilder()
                    .setData(ByteString.copyFrom(secret.getSecretValue().getBytes(StandardCharsets.UTF_8)))
                    .build();

            client.addSecretVersion(secretName, payload);

            System.out.println("Successfully migrated a secret :"+id);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    public String getSecret(SecretManagerServiceClient client, String key, String projectId) {
        try {
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

    private Credential getCred(SecretManagerServiceClient client, String key, String orgId, String global, String projectId, Map<String, String> labels) throws Exception {
        try {
            //get the secret value
            Optional<Credential> value = get(client, key, orgId, projectId);
            if (value.isPresent()) {
                value.get().setSecretGlobal(Boolean.parseBoolean(global));
                value.get().setLabels(labels);
                return value.get();
            } else {
                Credential cred = new Credential(key, orgId, "", Boolean.parseBoolean(global), labels);
                cred.setSecretKey(key);
                cred.setOrganizationId(orgId);
                cred.setSecretValue("");
                return cred;
            }
        } catch (Exception ex) {
            System.out.println("Requested secret {} does not exist or has no latest version" + key);
            throw new RuntimeException("Error in fetching the creds: " + ex.getMessage(), ex);
        }
    }

    public Optional<Credential> get(SecretManagerServiceClient client, String key, String orgId, String projectId) throws Exception {
        try {
            String value = getSecret(client, getUid(key, orgId), projectId);
            Credential cred = new Credential();
            cred.setSecretKey(key);
            cred.setSecretValue(value);
            if (orgId != null) {
                cred.setOrganizationId(orgId);
            }

            return Optional.of(cred);
        } catch (Exception e) {
            System.out.println("Error retrieving secret: " + key);
            throw new Exception(e.getMessage(), e);
        }
    }


    private List<Secret> listSecrets(SecretManagerServiceClient client, String projectId) {
        List<Secret> secrets = new ArrayList<>();

        client.listSecrets(ProjectName.of(projectId))
                .iterateAll()
                .forEach(s -> secrets.add(s));

        return secrets;
    }

    private String getUid(String key, String tenant) {
        if(tenant==null){
            System.out.println("Tenant name is null ");
            throw new RuntimeException("Tenant name cannot be null ");
        }
        return tenant != null ? String.format("%s-%s", tenant, key) : key;
    }
}
