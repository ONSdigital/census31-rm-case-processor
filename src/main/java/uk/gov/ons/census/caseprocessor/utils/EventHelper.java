package uk.gov.ons.census.caseprocessor.utils;

import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.util.UUID;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.common.model.entity.EventType;

public class EventHelper {

  private static final String EVENT_SOURCE = "CASE_PROCESSOR";
  private static final String EVENT_CHANNEL = "RM";

  public static EventHeaderDTO createEventDTO(
      String topic,
      String eventChannel,
      String eventSource,
      UUID correlationId,
      String originatingUser,
      EventType eventType) {
    EventHeaderDTO eventHeader = new EventHeaderDTO();

    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setChannel(eventChannel);
    eventHeader.setSource(eventSource);
    eventHeader.setDateTime(OffsetDateTime.now());
    eventHeader.setMessageId(UUID.randomUUID());
    eventHeader.setCorrelationId(correlationId);
    eventHeader.setOriginatingUser(originatingUser);
    eventHeader.setTopic(topic);
    eventHeader.setMessageType(eventType);

    return eventHeader;
  }

  public static EventHeaderDTO createEventDTO(
      String topic, UUID correlationId, String originatingUser) {
    return createEventDTO(topic, EVENT_CHANNEL, EVENT_SOURCE, correlationId, originatingUser, null);
  }

  public static EventHeaderDTO createEventDTO(
      String topic, UUID correlationId, String originatingUser, EventType eventType) {
    return createEventDTO(
        topic, EVENT_CHANNEL, EVENT_SOURCE, correlationId, originatingUser, eventType);
  }

  public static EventDTO getDummyEvent(UUID correlationId, String originatingUser) {
    return getDummyEvent(correlationId, originatingUser, null);
  }

  public static EventDTO getDummyEvent(
      UUID correlationId, String originatingUser, PayloadDTO payloadDTO) {
    EventHeaderDTO eventHeader = new EventHeaderDTO();

    eventHeader.setChannel(EVENT_CHANNEL);
    eventHeader.setSource(EVENT_SOURCE);
    eventHeader.setMessageId(UUID.randomUUID());
    eventHeader.setCorrelationId(correlationId);
    eventHeader.setOriginatingUser(originatingUser);
    eventHeader.setDateTime(OffsetDateTime.now());

    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    return event;
  }
}
