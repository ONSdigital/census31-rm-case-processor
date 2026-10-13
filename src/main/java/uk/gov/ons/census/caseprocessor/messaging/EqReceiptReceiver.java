package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;
import static uk.gov.ons.census.caseprocessor.utils.MessageDateHelper.getMessageTimeStamp;

import java.util.UUID;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.ons.census.caseprocessor.model.dto.EqReceiptDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.ResponseDTO;
import uk.gov.ons.census.caseprocessor.service.ReceiptService;
import uk.gov.ons.census.caseprocessor.utils.ObjectMapperFactory;
import uk.gov.ons.census.common.model.entity.EventType;

@MessageEndpoint
public class EqReceiptReceiver {
  private static final String EQ_RECEIPT_TOPIC = "eq_receipt";
  private static final String EQ_CHANNEL = "EQ";
  private static final String RECEIPT_SOURCE = "RECEIPT_SERVICE";

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.objectMapper();
  private final ReceiptService receiptService;

  public EqReceiptReceiver(ReceiptService receiptService) {
    this.receiptService = receiptService;
  }

  @ServiceActivator(inputChannel = "eqReceiptInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EqReceiptDTO eqReceipt = parseEqReceipt(message.getPayload());
    EventDTO event = buildResponseReceivedEvent(message, eqReceipt);

    receiptService.processReceiptEvent(event);
  }

  private EqReceiptDTO parseEqReceipt(byte[] payload) {
    try {
      return OBJECT_MAPPER.readValue(payload, EqReceiptDTO.class);
    } catch (JacksonException e) {
      throw new RuntimeException("Failed to read eQ receipt", e);
    }
  }

  private EventDTO buildResponseReceivedEvent(Message<byte[]> message, EqReceiptDTO eqReceipt) {
    if (eqReceipt.getQuestionnaireId() == null || eqReceipt.getQuestionnaireId().isBlank()) {
      throw new IllegalArgumentException("eQ receipt is missing questionnaire_id");
    }

    EventHeaderDTO header = new EventHeaderDTO();
    header.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    header.setTopic(EQ_RECEIPT_TOPIC);
    header.setChannel(EQ_CHANNEL);
    header.setSource(RECEIPT_SOURCE);
    header.setMessageId(UUID.randomUUID());
    header.setCorrelationId(toCorrelationId(eqReceipt.getTxId()));
    header.setDateTime(getMessageTimeStamp(message));
    header.setMessageType(EventType.RESPONSE_RECEIVED);

    ResponseDTO response = new ResponseDTO();
    response.setQuestionnaireId(eqReceipt.getQuestionnaireId());
    PayloadDTO payload = new PayloadDTO();
    payload.setResponse(response);

    EventDTO event = new EventDTO();
    event.setHeader(header);
    event.setPayload(payload);
    event.setMessageTimestamp(header.getDateTime());
    return event;
  }

  private UUID toCorrelationId(String txId) {
    return txId == null || txId.isBlank() ? UUID.randomUUID() : UUID.fromString(txId);
  }
}
