package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class NewCase {
  private UUID caseId;

  private UUID collectionExerciseId;

  // Sample Fields
  private String uprn;
  private String estabUprn;
  private String addressType;
  private String estabType;
  private String addressLevel;
  private String abpCode;
  private String organisationName="";  // Defaulted to prevent nulls, is this the best approach?
  private String addressLine1;
  private String addressLine2="";
  private String addressLine3="";
  private String townName;
  private String postcode;
  private String latitude;
  private String longitude;
  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String region;
  private String htcWillingness;
  private String htcDigital;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private String treatmentCode;
  private Integer ceExpectedCapacity;
  private boolean secureEstablishment;
  private String printBatch;
}
