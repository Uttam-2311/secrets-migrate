# secrets-migrate

This project is about migrating secrets from google secret manager of default project to other project based on the organization.

## Setup

add an env variable GOOGLE_APPLICATION_CREDENTIALS of a service account's json file in your system which has access to all the projects.

For creation of a google's service account, refer this [doc](https://cloud.google.com/secret-manager/docs/reference/libraries#cloud-console). 

