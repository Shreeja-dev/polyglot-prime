package org.techbd.orchestrate.fhir;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;

public class CustomLenientErrorHandler extends LenientErrorHandler {

    private final List<SingleValidationMessage> preValidationMessages = new ArrayList<>();
    private static final Logger LOG = LoggerFactory.getLogger(CustomLenientErrorHandler.class);

    public CustomLenientErrorHandler() {
        super();
    }

    public CustomLenientErrorHandler(boolean myLogErrors) {
        super(myLogErrors);
    }
    @Override
    public void invalidValue(IParseLocation theLocation, String theValue, String theError) {
        // Create a validation message
        final var message = new SingleValidationMessage();
        message.setMessage(theError);
        message.setSeverity(ResultSeverityEnum.ERROR);
        // TODO - We are not getting line number and column number from IParseLocation.
        // Check to see if there is alternative approach
        preValidationMessages.add(message);
        LOG.warn(
                "WARNING: Invalid value :" + theValue + " - " + theError);
    }
    @Override
    public void unknownElement(IParseLocation theLocation, String theElementName) {
        final var message = new SingleValidationMessage();
        message.setMessage("Unknown element: " + theElementName);
        message.setSeverity(ResultSeverityEnum.ERROR);
        preValidationMessages.add(message);

        LOG.warn("WARNING: Unknown element  " + ": " + theElementName);
    }
    public List<SingleValidationMessage> getPreValidationMessages() {
        return preValidationMessages;
    }
}