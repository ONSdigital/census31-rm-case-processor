package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class FulfilmentDTO {
  private UUID caseId;
  private String packCode;
  private Object uacMetadata;
  private Map<String, String> personalisation;
}
