package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import static uk.gov.ons.census.caseprocessor.utils.MessageDateHelper.getMessageTimeStamp;

import java.time.OffsetDateTime;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.service.QidReceiptService;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;



@MessageEndpoint
public class ReceiptReceiver {
    private final QidReceiptService qidReceiptService;

    public ReceiptReceiver(QidReceiptService qidReceiptService) {
        this.qidReceiptService = qidReceiptService;
    }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @ServiceActivator(inputChannel = "receiptInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {

      OffsetDateTime messageTimestamp = getMessageTimeStamp(message);
      EventDTO event = convertJsonBytesToEvent(message.getPayload());
      qidReceiptService.processReceipt(event.getPayload(), messageTimestamp);

//    EventDTO event = convertJsonBytesToEvent(message.getPayload());
//
//    UacQidLink uacQidLink = uacService.findByQid(event.getPayload().getReceipt().getQid());
//
//    if (!uacQidLink.isReceiptReceived()) {
//      uacQidLink.setActive(false);
//      uacQidLink.setReceiptReceived(true);
//
//      uacQidLink =
//          uacService.saveAndEmitUacUpdateEvent(
//              uacQidLink,
//              event.getHeader().getCorrelationId(),
//              event.getHeader().getOriginatingUser());
    }

    //eventLogger.logUacQidEvent(uacQidLink, "Receipt received", EventType.RECEIPT, event, message);
  }

