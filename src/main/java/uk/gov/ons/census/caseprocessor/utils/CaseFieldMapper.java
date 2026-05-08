package uk.gov.ons.census.caseprocessor.utils;

import uk.gov.ons.census.caseprocessor.model.dto.Address;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.dto.NewCase;
import uk.gov.ons.census.common.model.entity.Case;

public class CaseFieldMapper {

  public static void mapPayloadSampleFieldsToNewCase(NewCase newCasePayload, Case newCase) {
    newCase.setUprn(newCasePayload.getUprn());
    newCase.setEstabUprn(newCasePayload.getEstabUprn());
    newCase.setCaseType(newCasePayload.getAddressType()); // Case type is set from address ype, do we need both?
    newCase.setAddressType(newCasePayload.getAddressType());
    newCase.setEstabType(newCasePayload.getEstabType());
    newCase.setAddressLevel(newCasePayload.getAddressLevel());
    newCase.setAbpCode(newCasePayload.getAbpCode());
    newCase.setOrganisationName(newCasePayload.getOrganisationName());
    newCase.setAddressLine1(newCasePayload.getAddressLine1());
    newCase.setAddressLine2(newCasePayload.getAddressLine2());
    newCase.setAddressLine3(newCasePayload.getAddressLine3());
    newCase.setTownName(newCasePayload.getTownName());
    newCase.setPostcode(newCasePayload.getPostcode());
    newCase.setLatitude(newCasePayload.getLatitude());
    newCase.setLongitude(newCasePayload.getLongitude());
    newCase.setOa(newCasePayload.getOa());
    newCase.setLsoa(newCasePayload.getLsoa());
    newCase.setMsoa(newCasePayload.getMsoa());
    newCase.setLad(newCasePayload.getLad());
    newCase.setRegion(newCasePayload.getRegion());
    newCase.setHtcWillingness(newCasePayload.getHtcWillingness());
    newCase.setHtcDigital(newCasePayload.getHtcDigital());
    newCase.setFieldCoordinatorId(newCasePayload.getFieldCoordinatorId());
    newCase.setFieldOfficerId(newCasePayload.getFieldOfficerId());
    newCase.setTreatmentCode(newCasePayload.getTreatmentCode());
    newCase.setCeExpectedCapacity(newCasePayload.getCeExpectedCapacity());
    newCase.setSecureEstablishment(newCasePayload.isSecureEstablishment());
  }

  public static void mapCaseSampleFieldsToCaseUpdateDTO(Case caze, CaseUpdateDTO caseUpdate) {
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setTownName(caze.getTownName());
    address.setPostcode(caze.getPostcode());
    address.setRegion(caze.getRegion());
    address.setLatitude(caze.getLatitude());
    address.setLongitude(caze.getLongitude());
    address.setUprn(caze.getUprn());
    address.setEstabUprn(caze.getEstabUprn());
    address.setAbpCode(caze.getAbpCode());
    address.setAddressType(caze.getAddressType());
    address.setAddressLevel(caze.getAddressLevel());
    address.setEstabType(caze.getEstabType());
    address.setOrganisationName(caze.getOrganisationName());
    address.setSecureType(false);

    caseUpdate.setAddress(address);

    caseUpdate.setOa(caze.getOa());
    caseUpdate.setLsoa(caze.getLsoa());
    caseUpdate.setMsoa(caze.getMsoa());
    caseUpdate.setLad(caze.getLad());
    caseUpdate.setCeExpectedCapacity(caze.getCeExpectedCapacity());
    caseUpdate.setTreatmentCode(caze.getTreatmentCode());
    caseUpdate.setHtcWillingness(caze.getHtcWillingness());
    caseUpdate.setHtcDigital(caze.getHtcDigital());
    caseUpdate.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    caseUpdate.setFieldOfficerId(caze.getFieldOfficerId());
    caseUpdate.setCaseType(caze.getCaseType());
  }
}
