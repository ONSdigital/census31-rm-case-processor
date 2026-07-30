package uk.gov.ons.census.caseprocessor.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RespondentAuthenticatedDTO {
  private String questionnaireId;
}
