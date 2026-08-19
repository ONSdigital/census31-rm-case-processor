package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.service.FulfilmentRequestService;
import uk.gov.ons.census.common.model.entity.*;

@MessageEndpoint
public class FulfilmentRequestReceiver {

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  private final FulfilmentRequestService fulfilmentRequestService;
  private final EventLogger eventLogger;

  private static final String SMS_FULFILMENT_DESCRIPTION = "SMS fulfilment request received";

  public FulfilmentRequestReceiver(
      FulfilmentRequestService fulfilmentRequestService, EventLogger eventLogger) {
    this.fulfilmentRequestService = fulfilmentRequestService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "fulfilmentRequestInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO receiptEvent = convertJsonBytesToEvent(message.getPayload());
    if (!processEvent(receiptEvent)) {
      return;
    }

    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    Case caze;
    String packCode = event.getPayload().getFulfilmentRequest().getFulfilmentCode();

    Optional<SmsTemplate> smsTemplate = fulfilmentRequestService.getSmsTemplate(packCode);
    Optional<ExportFileTemplate> exportFileTemplate =
        fulfilmentRequestService.getExportFileTemplate(packCode);

    if (exportFileTemplate.isPresent() && smsTemplate.isEmpty()) {
      caze = fulfilmentRequestService.processPrintFulfilmentReceiver(event);

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

      EventDTO smsRequestEnrichedEvent =
          fulfilmentRequestService.processSMSRequestReceiver(event, smsRequestEnrichedTopic);

      caze =
          fulfilmentRequestService.processSMSFulfilmentReceiptService(
              smsRequestEnrichedEvent, smsRequestEnrichedTopic);

      eventLogger.logCaseEvent(
          caze,
          SMS_FULFILMENT_DESCRIPTION,
          EventType.SMS_FULFILMENT,
          smsRequestEnrichedEvent,
          message);

    } else {
      // TODO: Need to check what should happen when the fulfilment code or pack code is not valid.
      return;
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
