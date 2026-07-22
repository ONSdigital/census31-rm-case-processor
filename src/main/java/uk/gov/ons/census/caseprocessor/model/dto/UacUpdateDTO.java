package uk.gov.ons.census.caseprocessor.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.UUID;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class UacUpdateDTO {
  private String uacHash;
  private boolean active;
  private String qid;

  @JsonInclude(Include.ALWAYS)
  private UUID caseId;

  private UUID collectionExerciseId;
  private UUID surveyId;
  private boolean receiptReceived;
  private boolean eqLaunched;
  private String collectionInstrumentUrl;
}
