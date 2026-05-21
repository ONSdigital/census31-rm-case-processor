package uk.gov.ons.census.caseprocessor.model.dto;

import lombok.Data;

@Data
public class Address {
  private String addressLine1;
  private String addressLine2;
  private String addressLine3;
  private String townName;
  private String postcode;
  private String region;
  private String latitude;
  private String longitude;
  private String uprn;
  private String estabUprn;
  private String abpCode;
  private String addressType;
  private String addressLevel;
  private String estabType;
  private String organisationName;
  private Boolean secureType;
}
