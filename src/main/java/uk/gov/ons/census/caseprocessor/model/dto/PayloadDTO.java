package uk.gov.ons.census.caseprocessor.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class PayloadDTO {
  private ResponseDTO response;
  private RefusalDTO refusal;
  private CaseUpdateDTO caseUpdate;
  private UacUpdateDTO uacUpdate;
  private InvalidAddress invalidAddress;
  private DeactivateUacDTO deactivateUac;
  private SurveyLaunchedDTO surveyLaunched;
  private SmsConfirmation smsConfirmation;
  private EmailConfirmation emailConfirmation;
  private NewCase newCase;
  private EmailRequest emailRequest;
  private ExportFileDTO exportFile;
  private RespondentAuthenticatedDTO respondentAuthenticated;
  private SmsRequestEnriched smsRequestEnriched;
  private FulfilmentRequest fulfilmentRequest;
}
