package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.service.ReceiptService;
import uk.gov.ons.census.caseprocessor.utils.JsonHelper;
import uk.gov.ons.census.common.model.entity.EventType;

@ExtendWith(MockitoExtension.class)
class EqReceiptReceiverTest {
  @Mock private ReceiptService receiptService;
  @InjectMocks private EqReceiptReceiver underTest;

  @Test
  void translatesEqReceiptIgnoringCaseIdAndAdditionalFields() {
    UUID txId = UUID.randomUUID();
    Message<byte[]> message =
        message(
            """
            {"tx_id":"%s", "case_id":"wrong-case", "questionnaire_id":"0123456789", "extra":"ignored"}
            """
                .formatted(txId));

    underTest.receiveMessage(message);

    ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
    verify(receiptService).processReceiptEvent(captor.capture());
    EventDTO event = captor.getValue();
    assertThat(event.getHeader().getVersion()).isEqualTo(OUTBOUND_EVENT_SCHEMA_VERSION);
    assertThat(event.getHeader().getTopic()).isEqualTo("eq_receipt");
    assertThat(event.getHeader().getSource()).isEqualTo("RECEIPT_SERVICE");
    assertThat(event.getHeader().getChannel()).isEqualTo("EQ");
    assertThat(event.getHeader().getMessageType()).isEqualTo(EventType.RESPONSE_RECEIVED);
    assertThat(event.getHeader().getCorrelationId()).isEqualTo(txId);
    assertThat(event.getHeader().getMessageId()).isNotEqualTo(txId);
    assertThat(event.getHeader().getMessageId().version()).isEqualTo(4);
    assertThat(event.getHeader().getDateTime().toInstant().toEpochMilli())
        .isEqualTo(message.getHeaders().getTimestamp());
    assertThat(event.getMessageTimestamp()).isEqualTo(event.getHeader().getDateTime());
    assertThat(event.getPayload().getResponse().getQuestionnaireId()).isEqualTo("0123456789");
    assertThat(JsonHelper.convertObjectToJson(event))
        .doesNotContain("wrong-case", "messageTimestamp");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void generatesNewCorrelationIdWhenTxIdIsBlank(String txId) {
    underTest.receiveMessage(
        message("{\"tx_id\":\"" + txId + "\",\"questionnaire_id\":\"0123456789\"}"));

    ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
    verify(receiptService).processReceiptEvent(captor.capture());
    assertThat(captor.getValue().getHeader().getCorrelationId().version()).isEqualTo(4);
    assertThat(captor.getValue().getHeader().getMessageId())
        .isNotEqualTo(captor.getValue().getHeader().getCorrelationId());
  }

  @Test
  void generatesNewCorrelationIdWhenTxIdIsAbsent() {
    underTest.receiveMessage(message("{\"questionnaire_id\":\"0123456789\"}"));

    ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
    verify(receiptService).processReceiptEvent(captor.capture());
    assertThat(captor.getValue().getHeader().getCorrelationId().version()).isEqualTo(4);
    assertThat(captor.getValue().getHeader().getMessageId())
        .isNotEqualTo(captor.getValue().getHeader().getCorrelationId());
  }

  @Test
  void generatesNewCorrelationIdWhenTxIdIsNull() {
    underTest.receiveMessage(message("{\"tx_id\":null,\"questionnaire_id\":\"0123456789\"}"));

    ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
    verify(receiptService).processReceiptEvent(captor.capture());
    assertThat(captor.getValue().getHeader().getCorrelationId().version()).isEqualTo(4);
    assertThat(captor.getValue().getHeader().getMessageId())
        .isNotEqualTo(captor.getValue().getHeader().getCorrelationId());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"case_id\":\"wrong-case\"}",
        "{\"questionnaire_id\":\"\"}",
        "{\"questionnaire_id\":\" \"}",
        "{\"questionnaire_id\":null}"
      })
  void rejectsMissingOrBlankQuestionnaireId(String payload) {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> underTest.receiveMessage(message(payload)));

    assertThat(thrown.getMessage()).contains("questionnaire_id");
    verifyNoInteractions(receiptService);
  }

  @Test
  void rejectsInvalidTransactionId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            underTest.receiveMessage(
                message("{\"tx_id\":\"not-a-uuid\",\"questionnaire_id\":\"0123456789\"}")));
    verifyNoInteractions(receiptService);
  }

  @Test
  void rejectsMalformedJson() {
    assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message("not json")));
    verifyNoInteractions(receiptService);
  }

  private Message<byte[]> message(String json) {
    return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
  }
}
