package uk.gov.ons.census.caseprocessor.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EqReceiptDTO {
  @JsonProperty("tx_id")
  private String txId;

  @JsonProperty("questionnaire_id")
  private String questionnaireId;
}
