package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.MessageDateHelper.getMessageTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.service.QidReceiptService;

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
    qidReceiptService.processReceipt(message, messageTimestamp);
  }
}
