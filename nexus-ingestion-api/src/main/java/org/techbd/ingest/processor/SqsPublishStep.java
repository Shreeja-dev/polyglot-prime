package org.techbd.ingest.processor;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.AppLogger;
import org.techbd.ingest.commons.TemplateLogger;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageGroupService;
import org.techbd.ingest.service.MetadataBuilderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
/**
 * {@code SqsPublishStep} is a {@link MessageProcessingStep} implementation responsible for
 * publishing messages to an Amazon SQS queue.
 * <p>
 * This step is typically used to send messages containing metadata and content to SQS
 * for downstream processing by other services or systems.
 * </p>
 *
 * <p>
 * It supports both {@link MultipartFile} and raw {@link String} content. The message body
 * is typically constructed using details from the {@link RequestContext}, including source,
 * identifiers, and correlation metadata.
 * </p>
 */
@Component
@Order(2)
public class SqsPublishStep implements MessageProcessingStep {
    private static final Logger LOG = LoggerFactory.getLogger(SqsPublishStep.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MetadataBuilderService metadataBuilderService;
    private final AppConfig appConfig;
    private final MessageGroupService messageGroupService;
    private final TemplateLogger log;
    
    public SqsPublishStep(SqsClient sqsClient, ObjectMapper objectMapper, MetadataBuilderService metadataBuilderService,
            AppConfig appConfig, MessageGroupService messageGroupService,AppLogger appLogger) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.metadataBuilderService = metadataBuilderService;
        this.messageGroupService = messageGroupService;
        this.appConfig = appConfig;
        this.log = appLogger.getLogger(SqsPublishStep.class);
        LOG.info("SqsPublishStep initialized");
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
         log.info(interactionId, "SqsPublishStep::process",
                "Called with MultipartFile. filename={}", file != null ? file.getOriginalFilename() : "null");
        
        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context,interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            String queueUrl = appConfig.getAws().getSqs().getFifoQueueUrl();
            log.info(interactionId, "SqsPublishStep::process","Sending message to SQS. queueUrl={}", queueUrl);
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
                    .build())
                    .messageId();
            context.setMessageId(messageId);
            log.info(interactionId, "SqsPublishStep::process", "Message sent to SQS successfully. messageId={}", messageId);
        } catch (Exception e) {
            log.error(interactionId, "SqsPublishStep::process", "SQS Publish Step Failed", e);
            // throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }

    public void process(RequestContext context, String content,String ackMessage) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        log.info(interactionId, "SqsPublishStep::process", "Called with String content.");
        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context,interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            String queueUrl = appConfig.getAws().getSqs().getFifoQueueUrl();
            log.info(interactionId, "SqsPublishStep::process", "Sending message to SQS. queueUrl={}", queueUrl);

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
                    .build())
                    .messageId();
            context.setMessageId(messageId);
            log.info(interactionId, "SqsPublishStep::process", "Message sent to SQS successfully. messageId={}", messageId);
        } catch (Exception e) {
            log.error(interactionId, "SqsPublishStep::process", "SQS Publish Step Failed", e);
            // throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }
}