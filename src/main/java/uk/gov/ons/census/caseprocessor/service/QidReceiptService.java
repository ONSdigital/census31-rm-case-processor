package uk.gov.ons.census.caseprocessor.service;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@Service
public class QidReceiptService {

  private static final Logger log = LoggerFactory.getLogger(QidReceiptService.class);
  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseReceiptService caseReceiptService;

  public QidReceiptService(
      UacService uacService, EventLogger eventLogger, CaseReceiptService caseReceiptService) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseReceiptService = caseReceiptService;
  }

  public void processReceipt(Message<byte[]> message, OffsetDateTime messageTimestamp) {

    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    UacQidLink uacQidLink = uacService.findByQid(event.getPayload().getReceipt().getQid());

    if (!uacQidLink.isReceiptReceived()) {
      uacQidLink.setActive(false);
      uacQidLink.setReceiptReceived(true);

      uacQidLink =
          uacService.saveAndEmitUacUpdateEvent(
              uacQidLink,
              event.getHeader().getCorrelationId(),
              event.getHeader().getOriginatingUser());

      Case caze = uacQidLink.getCaze();

      if (caze != null) {
        caseReceiptService.receiptCase(uacQidLink, event);
      } else {
        if (log.isWarnEnabled()) {
          log.warn(
              "Receipt received for unaddressed UAC/QID pair not yet linked to a case. QID: {}, Correlation ID: {}, Channel: {}",
              event.getPayload().getReceipt().getQid(),
              event.getHeader().getCorrelationId(),
              event.getHeader().getChannel());
        }
      }
    }
    eventLogger.logUacQidEvent(uacQidLink, "Receipt received", EventType.RECEIPT, event, message);
  }
}
