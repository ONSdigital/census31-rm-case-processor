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
    EventDTO receiptEvent = convertJsonBytesToEvent(message.getPayload());
    if (!processEvent(receiptEvent)) {
      return;
    }
    List<String> smsIndividualPackCodes =
        List.of("UACIT1", "UACIT2", "UACIT2W", "UACIT3", "UACIT4");
    List<String> printIndividualPackCodes = List.of("P_OR_I1", "P_OR_I2", "P_OR_I2W", "P_OR_IACR3");

    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    Case parentCase = null;
    Case caze;
    UUID caseId;
    String packCode = event.getPayload().getFulfilmentRequest().getFulfilmentCode();

    Optional<SmsTemplate> smsTemplate = fulfilmentRequestService.getSmsTemplate(packCode);
    Optional<ExportFileTemplate> exportFileTemplate =
        fulfilmentRequestService.getExportFileTemplate(packCode);
    // TODO:CN-174 When packCode for SMS/Paper is in given list (UACIT1, UACIT2, UACIT2W, UACIT3,
    // UACIT4, P_OR_I1, P_OR_I2, P_OR_I2W, P_OR_IACR3)
    // need to create  a Child Case, copy all the case sample data from the main HH case, exclude
    // activity flags receiptReceived or
    // refusalReceived to default and case type to HI in the child case.
    // Also, log the fullfilment request for both parent(HH) and child(HI) case, and
    // Generate a new HI type UAC/QID pair and link it to the new HI case
    // emit the uac_update event for the new QID Pair.
    // And action the fulfilment against new HI case ( send the SMS or Print request )
    ///
    FulfilmentRequest fulfilmentRequest = event.getPayload().getFulfilmentRequest();
    caseId = fulfilmentRequest.getCaseId();
    if (exportFileTemplate.isPresent() && smsTemplate.isEmpty()) {
      // For PRINT  Individual Fulfilment
      if (printIndividualPackCodes.contains(fulfilmentRequest.getFulfilmentCode())) {

        parentCase = caseService.getCase(caseId);
          if (parentCase != null) {
              eventLogger.logCaseEvent(
                      parentCase, "Print fulfilment requested", EventType.PRINT_FULFILMENT, event, message);
          }
          caze =
            fulfilmentRequestService.processFulfilmentForIndividual(
                event);
        if (caze != null) {
          eventLogger.logCaseEvent(caze, "New case created", EventType.NEW_CASE, event, message);
          caseId = caze.getId();
        }
      }
      // TODO: should rebuild the HeaderDTO or just change the case Id and use the same header for
      // child case fulfilment.
      caze = fulfilmentRequestService.processPrintFulfilmentReceiver(event, caseId);

      // TODO: LogEvent for both parent and child case

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
      // For SMS Individual Fulfilment
      if (smsIndividualPackCodes.contains(fulfilmentRequest.getFulfilmentCode())) {
        parentCase = caseService.getCase(fulfilmentRequest.getCaseId());
        caze =
            fulfilmentRequestService.processFulfilmentForIndividual(
                event);
        if (caze != null) {
          eventLogger.logCaseEvent(caze, "New case created", EventType.NEW_CASE, event, message);
          caseId = caze.getId();
        }
      }
      // TODO: should rebuild the HeaderDTO or just change the case Id and use the same header for
      // child case fulfilment.
      EventDTO smsRequestEnrichedEvent =
          fulfilmentRequestService.processSMSRequestReceiver(
              event, smsRequestEnrichedTopic, caseId);

      caze =
          fulfilmentRequestService.processSMSFulfilmentService(
              smsRequestEnrichedEvent, smsRequestEnrichedTopic);

      // TODO: LogEvent for both parent and child case
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
}
