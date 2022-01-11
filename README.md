# secrets-migrate

This project is about migrating secrets from google secret manager of default project to other project's based on the organization.

## Setup
Need to create a google's service account, refer this [doc](https://cloud.google.com/secret-manager/docs/reference/libraries#cloud-console). 

Once service account is created, add its json key as an env variable GOOGLE_APPLICATION_CREDENTIALS in your system.
