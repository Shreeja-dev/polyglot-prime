package org.techbd.nexusingestionapi.model;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
public class Message {
    private String id;
    private byte[] payload;
    private String source;
    private String contentType;
    private Instant receivedAt;
    private Map<String, String> metadata;
}
