package org.techbd.config;

public interface Constants {

    public static final String PATIENT_ID="PATIENT_ID";
    public static final String ENCOUNTER_ID="ENCOUNTER_ID";
    public static final String ORGANIZATION_ID="ORGRANIZATION_ID";
    public static final String INTERPERSONAL_SAFETY_GROUP_QUESTIONS = "INTERPERSONAL_SAFETY_GROUP_QUESTIONS";
    public static final String AHC_SCREENING_GROUP_QUESTIONS = "AHC_SCREENING_GROUP_QUESTIONS";
    public static final String SUPPLEMENTAL_GROUP_QUESTIONS = "SUPPLEMENTAL_GROUP_QUESTIONS";
    public static final String TOTAL_SCORE_QUESTIONS = "TOTAL_SCORE_QUESTIONS";
    public static final String MENTAL_HEALTH_SCORE_QUESTIONS = "MENTAL_HEALTH_SCORE_QUESTIONS";
    public static final String PHYSICAL_ACTIVITY_SCORE_QUESTIONS = "PHYSICAL_ACTIVITY_SCORE_QUESTIONS";

    //Constants for request paramteres
    public static final String INTERACTION_ID="INTERACTION_ID";
    public static final String REQUEST_URI="REQUEST_URI";
    public static final String USER_AGENT="USER_AGENT";
    public static final String TENANT_ID="TENANT_ID";
    public static final String SFTP_SESSION_ID="SFTP_SESSION_ID";
    public static final String REQUESTED_SESSION_ID="REQUESTED_SESSION_ID";
    public static final String ORIGIN="ORIGIN";
    public static final String PREFIX = "X-TechBD-";
    public static final String HEALTH_CHECK_HEADER = Configuration.Servlet.HeaderName.PREFIX
    + "HealthCheck";
    public static final String GROUP_INTERACTION_ID = "GROUP_INTERACTION_ID";
    public static final String MASTER_INTERACTION_ID = "MASTER_INTERACTION_ID"; 
    public static final String REQUEST_URI_TO_BE_OVERRIDDEN = "REQUEST_URI_TO_BE_OVERRIDDEN";
    public static final String CUSTOM_DATA_LAKE_API = "CUSTOM_DATA_LAKE_API";
    public static final String DATA_LAKE_API_CONTENT_TYPE = "DATA_LAKE_API_CONTENT_TYPE";
    public static final String HEALTH_CHECK = "HEALTH_CHECK";
    public static final String IS_SYNC = "IS_SYNC";
    public static final String PROVENANCE = "PROVENANCE";
    public static final String MTLS_STRATEGY = "MTLS_STRATEGY";
    public static final String SOURCE_TYPE = "SOURCE_TYPE";
    public static final String CORRELATION_ID = "CORRELATION_ID";

    public static final String OBSERVABILITY_METRIC_INTERACTION_START_TIME = "X-Observability-Metric-Interaction-Start-Time";
    public static final String OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME = "X-Observability-Metric-Interaction-Finish-Time";
    public static final String OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS = "X-Observability-Metric-Interaction-Duration-Nanosecs";
    public static final String OBSERVABILITY_METRIC_INTERACTION_DURATION_MILLISECS = "X-Observability-Metric-Interaction-Duration-Millisecs";
    public static final String METRIC_COOKIE="METRIC_COOKIE";
    public static final String FHIR_CONTENT_TYPE_HEADER_VALUE = "application/fhir+json";

    public static final String USER_NAME = "USER_NAME";
    public static final String USER_ID = "USER_ID";
    public static final String USER_SESSION = "USER_SESSION";
    public static final String USER_ROLE = "USER_ROLE";    
}
