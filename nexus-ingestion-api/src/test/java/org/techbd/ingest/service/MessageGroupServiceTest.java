package org.techbd.ingest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.ingest.commons.AppLogger;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.TemplateLogger;
import org.techbd.ingest.model.RequestContext;

class MessageGroupServiceTest {

    private MessageGroupService messageGroupService;

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger log;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(appLogger.getLogger(MessageGroupService.class)).thenReturn(log);
        messageGroupService = new MessageGroupService(appLogger);
    }

    @Test
    void testCreateMessageGroupId_withValidValues() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",                
                "s3://bucket/metadata",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1", // localAddress
                "192.168.1.1", // remoteAddress
                "192.168.1.1", // sourceIp
                "192.168.1.2", // destinationIp
                "8080",null,null
        );

        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals("192.168.1.1_192.168.1.2_8080", groupId);
    }

    @Test
    void testCreateMessageGroupId_withNullValues_returnsDefault() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/metadata.txt",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                null, // sourceIp
                "192.168.1.2",
                "8080",null,null);

        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withEmptyValues_returnsDefault() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/filemetadata.txt",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "   ", // sourceIp blank
                "", // destinationIp empty
                " " ,null,null// destinationPort blank
        );

        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }

    @Test
    void testCreateMessageGroupId_withOneMissingField_returnsDefault() {
        RequestContext context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/filemetdata.txt",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "192.168.1.1",
                null, // destinationIp is null
                "8080",null,null);

        String groupId = messageGroupService.createMessageGroupId(context, "interaction123");
        assertEquals(Constants.DEFAULT_MESSAGE_GROUP_ID, groupId);
    }
}