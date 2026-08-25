package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class FulfilmentRequest {
  private UUID caseId;
  private String fulfilmentCode;
  private Contact contact;
}
