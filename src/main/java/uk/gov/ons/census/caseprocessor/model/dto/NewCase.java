package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;
import uk.gov.ons.census.common.model.entity.SampleField;

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
  private String organisationName = ""; // Defaulted to prevent nulls, is this the best approach?
  private String addressLine1;
  private String addressLine2 = "";
  private String addressLine3 = "";
  private String townName;
  private String postcode;
  private String latitude;
  private String longitude;
  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String region;
  private String htc;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private String treatmentCode;
  private Integer ceExpectedCapacity;
  private boolean secureEstablishment;
  private String printBatch;

  public Object getSampleFieldValue(SampleField sampleField) {
    return switch (sampleField) {
      case UPRN -> this.getUprn();
      case ESTAB_UPRN -> this.getEstabUprn();
      case ESTAB_TYPE -> this.getEstabType();
      case ADDRESS_TYPE -> this.getAddressType();
      case ABP_CODE -> this.getAbpCode();
      case ORGANISATION_NAME -> this.getOrganisationName();
      case ADDRESS_LINE1 -> this.getAddressLine1();
      case ADDRESS_LINE2 -> this.getAddressLine2();
      case ADDRESS_LINE3 -> this.getAddressLine3();
      case ADDRESS_LEVEL -> this.getAddressLevel();
      case TOWN_NAME -> this.getTownName();
      case POSTCODE -> this.getPostcode();
      case LATITUDE -> this.getLatitude();
      case LONGITUDE -> this.getLongitude();
      case OA -> this.getOa();
      case LSOA -> this.getLsoa();
      case MSOA -> this.getMsoa();
      case LAD -> this.getLad();
      case REGION -> this.getRegion();
      case HTC -> this.getHtc();
      case TREATMENT_CODE -> this.getTreatmentCode();
      case FIELDCOORDINATOR_ID -> this.getFieldCoordinatorId();
      case FIELDOFFICER_ID -> this.getFieldOfficerId();
      case CE_EXPECTED_CAPACITY -> this.getCeExpectedCapacity();
      case PRINT_BATCH -> this.getPrintBatch();
      case CE_SECURE -> this.isSecureEstablishment();
    };
  }
}
