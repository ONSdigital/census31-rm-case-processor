package uk.gov.ons.census.caseprocessor.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;

public class EventHelperTest {

  @Test
  public void testCreateEventDTOWithEventType() {
    EventHeaderDTO eventHeader =
        EventHelper.createEventDTO("TOPIC", TEST_CORRELATION_ID, TEST_ORIGINATING_USER);

    assertThat(eventHeader.getVersion()).isEqualTo(OUTBOUND_EVENT_SCHEMA_VERSION);
    assertThat(eventHeader.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(eventHeader.getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);
    assertThat(eventHeader.getTopic()).isEqualTo("TOPIC");
    assertThat(eventHeader.getChannel()).isEqualTo("RM");
    assertThat(eventHeader.getSource()).isEqualTo("CASE_PROCESSOR");
    assertThat(eventHeader.getDateTime()).isInstanceOf(OffsetDateTime.class);
    assertThat(eventHeader.getMessageId()).isInstanceOf(UUID.class);
  }

  @Test
  public void testCreateEventDTOWithEventTypeChannelAndSource() {
    EventHeaderDTO eventHeader =
        EventHelper.createEventDTO(
            "TOPIC", "CHANNEL", "SOURCE", TEST_CORRELATION_ID, TEST_ORIGINATING_USER, null);

    assertThat(eventHeader.getVersion()).isEqualTo(OUTBOUND_EVENT_SCHEMA_VERSION);
    assertThat(eventHeader.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(eventHeader.getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);
    assertThat(eventHeader.getChannel()).isEqualTo("CHANNEL");
    assertThat(eventHeader.getSource()).isEqualTo("SOURCE");
    assertThat(eventHeader.getDateTime()).isInstanceOf(OffsetDateTime.class);
    assertThat(eventHeader.getMessageId()).isInstanceOf(UUID.class);
    assertThat(eventHeader.getTopic()).isEqualTo("TOPIC");
  }

  @Test
  public void testGetDummyEvent() {
    EventHeaderDTO eventHeader =
        EventHelper.getDummyEvent(TEST_CORRELATION_ID, TEST_ORIGINATING_USER).getHeader();

    assertThat(eventHeader.getChannel()).isEqualTo("RM");
    assertThat(eventHeader.getSource()).isEqualTo("CASE_PROCESSOR");
    assertThat(eventHeader.getMessageId()).isInstanceOf(UUID.class);
    assertThat(eventHeader.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(eventHeader.getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);
    assertThat(eventHeader.getDateTime()).isNotNull();
    assertThat(eventHeader.getDateTime()).isInstanceOf(OffsetDateTime.class);
  }
}
