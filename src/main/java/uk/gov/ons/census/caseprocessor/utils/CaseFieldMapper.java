package uk.gov.ons.census.caseprocessor.utils;

import uk.gov.ons.census.caseprocessor.model.dto.Address;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.dto.NewCase;
import uk.gov.ons.census.common.model.entity.Case;

public class CaseFieldMapper {

  public static void mapPayloadSampleFieldsToCase(NewCase newCasePayload, Case caze) {
    caze.setUprn(newCasePayload.getUprn());
    caze.setEstabUprn(newCasePayload.getEstabUprn());
    caze.setCaseType(newCasePayload.getAddressType());
    caze.setAddressType(newCasePayload.getAddressType());
    caze.setEstabType(newCasePayload.getEstabType());
    caze.setAddressLevel(newCasePayload.getAddressLevel());
    caze.setAbpCode(newCasePayload.getAbpCode());
    caze.setOrganisationName(newCasePayload.getOrganisationName());
    caze.setAddressLine1(newCasePayload.getAddressLine1());
    caze.setAddressLine2(newCasePayload.getAddressLine2());
    caze.setAddressLine3(newCasePayload.getAddressLine3());
    caze.setTownName(newCasePayload.getTownName());
    caze.setPostcode(newCasePayload.getPostcode());
    caze.setLatitude(newCasePayload.getLatitude());
    caze.setLongitude(newCasePayload.getLongitude());
    caze.setOa(newCasePayload.getOa());
    caze.setLsoa(newCasePayload.getLsoa());
    caze.setMsoa(newCasePayload.getMsoa());
    caze.setLad(newCasePayload.getLad());
    caze.setRegion(newCasePayload.getRegion());
    caze.setHtcWillingness(newCasePayload.getHtcWillingness());
    caze.setHtcDigital(newCasePayload.getHtcDigital());
    caze.setFieldCoordinatorId(newCasePayload.getFieldCoordinatorId());
    caze.setFieldOfficerId(newCasePayload.getFieldOfficerId());
    caze.setTreatmentCode(newCasePayload.getTreatmentCode());
    caze.setCeExpectedCapacity(newCasePayload.getCeExpectedCapacity());
    caze.setSecureEstablishment(newCasePayload.isSecureEstablishment());
    caze.setPrintBatch(newCasePayload.getPrintBatch());
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
    caseUpdate.setPrintBatch(caze.getPrintBatch());
  }
}
