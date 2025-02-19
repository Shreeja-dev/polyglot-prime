# Mirth Connect Integration

## Overview
This document provides a step-by-step guide for integrating channel XML files and transformer scripts in Mirth Connect. It outlines the directory structure, file placement, and naming conventions to ensure a smooth integration process.

## Directory Structure
The Mirth Connect integration files are maintained in the following directory:
```
/home/shreeja/workspaces/github.com/Shreeja-dev/polyglot-prime/mirthconnectintegration
```
All Mirth Connect-related changes should be updated in this directory.

### 1. Channel-Specific Files
Each channel should have its own folder under `mirthconnectintegration/channels`.
For example, for the `flatfile` channel, we have:
```
mirthconnectintegration/channels/flatfile/validate    # For /flatfile/csv/Bundle/$validate
mirthconnectintegration/channels/flatfile/bundle      # For /flatfile/csv/Bundle
```
Place the channel-specific XML files directly in their respective folders.
Example:
```
mirthconnectintegration/channels/flatfile/validate/FlatFileCsv.xml
```

### 2. Transformer Scripts
Transformer scripts are divided into `source` and `destination` scripts.

#### a) Source Transformer Scripts
Source transformer scripts should be added under:
```
mirthconnectintegration/channels/<channel>/validate/source/transformerscripts
```
For example:
```
mirthconnectintegration/channels/flatfile/validate/source/transformerscripts/step1.js
mirthconnectintegration/channels/flatfile/validate/source/transformerscripts/step2.js
```
- Each step should have its own JavaScript file.
- Additional steps should be added in order of execution.

#### b) Destination Transformer Scripts
If required, add destination transformer scripts under:
```
mirthconnectintegration/channels/<channel>/validate/destination/transformerscripts
```

### 3. Other Channels
Follow the same folder structure for other channels:
```
mirthconnectintegration/channels/<channel>/source/transformerscripts
mirthconnectintegration/channels/<channel>/destination/transformerscripts
```
Example for another channel:
```
mirthconnectintegration/channels/fhir/bundle/source/transformerscripts
mirthconnectintegration/channels/fhir/bundle/destination/transformerscripts
mirthconnectintegration/channels/fhir/bundle/source/transformerscripts/step1.js
mirthconnectintegration/channels/fhir/bundle/source/transformerscripts/step2.js
mirthconnectintegration/channels/fhir/bundle/destination/transformerscripts/step1.js
mirthconnectintegration/channels/fhir/bundle/destination/transformerscripts/step2.js
```

## Configuration Changes
### 1. Mirth Connect Configuration
Any configuration changes made in the Mirth Connect installation folder should be added to:
```
mirthconnectintegration/config
```

### 2. Global Scripts
Any modifications to Mirth Connect's global scripts should be stored here:
```
mirthconnectintegration/global
```

## Best Practices
- Maintain proper naming conventions for transformer scripts.
- Keep steps modular and well-documented within scripts.
- Follow the directory structure consistently across all channels.
- Update XML files whenever channel configurations change.

By following this guide, the integration process with Mirth Connect will be streamlined and well-organized.

