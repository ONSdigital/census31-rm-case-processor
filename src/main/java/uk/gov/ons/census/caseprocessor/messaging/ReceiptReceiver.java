package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.service.QidReceiptService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@MessageEndpoint
public class ReceiptReceiver {
  private final EventLogger eventLogger;
  private final QidReceiptService qidReceiptService;

  public ReceiptReceiver(EventLogger eventLogger, QidReceiptService qidReceiptService) {
    this.eventLogger = eventLogger;
    this.qidReceiptService = qidReceiptService;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @ServiceActivator(inputChannel = "receiptInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {

    EventDTO receiptEvent = convertJsonBytesToEvent(message.getPayload());

    if (!processEvent(receiptEvent)) {
      return;
    }

    UacQidLink uacQidLink = qidReceiptService.processReceiptEvent(receiptEvent);

    eventLogger.logUacQidEvent(
        uacQidLink, "Receipt received", EventType.RECEIPT, receiptEvent, message);
  }

  private boolean processEvent(EventDTO receiptEvent) {

    EventHeaderDTO eventHeader = receiptEvent.getHeader();

    switch (eventHeader.getMessageType()) {
      case RECEIPT:
        return true;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format(
                "Event Type '%s' is invalid on this topic", eventHeader.getMessageType()));
    }
  }
}
