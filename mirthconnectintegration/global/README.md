# Setting Up Global Scripts in Mirth Connect

This guide explains how to set up global scripts in a local Mirth Connect installation. These global scripts are used across all channels to ensure consistent processing.

## Step 1: Import Global Scripts

1. Import `mirthconnectintegration/global/globalscripts.xml` in **Mirth Connect Administrator** → **Channels** → **Edit Global Scripts** → **Import Scripts**.

## Step 2: Configure System Environment Variables

1. All properties defined in `application.yml` should be set as **system environment variables**.
2. These variables should then be referenced in global scripts.

### Step 2.1: Add Environment Variables

- Add any new environment variables to `mirthconnectintegration/global/setEnv.bat`.
- Execute `setEnv.bat` to apply the variables to the system.

### Step 2.2: Update Application Configuration

- Update the newly added variables into `Packages.org.techbd.service.http.hub.prime.AppConfig`.
- Ensure that `AppConfig` is correctly populated in `update in deploy script`.

## Additional Updates

Any change in global scripts should also be updated to the respective js for versioning until an alternative or permanent version control system is integrated into Mirth Connect.

- mirthconnectintegration/global/deploy.js
- mirthconnectintegration/global/postprocessor.js
- mirthconnectintegration/global/preprocessor.js
- mirthconnectintegration/global/undeploy.js


