package uk.gov.ons.census.caseprocessor.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class EventDTO {
  private EventHeaderDTO header;
  private PayloadDTO payload;

  @JsonIgnore private OffsetDateTime messageTimestamp;
}
