package org.techbd.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.conf.Configuration;
import org.techbd.config.Constants;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class FHIRUtil {

    private final AppConfig appConfig;

    public static Map<String, String> PROFILE_MAP;
    private static String BASE_FHIR_URL;
    public static final String BUNDLE = "bundle";
    private static final Logger LOG = LoggerFactory.getLogger(FHIRUtil.class);
    public FHIRUtil(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostConstruct
    private void init() {
        BASE_FHIR_URL = appConfig.getBaseFHIRURL();
        PROFILE_MAP = appConfig.getStructureDefinitionsUrls() != null
                ? Collections.unmodifiableMap(appConfig.getStructureDefinitionsUrls())
                : Collections.emptyMap();
    }

    public static String getProfileUrl(String key) {
        return BASE_FHIR_URL + PROFILE_MAP.getOrDefault(key, "");
    }

    public static String getBundleProfileUrl() {
        return getProfileUrl(BUNDLE);
    }

    public static String getProfileUrl(String baseFhirUrl, String key) {
        return baseFhirUrl + PROFILE_MAP.getOrDefault(key, "");
    }

    public static List<String> getAllowedProfileUrls(AppConfig appConfig) {
        List<String> allowedProfileUrls = new ArrayList<>();
    
        if (appConfig.getIgPackages() != null && appConfig.getIgPackages().containsKey("fhir-v4")) {
            FhirV4Config fhirV4Config = appConfig.getIgPackages().get("fhir-v4");
            Map<String, Map<String, String>> shinNyPackages = fhirV4Config.getShinnyPackages();
    
            for (Map<String, String> igPackage : shinNyPackages.values()) {
                String profileBaseUrl = igPackage.getOrDefault("profile-base-url", "");
                String packageFhirProfileUrl = getProfileUrl(profileBaseUrl, BUNDLE);
                allowedProfileUrls.add(packageFhirProfileUrl);
            }
        } 
        return allowedProfileUrls;
    }

    public static void validateBaseFHIRProfileUrl(AppConfig appConfig, String baseFHIRProfileUrl) {
        if (StringUtils.isNotEmpty(baseFHIRProfileUrl)) {
            String profileUrl = getProfileUrl(baseFHIRProfileUrl, BUNDLE);
            List<String> allowedUrls = getAllowedProfileUrls(appConfig);
    
            if (!allowedUrls.contains(profileUrl)) {
                String supportedUrls = String.join(", ", allowedUrls);
                throw new IllegalArgumentException("Invalid Base FHIR profile URL provided : " + baseFHIRProfileUrl +
                        " in header 'X-TechBD-Base-FHIR-URL' . Supported  SHIN-NY IG URLs: " + supportedUrls);
            }
        }
    }
    public static String extractBundleId(String json,String interactionId) {
        try {
            JsonNode rootNode = Configuration.objectMapper.readTree(json);
            if (!"Bundle".equals(rootNode.path("resourceType").asText())) {
                return "Bundle id not provided"; 
            }
            return rootNode.path("id").asText("Bundle id not provided");
        } catch (Exception e) {
            LOG.error("Exception fetching bundle Id for interactionId :  ",interactionId , e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    public static void addCookieAndHeadersToResponse(HttpServletResponse response, Map<String, Object> responseParameters,
            Map<String, String> requestParameters) {
        if (responseParameters.get(Constants.METRIC_COOKIE) != null) {
            response.addCookie((Cookie) responseParameters.get(Constants.METRIC_COOKIE));
        }
        if (responseParameters.get(Constants.HEADER) != null) {
            response.addHeader(Constants.HEADER, responseParameters.get(Constants.HEADER).toString());
        }
        if (requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME) != null) {
            response.addHeader(Constants.START_TIME_ATTRIBUTE,
                    requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME));
        }
        if (responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME) != null) {
            response.addHeader(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME,
                    responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME).toString());
        }
        if (responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME) != null) {
            response.addHeader(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME,
                    responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME).toString());
        }
        if (responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS) != null) {
            response.addHeader(Constants.OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS,
                    responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS).toString());
        }
    }
}