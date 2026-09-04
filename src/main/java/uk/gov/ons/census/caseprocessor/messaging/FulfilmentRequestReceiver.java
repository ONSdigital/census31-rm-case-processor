package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.FulfilmentRequest;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.service.FulfilmentRequestService;
import uk.gov.ons.census.common.model.entity.*;

@MessageEndpoint
public class FulfilmentRequestReceiver {

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  @Value("${caserefgeneratorkey}")
  private byte[] caserefgeneratorkey;

  private final FulfilmentRequestService fulfilmentRequestService;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  private static final String SMS_FULFILMENT_DESCRIPTION = "SMS fulfilment request received";
  private static final String PRINT_FULFILMENT_DESCRIPTION = "Print fulfilment requested";
  private static final List<String> smsIndividualPackCodes =
      List.of("UACIT1", "UACIT2", "UACIT2W", "UACIT3", "UACIT4");
  private static final List<String> printIndividualPackCodes =
      List.of("P_OR_I1", "P_OR_I2", "P_OR_I2W", "P_OR_IACR3");

  public FulfilmentRequestReceiver(
      FulfilmentRequestService fulfilmentRequestService,
      EventLogger eventLogger,
      CaseService caseService) {
    this.fulfilmentRequestService = fulfilmentRequestService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "fulfilmentRequestInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    if (!processEvent(event)) {
      return;
    }

    Case eventCase;
    Case caze;
    UUID caseId;
    String packCode = event.getPayload().getFulfilmentRequest().getFulfilmentCode();

    Optional<SmsTemplate> smsTemplate = fulfilmentRequestService.getSmsTemplate(packCode);
    Optional<ExportFileTemplate> exportFileTemplate =
        fulfilmentRequestService.getExportFileTemplate(packCode);
    boolean isPrintFulfilment = (exportFileTemplate.isPresent() && smsTemplate.isEmpty());
    boolean isSMSFulfilment = smsTemplate.isPresent() && exportFileTemplate.isEmpty();
    boolean isIndividualCase = false;

    FulfilmentRequest fulfilmentRequest = event.getPayload().getFulfilmentRequest();
    caseId = fulfilmentRequest.getCaseId();
    caze = caseService.getCase(caseId);
    eventCase = caze;
    UUID individualCaseId = fulfilmentRequest.getIndividualCaseId();

    if (individualCaseId != null
        && !checkIndividualCaseRequired(fulfilmentRequest.getFulfilmentCode())) {
      throw new RuntimeException(
          String.format(
              "Given pack_code %s does not belongs to Individual Fulfilment request for the case Id %s",
              fulfilmentRequest.getFulfilmentCode(), fulfilmentRequest.getCaseId()));
    }

    // Flow for child case if required for fulfilment and parentCase should be HH
    if (checkIndividualCaseRequired(fulfilmentRequest.getFulfilmentCode())
        && checkParentCaseIsHH(eventCase)) {

      if (individualCaseId != null
          && fulfilmentRequestService.isCaseAlreadyExists(individualCaseId)) {
        throw new RuntimeException(
            "Case already exists in the DB for the given individual case id");
      }

      Case individualCase =
          fulfilmentRequestService.processFulfilmentForIndividual(
              event, eventCase, caserefgeneratorkey, individualCaseId);

      eventLogger.logCaseEvent(
          individualCase, "New case created", EventType.NEW_CASE, event, message);
      if (individualCase != null) {
        isIndividualCase = true;
        caze = individualCase;
      }
    }

    // Flow for Fulfilment
    if (isPrintFulfilment) {
      caze = fulfilmentRequestService.processPrintFulfilmentReceiver(event, caze);

      // logEvents
      if (isIndividualCase) {
        if (caze != null && eventCase != null && eventCase.getId() != caze.getId()) {
          eventLogger.logCaseEvent(
              eventCase, PRINT_FULFILMENT_DESCRIPTION, EventType.PRINT_FULFILMENT, event, message);
        }
      }
      if (caze != null) {
        eventLogger.logCaseEvent(
            caze, PRINT_FULFILMENT_DESCRIPTION, EventType.PRINT_FULFILMENT, event, message);
      }

    } else if (isSMSFulfilment) {

      if (fulfilmentRequest.getContact().getTelNo() != null
          && !fulfilmentRequestService.validatePhoneNumber(
              fulfilmentRequest.getContact().getTelNo())) {
        throw new RuntimeException("Invalid phone number on SMS request message");
      }

      EventDTO smsRequestEnrichedEvent =
          fulfilmentRequestService.processSMSRequestReceiver(
              event, smsRequestEnrichedTopic, caze.getId());

      caze =
          fulfilmentRequestService.processSMSFulfilmentService(
              smsRequestEnrichedEvent, smsRequestEnrichedTopic, caze);

      // logEvents
      if (isIndividualCase) {
        if (caze != null && eventCase != null && eventCase.getId() != caze.getId()) {
          eventLogger.logCaseEvent(
              eventCase, SMS_FULFILMENT_DESCRIPTION, EventType.SMS_FULFILMENT, event, message);
        }
      }
      if (caze != null) {
        eventLogger.logCaseEvent(
            caze,
            SMS_FULFILMENT_DESCRIPTION,
            EventType.SMS_FULFILMENT,
            smsRequestEnrichedEvent,
            message);
      }

    } else {
      throw new RuntimeException("Invalid pack code on fulfilment request message");
    }
  }

  boolean processEvent(EventDTO receiptEvent) {

    EventHeaderDTO eventHeader = receiptEvent.getHeader();

    switch (eventHeader.getMessageType()) {
      case FULFILMENT_REQUEST:
        return true;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format(
                "Event Type '%s' is invalid on this topic", eventHeader.getMessageType()));
    }
  }

  boolean checkParentCaseIsHH(Case eventCase) {
    if (eventCase != null
        && eventCase.getCaseType() != null
        && "HH".equals(eventCase.getCaseType())) {
      return true;
    } else return false;
  }

  private boolean checkIndividualCaseRequired(String packCode) {

    if (smsIndividualPackCodes.contains(packCode) || printIndividualPackCodes.contains(packCode))
      return true;
    else return false;
  }
}
