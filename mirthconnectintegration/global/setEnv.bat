@echo off
REM ============================================
REM Batch file to add all YAML properties as system environment variables.
REM All variable names are prefixed with TECHBD_
REM Run this script as Administrator.
REM ============================================

REM --- General Settings ---
call :SetVarIfNotExist "TECHBD_DEFAULT_DATALAKE_API_URL" "https://uzrlhp39e0.execute-api.us-east-1.amazonaws.com/dev/HRSNBundle"
call :SetVarIfNotExist "TECHBD_OPERATION_OUTCOME_HELP_URL" "https://techbd.org/get-help/"
call :SetVarIfNotExist "TECHBD_FHIR_VERSION" "r4"
call :SetVarIfNotExist "TECHBD_IG_VERSION" "1.3.0"
call :SetVarIfNotExist "TECHBD_BASE_FHIR_URL" "http://test.shinny.org"

REM --- CSV Validation Settings ---
call :SetVarIfNotExist "TECHBD_CSV_PYTHON_SCRIPT_PATH" "D:/techbyDesign/specification/flat-file/validate-nyher-fhir-ig-equivalent.py"
REM Edit and provide the full path to the Python executable as per your installation directory
call :SetVarIfNotExist "TECHBD_CSV_PYTHON_EXECUTABLE" "<path to python exe>/python.exe"
call :SetVarIfNotExist "TECHBD_CSV_PACKAGE_PATH" "D:/techbyDesign/specification/flat-file/datapackage-nyher-fhir-ig-equivalent.json"
call :SetVarIfNotExist "TECHBD_CSV_INBOUND_PATH" "D:/techbyDesign/flatfile/inbound"
call :SetVarIfNotExist "TECHBD_CSV_INGRESS_PATH" "D:/techbyDesign/flatfile/ingress"

REM --- Default DataLake API Authn Settings ---
call :SetVarIfNotExist "TECHBD_MTLS_STRATEGY" "no-mTls"

REM --- IG Packages (for fhir-v4) ---
REM Note: For shinNy, the YAML uses a placeholder for igVersion. Here we substitute with the default 1.3.0.
call :SetVarIfNotExist "TECHBD_IG_PACKAGES_FHIR_V4_SHIN_NY" "D:/techbyDesign/shinny-artifacts/ig-packages/shin-ny-ig/v1.3.0"
call :SetVarIfNotExist "TECHBD_IG_PACKAGES_FHIR_V4_US_CORE" "D:/techbyDesign/shinny-artifacts/ig-packages/fhir-v4/us-core/stu-7.0.0"
call :SetVarIfNotExist "TECHBD_IG_PACKAGES_FHIR_V4_SDOH" "D:/techbyDesign/shinny-artifacts/ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0"
call :SetVarIfNotExist "TECHBD_IG_PACKAGES_FHIR_V4_UV_SDC" "D:/techbyDesign/shinny-artifacts/ig-packages/fhir-v4/uv-sdc/stu-3.0.0"

REM --- Structure Definitions URLs ---
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_BUNDLE" "/StructureDefinition/SHINNYBundleProfile"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_PATIENT" "/StructureDefinition/shinny-patient"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_CONSENT" "/StructureDefinition/shinny-Consent"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_ENCOUNTER" "/StructureDefinition/shinny-encounter"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_ORGANIZATION" "/StructureDefinition/shin-ny-organization"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_OBSERVATION" "/StructureDefinition/shinny-observation-screening-response"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_QUESTIONNAIRE" "/StructureDefinition/shinny-questionnaire"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_PRACTITIONER" "/StructureDefinition/shin-ny-practitioner"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_QUESTIONNAIRERESPONSE" "/StructureDefinition/shinny-questionnaire"
call :SetVarIfNotExist "TECHBD_STRUCTURE_DEFINITIONS_URLS_OBSERVATION_SEXUAL_ORIENTATION" "/StructureDefinition/shinny-observation-sexual-orientation"

echo.
echo All TECHBD environment variables have been processed.
pause
goto :EOF

:SetVarIfNotExist
REM %1 = Variable Name, %2 = Value.
setlocal
set "VAR_NAME=%~1"
set "VAR_VALUE=%~2"

REM Check if the variable already exists under the system environment registry key.
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v %VAR_NAME% >nul 2>&1
if %errorlevel%==0 (
    echo %VAR_NAME% already exists. Skipping...
) else (
    echo Setting %VAR_NAME% to %VAR_VALUE%
    setx %VAR_NAME% "%VAR_VALUE%" /M
)
endlocal
goto :EOF
