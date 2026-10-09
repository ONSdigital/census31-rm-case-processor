package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;
import static uk.gov.ons.census.caseprocessor.utils.MessageDateHelper.getMessageTimeStamp;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.service.ReceiptService;

@MessageEndpoint
public class ReceiptReceiver {
  private final ReceiptService receiptService;

  public ReceiptReceiver(ReceiptService receiptService) {
    this.receiptService = receiptService;
  }

  @ServiceActivator(inputChannel = "receiptInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO receiptEvent = convertJsonBytesToEvent(message.getPayload());

    if (!processEvent(receiptEvent)) {
      return;
    }

    receiptEvent.setMessageTimestamp(getMessageTimeStamp(message));
    receiptService.processReceiptEvent(receiptEvent);
  }

  private boolean processEvent(EventDTO receiptEvent) {
    EventHeaderDTO eventHeader = receiptEvent.getHeader();

    switch (eventHeader.getMessageType()) {
      case RESPONSE_RECEIVED:
        return true;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format(
                "Event Type '%s' is invalid on this topic", eventHeader.getMessageType()));
    }
  }
}
