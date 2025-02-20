# Setting Up FlatFile CSV Channel

## Step 1: Install Python3 and Verify Pip Installation

### Install Python3 on Windows
- Download and install the latest version of Python3 from the [official Python website](https://www.python.org/downloads/).
- During installation, ensure that you check the option **"Add Python to PATH"**.

### Verify Pip Installation
- Open Command Prompt and run the following command to verify pip installation:
  ```sh
  pip3 --version
  ```
  If pip is not installed, install it manually using:
  ```sh
  python -m ensurepip --default-pip
  ```

### Update System Environment Variables
Add the following paths to your system environment variables:
- `C:\Users\Shreeja Jobin\AppData\Local\Programs\Python\Python313\Scripts\`
- `C:\Users\Shreeja Jobin\AppData\Local\Programs\Python\Python313\`

## Step 2: Set Environment Variables
- Edit `setEnv.bat` to set `TECHBD_CSV_PYTHON_EXECUTABLE` to the full path of the Python executable:
  ```sh
  C:\Users\Shreeja Jobin\AppData\Local\Programs\Python\Python313\python.exe
  ```
- Run `mirthconnectintegration/global/setEnv.bat` to configure environment variables.
- Follow the steps in `mirthconnectintegration/global/README.md` to ensure all global environment variables are set.

## Step 3: Install Frictionless
After installing Python and updating system environment variables, install Frictionless using the following command:
  
```sh
pip install frictionless
```

## Step 4: Restart Your Machine
- Restart your computer to ensure all environment variables and configurations take effect.

## Step 5: Use Latest Code
- Clone the latest changes from the repository:
  
  [Polyglot Prime - Feature Branch](https://github.com/Shreeja-dev/polyglot-prime/tree/feature/mirth-phase1-java)

## Step 6: Import FlatFile CSV Channel
- Import the channel XML file:
  
  ```sh
  /home/shreeja/workspaces/github.com/Shreeja-dev/polyglot-prime/mirthconnectintegration/channels/flatfile/FlatFileCsv.xml
  ```

## Step 7: Copy Required Support Files

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

Following these steps will ensure the FlatFile CSV channel is set up correctly in your Mirth Connect environment with Python and Frictionless installed properly.

