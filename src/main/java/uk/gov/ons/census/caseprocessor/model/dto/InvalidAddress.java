package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class InvalidAddress {
  private UUID caseId;
  private InvalidAddressReason reason;
  private String notes;
}
