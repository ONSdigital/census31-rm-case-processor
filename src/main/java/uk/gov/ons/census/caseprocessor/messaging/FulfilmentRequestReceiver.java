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

  private final FulfilmentRequestService fulfilmentRequestService;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  private static final String SMS_FULFILMENT_DESCRIPTION = "SMS fulfilment request received";

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
    List<String> smsIndividualPackCodes =
        List.of("UACIT1", "UACIT2", "UACIT2W", "UACIT3", "UACIT4");
    List<String> printIndividualPackCodes = List.of("P_OR_I1", "P_OR_I2", "P_OR_I2W", "P_OR_IACR3");

    Case parentCase = null;
    Case caze;
    UUID caseId;
    String packCode = event.getPayload().getFulfilmentRequest().getFulfilmentCode();

    Optional<SmsTemplate> smsTemplate = fulfilmentRequestService.getSmsTemplate(packCode);
    Optional<ExportFileTemplate> exportFileTemplate =
        fulfilmentRequestService.getExportFileTemplate(packCode);

    FulfilmentRequest fulfilmentRequest = event.getPayload().getFulfilmentRequest();
    caseId = fulfilmentRequest.getCaseId();
    if (exportFileTemplate.isPresent() && smsTemplate.isEmpty()) {
      // For PRINT  Individual Fulfilment for HouseHold
      if (printIndividualPackCodes.contains(fulfilmentRequest.getFulfilmentCode())) {
        parentCase = caseService.getCase(caseId);
        checkParentCaseHH(parentCase);

        if (parentCase != null) {
          eventLogger.logCaseEvent(
              parentCase, "Print fulfilment requested", EventType.PRINT_FULFILMENT, event, message);
        }
        caze = fulfilmentRequestService.processFulfilmentForIndividual(event);

        if (caze != null) {
          eventLogger.logCaseEvent(caze, "New case created", EventType.NEW_CASE, event, message);
          caseId = caze.getId();
        }
      }
      caze = fulfilmentRequestService.processPrintFulfilmentReceiver(event, caseId);
      if (caze != null) {
        eventLogger.logCaseEvent(
            caze, "Print fulfilment requested", EventType.PRINT_FULFILMENT, event, message);
      }
    } else if (smsTemplate.isPresent() && exportFileTemplate.isEmpty()) {

      if (event.getPayload().getFulfilmentRequest().getContact().getTelNo() != null
          && !fulfilmentRequestService.validatePhoneNumber(
              event.getPayload().getFulfilmentRequest().getContact().getTelNo())) {
        throw new RuntimeException("Invalid phone number on SMS request message");
      }

      // For SMS Individual Fulfilment for HouseHold
      if (smsIndividualPackCodes.contains(fulfilmentRequest.getFulfilmentCode())) {
        parentCase = caseService.getCase(fulfilmentRequest.getCaseId());
        checkParentCaseHH(parentCase);
        caze = fulfilmentRequestService.processFulfilmentForIndividual(event);
        if (caze != null) {
          eventLogger.logCaseEvent(caze, "New case created", EventType.NEW_CASE, event, message);
          caseId = caze.getId();
        }
      }

      EventDTO smsRequestEnrichedEvent =
          fulfilmentRequestService.processSMSRequestReceiver(
              event, smsRequestEnrichedTopic, caseId);

      caze =
          fulfilmentRequestService.processSMSFulfilmentService(
              smsRequestEnrichedEvent, smsRequestEnrichedTopic);

      if (parentCase != null) {
        eventLogger.logCaseEvent(
            parentCase,
            SMS_FULFILMENT_DESCRIPTION,
            EventType.SMS_FULFILMENT,
            smsRequestEnrichedEvent,
            message);
      }

      eventLogger.logCaseEvent(
          caze,
          SMS_FULFILMENT_DESCRIPTION,
          EventType.SMS_FULFILMENT,
          smsRequestEnrichedEvent,
          message);

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

  void checkParentCaseHH(Case parentCase) {
    if (parentCase != null
        && (parentCase.getCaseType() == null || !("HH".equals(parentCase.getCaseType())))) {
      throw new RuntimeException("Case is not a House Hold Type on fulfilment request message");
    }
  }
}
