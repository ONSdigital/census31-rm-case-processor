package uk.gov.ons.census.caseprocessor.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import uk.gov.ons.census.common.model.entity.EventType;

@Data
public class EventHeaderDTO {
  private String version;
  private String topic;
  private String source;
  private String channel;
  private OffsetDateTime dateTime;
  private UUID messageId;
  private UUID correlationId;
  private String originatingUser;
  private EventType messageType;
}
