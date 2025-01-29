package org.techbd.service.constants;

public enum CsvProcessingStatus {
    RECEIVED, //initial state 
    PROCESSING_STARTED, 
    PARTIALLY_PROCESSED,
    PROCESSED_SUCESSFULLY
}
