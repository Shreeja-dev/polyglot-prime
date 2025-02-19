# Setting Up FlatFile CSV Channel

## Step 1: Set Environment Variables
- Run `mirthconnectintegration/global/setEnv.bat` to configure environment variables.
- Follow the steps in `mirthconnectintegration/global/README.md` to ensure all global environment variables are set.

## Step 2: Use Latest Code
- Clone the latest changes from the repository:
  
  [Polyglot Prime - Feature Branch](https://github.com/Shreeja-dev/polyglot-prime/tree/feature/mirth-phase1-java)

## Step 3: Import FlatFile CSV Channel
- Import the channel XML file:
  
  ```sh
  /home/shreeja/workspaces/github.com/Shreeja-dev/polyglot-prime/mirthconnectintegration/channels/flatfile/FlatFileCsv.xml
  ```

## Step 4: Copy Required Support Files

### Copy JSON Specification File
- Copy `support/specifications/flat-file/datapackage-nyher-fhir-ig-equivalent.json` to:
  
  ```sh
  D:/techbyDesign/specification/flat-file
  ```

### Copy Python Validation Script
- Copy `support/specifications/flat-file/validate-nyher-fhir-ig-equivalent.py` to:
  
  ```sh
  D:/techbyDesign/specification/flat-file
  ```

### Copy IG Packages
- Copy `hub-prime/src/main/resources/ig-packages` to:
  
  ```sh
  D:/techbyDesign/shinny-artifacts/ig-packages
  ```

Following these steps will ensure the FlatFile CSV channel is set up correctly in your Mirth Connect environment.

