package uk.gov.ons.census.caseprocessor.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@Service
public class ReceiptService {
  private final EventLogger eventLogger;
  private final QidReceiptService qidReceiptService;

  public ReceiptService(EventLogger eventLogger, QidReceiptService qidReceiptService) {
    this.eventLogger = eventLogger;
    this.qidReceiptService = qidReceiptService;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public void processReceiptEvent(EventDTO receiptEvent) {
    EventType messageType = receiptEvent.getHeader().getMessageType();
    if (messageType != EventType.RESPONSE_RECEIVED) {
      throw new RuntimeException(
          String.format("Event Type '%s' is invalid on this topic", messageType));
    }

    UacQidLink uacQidLink = qidReceiptService.processReceiptEvent(receiptEvent);
    eventLogger.logUacQidEvent(
        uacQidLink,
        "Receipt received",
        EventType.RESPONSE_RECEIVED,
        receiptEvent,
        receiptEvent.getMessageTimestamp());
  }
}
