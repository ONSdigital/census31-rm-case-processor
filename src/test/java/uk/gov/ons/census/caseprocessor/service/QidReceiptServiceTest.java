package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  private static final String TEST_QID_ID = "1234567890123456";

  @Mock private UacService uacService;
  @Mock private CaseReceiptService caseReceiptService;

  @InjectMocks QidReceiptService underTest;

  @ParameterizedTest(name = "processes receipt when case exists and channel is {0}")
  @MethodSource("allowedChannels")
  public void testHandleReceiptEventWithCaseForAllowedChannels(String channel) {
    // Given
    EventDTO receiptEvent = buildReceiptEvent(channel);
    UacQidLink expectedUacQidLink = buildUacQidLink(false, true, new Case());

    stubLookupAndSave(expectedUacQidLink, receiptEvent);
    when(caseReceiptService.receiptCase(expectedUacQidLink, receiptEvent))
        .thenReturn(expectedUacQidLink);

    // When
    underTest.processReceiptEvent(receiptEvent);

    // Then
    assertSavedAsReceiptedAndInactive();
    verify(caseReceiptService).receiptCase(expectedUacQidLink, receiptEvent);
  }

  @Test
  public void testHandleReceiptEventNoCase() {
    // Given
    EventDTO receiptEvent = buildReceiptEvent("RH");
    UacQidLink expectedUacQidLink = buildUacQidLink(false, true, null);

    stubLookupAndSave(expectedUacQidLink, receiptEvent);

    // When
    underTest.processReceiptEvent(receiptEvent);

    // Then
    assertSavedAsReceiptedAndInactive();
    verifyNoInteractions(caseReceiptService);
  }

  @Test
  public void testHandleReceiptEventAlreadyReceiptedDoesNothing() {
    // Given
    EventDTO receiptEvent = buildReceiptEvent("EQ");
    UacQidLink existingUacQidLink = buildUacQidLink(true, false, new Case());
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(existingUacQidLink);

    // When
    UacQidLink result = underTest.processReceiptEvent(receiptEvent);

    // Then
    assertThat(result).isSameAs(existingUacQidLink);
    verify(uacService, never()).saveAndEmitUacUpdateEvent(any(), any(), anyString());
    verifyNoInteractions(caseReceiptService);
  }

  private EventDTO buildReceiptEvent(String channel) {
    EventDTO receiptEvent = new EventDTO();
    receiptEvent.setHeader(new EventHeaderDTO());
    receiptEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    receiptEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    receiptEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    receiptEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    receiptEvent.getHeader().setTopic("Test topic");
    receiptEvent.getHeader().setChannel(channel);
    receiptEvent.getHeader().setMessageType(EventType.RECEIPT);

    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(TEST_QID_ID);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    receiptEvent.setPayload(payloadDTO);

    return receiptEvent;
  }

  private UacQidLink buildUacQidLink(boolean receiptReceived, boolean active, Case caze) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(TEST_QID_ID);
    uacQidLink.setReceiptReceived(receiptReceived);
    uacQidLink.setActive(active);
    uacQidLink.setCaze(caze);
    return uacQidLink;
  }

  private void stubLookupAndSave(UacQidLink uacQidLink, EventDTO receiptEvent) {
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(uacQidLink);
    when(uacService.saveAndEmitUacUpdateEvent(
            uacQidLink,
            receiptEvent.getHeader().getCorrelationId(),
            receiptEvent.getHeader().getOriginatingUser()))
        .thenReturn(uacQidLink);
  }

  private void assertSavedAsReceiptedAndInactive() {
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));

    UacQidLink capturedUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(capturedUacQidLink.isReceiptReceived()).isTrue();
    assertThat(capturedUacQidLink.isActive()).isFalse();
  }

  private static Stream<Arguments> allowedChannels() {
    return Stream.of(Arguments.of("RH"), Arguments.of("EQ"), Arguments.of((String) null));
  }
}
