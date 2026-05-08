package uk.gov.ons.census.caseprocessor.model.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CaseUpdateDTO {
  private UUID caseId;
  private String caseRef;
  private UUID collectionExerciseId;
  private UUID surveyId;
  private boolean invalid;
  private RefusalTypeDTO refusalReceived;
  private boolean surveyLaunched;
  private boolean receiptReceived;
  private OffsetDateTime createdAt;
  private OffsetDateTime lastUpdatedAt;

  // Sample Fields and Address
  private String caseType;

  private Address address;

  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String htcWillingness;
  private String htcDigital;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private String treatmentCode;
  private Integer ceExpectedCapacity;
  private boolean secureEstablishment;
  private String printBatch;
}
