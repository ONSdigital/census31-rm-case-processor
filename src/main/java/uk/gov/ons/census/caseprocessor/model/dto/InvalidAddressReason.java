package uk.gov.ons.census.caseprocessor.model.dto;

public enum InvalidAddressReason {
  SPLIT_ADDRESS,
  DERELICT,
  DEMOLISHED,
  CANT_FIND,
  UNADDRESSABLE_OBJECT,
  NON_RESIDENTIAL,
  DUPLICATE,
  UNDER_CONSTRUCTION,
  DOES_NOT_EXIST
}
