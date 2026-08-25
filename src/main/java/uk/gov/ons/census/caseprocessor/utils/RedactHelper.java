package uk.gov.ons.census.caseprocessor.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.StringUtils;

public class RedactHelper {

  private static final String REDACTION_FAILURE = "Failed to redact sensitive data";
  private static final String REDACTION_TEXT = "REDACTED";
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  private static final ThingToRedact[] THINGS_TO_REDACT = {
    new ThingToRedact("setUac", String.class),
    new ThingToRedact("setPhoneNumber", String.class),
    new ThingToRedact("setEmail", String.class),
    new ThingToRedact("getPersonalisation", Map.class),
    new ThingToRedact("setForename", String.class),
    new ThingToRedact("setSurname", String.class),
    new ThingToRedact("setTelNo", String.class),
    new ThingToRedact("setTitle", String.class),
  };

  public static Object redact(Object rootObjectToRedact) {
    if (rootObjectToRedact == null) {
      return null; // can't redact null!
    }

    try {
      Object rootObjectToRedactDeepCopy =
          objectMapper.readValue(
              objectMapper.writeValueAsString(rootObjectToRedact), rootObjectToRedact.getClass());
      recursivelyRedact(
          rootObjectToRedactDeepCopy, rootObjectToRedactDeepCopy.getClass().getPackageName());
      return rootObjectToRedactDeepCopy;
    } catch (JsonProcessingException e) {
      throw new RuntimeException(REDACTION_FAILURE, e);
    }
  }

  private static void recursivelyRedact(Object object, String packageName) {
    Arrays.stream(object.getClass().getMethods())
        .filter(item -> Modifier.isPublic(item.getModifiers()))
        .forEach(
            method -> {
              if (method.getName().startsWith("get")
                  && method.getReturnType().getPackageName().equals(packageName)) {
                try {
                  Object invokeResult = method.invoke(object);
                  if (invokeResult != null) {
                    recursivelyRedact(invokeResult, packageName);
                  }
                } catch (IllegalAccessException | InvocationTargetException e) {
                  throw new RuntimeException(REDACTION_FAILURE, e);
                }
              }

              for (ThingToRedact thingToRedact : THINGS_TO_REDACT) {
                redactMethod(object, method, thingToRedact);
              }
            });
  }

  private static void redactMethod(Object object, Method method, ThingToRedact thingToRedact) {
    if (!method.getName().equals(thingToRedact.getMethodName())) {
      return;
    }

    if (thingToRedact.getThingToRedactType() == Map.class
        && method.getReturnType().equals(Map.class)) {
      try {
        Map<String, String> sensitiveData = (Map<String, String>) method.invoke(object);
        if (sensitiveData == null) {
          return;
        }
        for (String key : sensitiveData.keySet()) {
          if (StringUtils.hasText((sensitiveData.get(key)))) {
            sensitiveData.put(key, REDACTION_TEXT);
          }
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(REDACTION_FAILURE, e);
      }
    } else if (thingToRedact.getThingToRedactType() == String.class
        && method.getParameterTypes().length == 1
        && method.getParameterTypes()[0].equals(String.class)) {
      try {
        method.invoke(object, REDACTION_TEXT);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(REDACTION_FAILURE, e);
      }
    }
  }

  @Data
  @AllArgsConstructor
  private static class ThingToRedact {
    private String methodName;
    private Class thingToRedactType;
  }
}
