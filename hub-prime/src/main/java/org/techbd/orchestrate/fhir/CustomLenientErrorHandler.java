package org.techbd.orchestrate.fhir;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.parser.IParserErrorHandler;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue.ScalarType;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue.ValueType;
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
    public void containedResourceWithNoId(IParseLocation theLocation) {
        final var message = new SingleValidationMessage();
        message.setMessage("Resource has contained child resource with no ID : " + theLocation);
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
    }

    @Override
    public void incorrectJsonType(
            IParseLocation theLocation,
            String theElementName,
            ValueType theExpected,
            ScalarType theExpectedScalarType,
            ValueType theFound,
            ScalarType theFoundScalarType) {
        String message = createIncorrectJsonTypeMessage(
                theElementName, theExpected, theExpectedScalarType, theFound, theFoundScalarType);
        final var smessage = new SingleValidationMessage();
        smessage.setMessage(message);
        smessage.setSeverity(ResultSeverityEnum.ERROR);
        smessage.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(smessage);
        LOG.warn(message);
    }

     @Override
    public void unknownReference(IParseLocation theLocation, String theReference) {
        final var message = new SingleValidationMessage();
        message.setMessage("Unknown reference: " + theReference);
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
    }

    @Override
    public void invalidValue(IParseLocation theLocation, String theValue, String theError) {
        // Create a validation message
        final var message = new SingleValidationMessage();
        message.setMessage(theError);
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
    }

    @Override
    public void unknownElement(IParseLocation theLocation, String theElementName) {
        final var message = new SingleValidationMessage();
        message.setMessage("Unknown element: " + theElementName);
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
    }
    @Override
	public void missingRequiredElement(IParseLocation theLocation, String theElementName) {
        final var message = new SingleValidationMessage();
        message.setMessage("Resource is missing required element:" + theElementName);
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
	}
@Override
	public void unexpectedRepeatingElement(IParseLocation theLocation, String theElementName) {
        final var message = new SingleValidationMessage();
        message.setMessage("Multiple repetitions of non-repeatable element :"+theElementName+"found while parsing :" + describeLocation(theLocation));
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
	}

	@Override
	public void unknownAttribute(IParseLocation theLocation, String theElementName) {
        final var message = new SingleValidationMessage();
        message.setMessage("Unknown attribute :"+theElementName+" found while parsing :"+ describeLocation(theLocation));
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
    }

	@Override
	public void extensionContainsValueAndNestedExtensions(IParseLocation theLocation) {
        final var message = new SingleValidationMessage();
        message.setMessage("Extension contains both a value and nested extensions "+ describeLocation(theLocation));
        message.setSeverity(ResultSeverityEnum.ERROR);
        message.setLocationString(describeLocation(theLocation));
        preValidationMessages.add(message);
	}

    public List<SingleValidationMessage> getPreValidationMessages() {
        return preValidationMessages;
    }

    String describeLocation(IParserErrorHandler.IParseLocation theLocation) {
		if (theLocation == null) {
			return "";
		} else {
			return theLocation.toString() + " ";
		}
	}
}