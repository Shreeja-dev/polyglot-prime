package org.techbd.config;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppConfig {

    @JsonProperty("version")
    private String version;
    
    @JsonProperty("defaultDatalakeApiUrl")
    private String defaultDatalakeApiUrl;
    
    @JsonProperty("operationOutcomeHelpUrl")
    private String operationOutcomeHelpUrl;
    
    @JsonProperty("structureDefinitionsUrls")
    private Map<String, String> structureDefinitionsUrls;
    
    @JsonProperty("baseFHIRURL")
    private String baseFHIRURL;
    
    @JsonProperty("defaultDataLakeApiAuthn")
    private DefaultDataLakeApiAuthn defaultDataLakeApiAuthn;
    
    @JsonProperty("fhirVersion")
    private String fhirVersion;
    
    @JsonProperty("igVersion")
    private String igVersion;
    
    @JsonProperty("csv")
    private CsvValidation csv;
    
    @JsonProperty("ig-packages")
    private Map<String, FhirV4Config> igPackages;

    @Getter
    @Setter
    public static class FhirV4Config {
        @JsonProperty("shinny-packages")
        private Map<String, Map<String, String>> shinnyPackages;
        
        @JsonProperty("base-packages")
        private Map<String, String> basePackages;
    }

    public record DefaultDataLakeApiAuthn(
            @JsonProperty("mTlsStrategy") String mTlsStrategy,
            @JsonProperty("mTlsAwsSecrets") MTlsAwsSecrets mTlsAwsSecrets,
            @JsonProperty("postStdinPayloadToNyecDataLakeExternal") PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal,
            @JsonProperty("mTlsResources") MTlsResources mTlsResources) {
    }

    public record MTlsResources(
            @JsonProperty("mTlsKeyResourceName") String mTlsKeyResourceName,
            @JsonProperty("mTlsCertResourceName") String mTlsCertResourceName) {
    }

    public record MTlsAwsSecrets(
            @JsonProperty("mTlsKeySecretName") String mTlsKeySecretName,
            @JsonProperty("mTlsCertSecretName") String mTlsCertSecretName) {
    }

    public record PostStdinPayloadToNyecDataLakeExternal(
            @JsonProperty("cmd") String cmd,
            @JsonProperty("timeout") int timeout) {
    }

    public record CsvValidation(
            @JsonProperty("validation") Validation validation) {
    }

    public record Validation(
            @JsonProperty("pythonScriptPath") String pythonScriptPath,
            @JsonProperty("pythonExecutable") String pythonExecutable,
            @JsonProperty("packagePath") String packagePath,
            @JsonProperty("outputPath") String outputPath,
            @JsonProperty("inboundPath") String inboundPath,
            @JsonProperty("ingessHomePath") String ingressHomePath) {
    }
}