package uk.gov.ons.census.caseprocessor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SmsRequest;
import uk.gov.ons.census.common.model.entity.FulfilmentToProcess;

class RedactHelperTest {
  @Test
  void testRedactWorksForMap() {
    // GIVEN
    FulfilmentToProcess fulfilmentToProcess = new FulfilmentToProcess();

    fulfilmentToProcess.setPersonalisation(Map.of("PHONE_NUMBER", "999999"));

    // WHEN
    // Cast the object back to it's original type, just for the test
    FulfilmentToProcess fulfilmentToProcessRedacted =
        (FulfilmentToProcess) RedactHelper.redact(fulfilmentToProcess);

    // THEN
    assertThat(fulfilmentToProcessRedacted.getPersonalisation())
        .isEqualTo(Map.of("PHONE_NUMBER", "REDACTED"));

    // Extra check to make sure the original object wasn't accidentally mutated
    assertThat(fulfilmentToProcess.getPersonalisation())
        .isEqualTo(Map.of("PHONE_NUMBER", "999999"));
  }

  @Test
  void testRedactWorksForString() {
    // GIVEN
    SmsRequest smsRequest = new SmsRequest();

    smsRequest.setPhoneNumber("SUPER SECRET VALUE");

    PayloadDTO payloadDto = new PayloadDTO();
    payloadDto.setSmsRequest(smsRequest);

    EventDTO eventDto = new EventDTO();
    eventDto.setPayload(payloadDto);

    // WHEN
    // Cast the object back to it's original type, just for the test
    EventDTO eventDeepCopy = (EventDTO) RedactHelper.redact(eventDto);

    // THEN
    assertThat(eventDeepCopy.getPayload().getSmsRequest().getPhoneNumber()).isEqualTo("REDACTED");

    // Extra check to make sure the original object wasn't accidentally mutated
    assertThat(eventDto.getPayload().getSmsRequest().getPhoneNumber())
        .isEqualTo("SUPER SECRET VALUE");
  }
}
