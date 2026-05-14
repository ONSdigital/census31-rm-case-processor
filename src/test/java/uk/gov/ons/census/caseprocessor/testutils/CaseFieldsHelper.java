package uk.gov.ons.census.caseprocessor.testutils;

import uk.gov.ons.census.common.model.entity.Case;

public class CaseFieldsHelper {

  public static Case setDummyCaseFields(Case caseToUpdate) {
    caseToUpdate.setCaseRef(123L);
    caseToUpdate.setTreatmentCode("HH_QP3E");
    caseToUpdate.setAddressType("H");
    caseToUpdate.setUprn("1234567890");
    caseToUpdate.setEstabUprn("1234567890");
    caseToUpdate.setEstabType("HOUSEHOLD");
    caseToUpdate.setAddressLine1("123 Fake Street");
    caseToUpdate.setTownName("Testington");
    caseToUpdate.setRegion("E");
    caseToUpdate.setPostcode("NP10 111");
    caseToUpdate.setAddressType("HH");
    caseToUpdate.setAddressLevel("U");
    caseToUpdate.setAbpCode("ABC123");
    caseToUpdate.setFieldCoordinatorId("ABCD1234");
    caseToUpdate.setFieldOfficerId("ABCD1234");
    caseToUpdate.setOa("A12345678");
    caseToUpdate.setLsoa("A12345678");
    caseToUpdate.setMsoa("A12345678");
    caseToUpdate.setLad("ABC123");
    caseToUpdate.setHtcDigital("1");
    caseToUpdate.setHtcWillingness("1");
    caseToUpdate.setLatitude("51.5074");
    caseToUpdate.setLongitude("0.1278");
    caseToUpdate.setPrintBatch("1");
    caseToUpdate.setSecureEstablishment(false);
    return caseToUpdate;
  }
}
