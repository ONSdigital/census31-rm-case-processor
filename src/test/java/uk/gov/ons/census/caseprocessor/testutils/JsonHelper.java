package uk.gov.ons.census.caseprocessor.testutils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.ons.census.caseprocessor.utils.ObjectMapperFactory;

public class JsonHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static <T> T convertJsonBytesToObject(byte[] bytes, Class<T> clazz) {
    try {
      return objectMapper.readValue(bytes, clazz);
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }
}
