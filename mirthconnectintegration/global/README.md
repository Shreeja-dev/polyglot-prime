# Setting Up Global Scripts in Mirth Connect

This guide explains how to set up global scripts in a local Mirth Connect installation. These global scripts are used across all channels to ensure consistent processing.

## Step 1: Copy Preprocessor Script

1. Navigate to **Mirth Connect Administrator** → **Channels** → **Edit Global Scripts**.
2. From the **drop-down menu**, choose **Deploy**.
3. Copy and paste the contents of `mirthconnectintegration/global/preprocessor.js`.

Similarly, copy `mirthconnectintegration/global/preprocessor.js` to:

- **Mirth Connect Administrator** → **Channels** → **Edit Global Scripts** → **Choose Preprocessor**

## Step 2: Add Deployment and Postprocessor Scripts

If there are any **undeploy** or **postprocess** scripts, copy them from `mirthconnectintegration/global` and paste them into the **Administrator Console** under the respective script sections.

### Deployment Script
Copy `mirthconnectintegration/global/deploy.js` into:

- **Mirth Connect Administrator** → **Channels** → **Edit Global Scripts** → **Choose Deploy**

## Step 3: Configure System Environment Variables

1. All properties defined in `application.yml` should be set as **system environment variables**.
2. These variables should then be referenced in global scripts.

### Step 3.1: Add Environment Variables

- Add any new environment variables to `mirthconnectintegration/global/setEnv.bat`.
- Execute `setEnv.bat` to apply the variables to the system.

### Step 3.2: Update Application Configuration

- Update the newly added variables into `Packages.org.techbd.service.http.hub.prime.AppConfig`.
- Ensure that `AppConfig` is correctly populated in `mirthconnectintegration/global/deploy.js`.

## Summary
Following these steps ensures that Mirth Connect's global scripts are correctly configured and available in all channels, enhancing consistency and efficiency.

