package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {
  @Mock private EventLogger eventLogger;
  @Mock private QidReceiptService qidReceiptService;
  @InjectMocks private ReceiptService underTest;

  @Test
  void processesAndLogsReceiptWithOriginalDeliveryTimestamp() {
    EventDTO event = new EventDTO();
    event.setHeader(new EventHeaderDTO());
    event.getHeader().setMessageType(EventType.RESPONSE_RECEIVED);
    event.setMessageTimestamp(OffsetDateTime.parse("2026-10-07T10:30:00Z"));
    UacQidLink link = new UacQidLink();
    when(qidReceiptService.processReceiptEvent(event)).thenReturn(link);

    underTest.processReceiptEvent(event);

    verify(qidReceiptService).processReceiptEvent(event);
    verify(eventLogger)
        .logUacQidEvent(
            link,
            "Receipt received",
            EventType.RESPONSE_RECEIVED,
            event,
            event.getMessageTimestamp());
  }

  @Test
  void rejectsOtherEventTypesWithoutUpdatingQidOrLogging() {
    EventDTO event = new EventDTO();
    event.setHeader(new EventHeaderDTO());
    event.getHeader().setMessageType(EventType.CASE_UPDATE);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.processReceiptEvent(event));

    assertThat(thrown.getMessage()).isEqualTo("Event Type 'CASE_UPDATE' is invalid on this topic");
    verifyNoInteractions(qidReceiptService, eventLogger);
  }
}
