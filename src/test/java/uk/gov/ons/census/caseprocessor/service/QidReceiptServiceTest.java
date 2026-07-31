package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
public class QidReceiptServiceTest {
  private final String TEST_QID_ID = "1234567890123456";

  @Mock private UacService uacService;
  @Mock private CaseReceiptService caseReceiptService;

  @InjectMocks QidReceiptService underTest;

  @Test
  public void testHandleReceiptEvent() {
    // Given
    EventDTO receiptEvent = new EventDTO();
    receiptEvent.setHeader(new EventHeaderDTO());
    receiptEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    receiptEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    receiptEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    receiptEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    receiptEvent.getHeader().setTopic("Test topic");
    receiptEvent.getHeader().setChannel("RH");
    receiptEvent.getHeader().setMessageType(EventType.RECEIPT);
    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(TEST_QID_ID);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    receiptEvent.setPayload(payloadDTO);

    Case caze = new Case();
    caze.setReceiptReceived(false);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(TEST_QID_ID);
    expectedUacQidLink.setReceiptReceived(false);
    expectedUacQidLink.setActive(true);
    expectedUacQidLink.setCaze(caze);

    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);

    when(uacService.saveAndEmitUacUpdateEvent(
            expectedUacQidLink,
            receiptEvent.getHeader().getCorrelationId(),
            receiptEvent.getHeader().getOriginatingUser()))
        .thenReturn(expectedUacQidLink);

    when(caseReceiptService.receiptCase(expectedUacQidLink, receiptEvent))
        .thenReturn(expectedUacQidLink);

    // When
    underTest.processReceiptEvent(receiptEvent);

    // Then
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink capturedUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(capturedUacQidLink.isActive()).isFalse();
    assertThat(capturedUacQidLink.isReceiptReceived()).isTrue();
    // assertThat(capturedUacQidLink.getCaze().isReceiptReceived()).isTrue();
  }

  @Test
  public void testHandleReceiptEventNoCase() {
    // Given
    EventDTO receiptEvent = new EventDTO();
    receiptEvent.setHeader(new EventHeaderDTO());
    receiptEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    receiptEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    receiptEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    receiptEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    receiptEvent.getHeader().setTopic("Test topic");
    receiptEvent.getHeader().setChannel("RH");
    receiptEvent.getHeader().setMessageType(EventType.RECEIPT);
    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(TEST_QID_ID);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    receiptEvent.setPayload(payloadDTO);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(TEST_QID_ID);
    expectedUacQidLink.setActive(true);
    expectedUacQidLink.setReceiptReceived(false);

    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);
    when(uacService.saveAndEmitUacUpdateEvent(
            expectedUacQidLink,
            receiptEvent.getHeader().getCorrelationId(),
            receiptEvent.getHeader().getOriginatingUser()))
        .thenReturn(expectedUacQidLink);

    // When
    underTest.processReceiptEvent(receiptEvent);

    // Then
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink capturedUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(capturedUacQidLink.isReceiptReceived()).isTrue();
    assertThat(capturedUacQidLink.isActive()).isFalse();

    verifyNoInteractions(caseReceiptService);
  }
}
