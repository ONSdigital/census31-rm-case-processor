package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.caseprocessor.service.ReceiptService;
import uk.gov.ons.census.caseprocessor.utils.JsonHelper;
import uk.gov.ons.census.common.model.entity.EventType;

@ExtendWith(MockitoExtension.class)
public class ReceiptReceiverTest {
  private final String QID = "1234567890123456";

  @Mock private ReceiptService receiptService;

  @InjectMocks ReceiptReceiver underTest;

  @Test
  public void forwardsResponseReceivedEventToReceiptServiceWithMessageTimestamp() {
    EventDTO receiptEvent = new EventDTO();
    receiptEvent.setHeader(new EventHeaderDTO());
    receiptEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    receiptEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    receiptEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    receiptEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    receiptEvent.getHeader().setTopic("Test topic");
    receiptEvent.getHeader().setChannel("RH");
    receiptEvent.getHeader().setMessageType(EventType.RESPONSE_RECEIVED);
    receiptEvent.setPayload(new PayloadDTO());

    ResponseDTO responseDTO = new ResponseDTO();
    responseDTO.setQuestionnaireId(QID);
    receiptEvent.getPayload().setResponse(responseDTO);

    Message<byte[]> message =
        MessageBuilder.withPayload(
                JsonHelper.convertObjectToJson(receiptEvent).getBytes(StandardCharsets.UTF_8))
            .build();

    // when
    underTest.receiveMessage(message);

    // then
    ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
    verify(receiptService).processReceiptEvent(captor.capture());
    assertThat(captor.getValue().getPayload().getResponse().getQuestionnaireId()).isEqualTo(QID);
    assertThat(captor.getValue().getHeader().getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(captor.getValue().getMessageTimestamp().toInstant().toEpochMilli())
        .isEqualTo(message.getHeaders().getTimestamp());
  }

  @Test
  void rejectsNonResponseReceivedEventTypeBeforeCallingReceiptService() {
    EventDTO receiptEvent = new EventDTO();
    receiptEvent.setHeader(new EventHeaderDTO());
    receiptEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    receiptEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    receiptEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    receiptEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    receiptEvent.getHeader().setTopic("Test topic");
    receiptEvent.getHeader().setChannel("RH");
    receiptEvent.getHeader().setMessageType(EventType.CASE_UPDATE);
    receiptEvent.setPayload(new PayloadDTO());

    ResponseDTO responseDTO = new ResponseDTO();
    responseDTO.setQuestionnaireId(QID);
    receiptEvent.getPayload().setResponse(responseDTO);

    Message<byte[]> message =
        MessageBuilder.withPayload(
                JsonHelper.convertObjectToJson(receiptEvent).getBytes(StandardCharsets.UTF_8))
            .build();

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));

    assertThat(thrown.getMessage()).isEqualTo("Event Type 'CASE_UPDATE' is invalid on this topic");
    verifyNoInteractions(receiptService);
  }
}
