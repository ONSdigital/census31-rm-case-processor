package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.ReceiptDTO;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
class CaseReceiptServiceTest {

  @Mock private CaseService caseService;

  @InjectMocks private CaseReceiptService underTest;

  @Test
  void receiptCaseUpdatesHouseholdCaseAndEmitsUpdate() {
    UacQidLink uacQidLink = buildLink("0112345678901234", "HH");

    UacQidLink result = underTest.receiptCase(uacQidLink, buildReceiptEvent("0112345678901234"));

    assertThat(result).isSameAs(uacQidLink);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdate(
            caseCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));

    assertThat(caseCaptor.getValue().isReceiptReceived()).isTrue();
  }

  @Test
  void receiptCaseUpdatesHICaseAndEmitsUpdate() {
    UacQidLink uacQidLink = buildLink("0112345678901234", "HI");

    UacQidLink result = underTest.receiptCase(uacQidLink, buildReceiptEvent("0112345678901234"));

    assertThat(result).isSameAs(uacQidLink);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdate(
            caseCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));

    assertThat(caseCaptor.getValue().isReceiptReceived()).isTrue();
  }

  @Test
  void receiptCaseDoesNothingForNoActionRule() {
    UacQidLink uacQidLink = buildLink("2112345678901234", "HH");

    UacQidLink result = underTest.receiptCase(uacQidLink, buildReceiptEvent("2112345678901234"));

    assertThat(result).isSameAs(uacQidLink);
    assertThat(uacQidLink.getCaze().isReceiptReceived()).isFalse();

    verifyNoInteractions(caseService);
  }

  @Test
  void receiptCaseThrowsWhenNoRuleMatches() {
    UacQidLink uacQidLink = buildLink("9912345678901234", "HH");

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> underTest.receiptCase(uacQidLink, buildReceiptEvent("9912345678901234")));

    assertThat(thrown.getMessage()).contains("does not map to any known processing rule");
    verifyNoInteractions(caseService);
  }

  private UacQidLink buildLink(String qid, String caseType) {
    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel("U");
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLink.setReceiptReceived(false);
    return uacQidLink;
  }

  private EventDTO buildReceiptEvent(String qid) {
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
    receiptDTO.setQid(qid);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    receiptEvent.setPayload(payloadDTO);
    return receiptEvent;
  }
}
