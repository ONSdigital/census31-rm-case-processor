package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.caseprocessor.service.QidReceiptService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
public class ReceiptReceiverTest {
  private final String QID = "1234567890123456";

  @Mock private EventLogger eventLogger;
  @Mock private QidReceiptService qidReceiptService;

  @InjectMocks ReceiptReceiver underTest;

  @Test
  public void testReceiptReceivedEventFromRH() {
    EventDTO receiptEvent = new EventDTO();
    receiptEvent.setHeader(new EventHeaderDTO());
    receiptEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    receiptEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    receiptEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    receiptEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    receiptEvent.getHeader().setTopic("Test topic");
    receiptEvent.getHeader().setChannel("RH");
    receiptEvent.getHeader().setMessageType(EventType.RECEIPT);
    receiptEvent.setPayload(new PayloadDTO());

    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(QID);
    receiptEvent.getPayload().setReceipt(receiptDTO);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(QID);
    expectedUacQidLink.setReceiptReceived(true);
    expectedUacQidLink.setActive(false);

    Message<byte[]> message = constructMessage(receiptEvent);

    // Given
    when(qidReceiptService.processReceiptEvent(receiptEvent)).thenReturn(expectedUacQidLink);

    // when
    underTest.receiveMessage(message);

    // then
    verify(qidReceiptService).processReceiptEvent(receiptEvent);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);

    verify(eventLogger)
        .logUacQidEvent(
            uacQidLinkCaptor.capture(),
            eq("Receipt received"),
            eq(EventType.RECEIPT),
            eq(receiptEvent),
            eq(message));

    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(QID);
    assertThat(actualUacQidLink.isReceiptReceived()).isTrue();
    assertThat(actualUacQidLink.isActive()).isFalse();

    verifyNoMoreInteractions(eventLogger);
    verifyNoMoreInteractions(qidReceiptService);
  }

  @Test
  void testReceiptEventFromRHWrongEventType() {
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

    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(QID);
    receiptEvent.getPayload().setReceipt(receiptDTO);

    Message<byte[]> message = constructMessage(receiptEvent);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));

    Assertions.assertThat(thrown.getMessage())
        .isEqualTo("Event Type 'CASE_UPDATE' is invalid on this topic");
  }
}
