package uk.gov.ons.census.caseprocessor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QidFormTypeHelperTest {

  @ParameterizedTest
  @CsvSource({
    "0100000001,H",
    "0200000001,H",
    "0300000001,H",
    "0400000001,H",
    "0500000001,H",
    "0600000001,HA",
    "0700000001,HB",
    "2100000001,I",
    "2200000001,I",
    "2300000001,I",
    "2400000001,I",
    "2500000001,I",
    "2600000001,IA",
    "2700000001,IB"
  })
  void shouldMapQidToFormType(String qid, String expectedFormType) {
    assertThat(QidFormTypeHelper.mapQidToFormType(qid)).isEqualTo(expectedFormType);
  }

  @Test
  void shouldReturnNullForUnknownQidType() {
    assertThat(QidFormTypeHelper.mapQidToFormType("9900000001")).isNull();
  }

  @Test
  void shouldReturnNullForNullQid() {
    assertThat(QidFormTypeHelper.mapQidToFormType(null)).isNull();
  }

  @Test
  void shouldReturnNullForShortQid() {
    assertThat(QidFormTypeHelper.mapQidToFormType("1")).isNull();
  }

  @Test
  void shouldReturnNullForNonNumericPrefix() {
    assertThat(QidFormTypeHelper.mapQidToFormType("AB123456")).isNull();
  }
}
