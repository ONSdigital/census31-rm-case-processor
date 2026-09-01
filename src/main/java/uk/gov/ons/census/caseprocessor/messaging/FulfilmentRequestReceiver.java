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
    boolean logChildCaseEvent = false;

    FulfilmentRequest fulfilmentRequest = event.getPayload().getFulfilmentRequest();
    caseId = fulfilmentRequest.getCaseId();
    caze = caseService.getCase(caseId);
    eventCase = caze;

    // Flow for child case if required for fulfilment
    if (checkCreateChildCaseRequired(fulfilmentRequest.getFulfilmentCode())) {

      checkParentCaseIsHH(eventCase);

      Case childCase =
          fulfilmentRequestService.processFulfilmentForIndividual(
              event, eventCase, caserefgeneratorkey);

      if (childCase != null) {
        logChildCaseEvent = true;
        caze = childCase;
      }
    }
    // Flow for Fulfilment
    if (isPrintFulfilment) {
      caze = fulfilmentRequestService.processPrintFulfilmentReceiver(event, caze);

      // logEvents
      if (logChildCaseEvent) {
        eventLogger.logCaseEvent(caze, "New case created", EventType.NEW_CASE, event, message);

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
      if (logChildCaseEvent) {
        eventLogger.logCaseEvent(caze, "New case created", EventType.NEW_CASE, event, message);

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

  void checkParentCaseIsHH(Case eventCase) {
    if (eventCase != null
        && (eventCase.getCaseType() == null || !("HH".equals(eventCase.getCaseType())))) {
      throw new RuntimeException("Case is not a House Hold Type on fulfilment request message");
    }
  }

  private boolean checkCreateChildCaseRequired(String packCode) {

    if (smsIndividualPackCodes.contains(packCode) || printIndividualPackCodes.contains(packCode))
      return true;
    else return false;
  }
}
