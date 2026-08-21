package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class Contact {
  private String title;
  private String forename;
  private String surname;
  private String telNo;

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("title", this.title);
    map.put("forename", this.forename);
    map.put("surname", this.surname);
    map.put("telNo", this.telNo);
    return map;
  }
}
