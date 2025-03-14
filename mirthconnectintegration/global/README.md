# Setting Up Global Scripts in Mirth Connect

This guide explains how to set up global scripts in a local Mirth Connect installation. These global scripts are used across all channels to ensure consistent processing.

## Step 1: Import Global Scripts

1. Import `mirthconnectintegration/global/globalscripts.xml` in **Mirth Connect Administrator** → **Channels** → **Edit Global Scripts** → **Import Scripts**.


## Additional Updates

Any change in global scripts should also be updated to the respective js for versioning until an alternative or permanent version control system is integrated into Mirth Connect.

- mirthconnectintegration/global/deploy.js
- mirthconnectintegration/global/postprocessor.js
- mirthconnectintegration/global/preprocessor.js
- mirthconnectintegration/global/undeploy.js


