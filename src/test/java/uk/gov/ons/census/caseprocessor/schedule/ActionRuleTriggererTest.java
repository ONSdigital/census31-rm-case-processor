package uk.gov.ons.census.caseprocessor.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.UnknownHostException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleRepository;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.ActionRuleStatus;
import uk.gov.ons.census.common.model.entity.ActionRuleType;
import uk.gov.ons.census.common.model.entity.ExportFileTemplate;

class ActionRuleTriggererTest {
  private final ActionRuleRepository actionRuleRepository = mock(ActionRuleRepository.class);
  private final ActionRuleProcessor actionRuleProcessor = mock(ActionRuleProcessor.class);

  @Test
  void testTriggerActionRule() throws UnknownHostException {
    // Given
    ActionRule actionRule = new ActionRule();
    when(actionRuleRepository.findByTriggerDateTimeBeforeAndHasTriggeredIsFalse(
            any(OffsetDateTime.class)))
        .thenReturn(Collections.singletonList(actionRule));

    // When
    ActionRuleTriggerer underTest =
        new ActionRuleTriggerer(actionRuleRepository, actionRuleProcessor);
    underTest.triggerAllActionRules();

    // Then
    verify(actionRuleProcessor).processTriggeredActionRule(actionRule);
    verify(actionRuleProcessor)
        .updateActionRuleStatus(actionRule, ActionRuleStatus.SELECTING_CASES);
  }

  @Test
  void testTriggerMultipleActionRule() throws UnknownHostException {
    // Given
    List<ActionRule> actionRules = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      actionRules.add(new ActionRule());
    }

    when(actionRuleRepository.findByTriggerDateTimeBeforeAndHasTriggeredIsFalse(
            any(OffsetDateTime.class)))
        .thenReturn(actionRules);

    // When
    ActionRuleTriggerer underTest =
        new ActionRuleTriggerer(actionRuleRepository, actionRuleProcessor);
    underTest.triggerAllActionRules();

    // Then
    verify(actionRuleProcessor, times(50))
        .updateActionRuleStatus(any(ActionRule.class), eq(ActionRuleStatus.SELECTING_CASES));
    verify(actionRuleProcessor, times(50)).processTriggeredActionRule(any(ActionRule.class));
  }

  @Test
  void testNullQuestionnaireTypeButUacTemplate() throws UnknownHostException {
    // Given
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "ADDRESS_LINE1", "__uac__"});
    exportFileTemplate.setPackCode("Packcode");
    exportFileTemplate.setExportFileDestination("test-export-file-destination");
    exportFileTemplate.setDescription("Test description");
    exportFileTemplate.setQuestionnaireType(null);

    ActionRule actionRule = new ActionRule();
    actionRule.setHasTriggered(false);
    actionRule.setType(ActionRuleType.EXPORT_FILE);
    actionRule.setExportFileTemplate(exportFileTemplate);

    when(actionRuleRepository.findByTriggerDateTimeBeforeAndHasTriggeredIsFalse(
            any(OffsetDateTime.class)))
        .thenReturn(Collections.singletonList(actionRule));

    // When
    ActionRuleTriggerer underTest =
        new ActionRuleTriggerer(actionRuleRepository, actionRuleProcessor);
    underTest.triggerAllActionRules();

    // Then
    assertThat(actionRule.isHasTriggered()).isTrue();
    assertThat(actionRule.getActionRuleStatus()).isEqualTo(ActionRuleStatus.ERRORED);
    verify(actionRuleRepository).save(actionRule); // Verify it was saved
  }

  @Test
  void badSqlGrammarExceptionHandled() throws UnknownHostException {
    // Given
    ActionRule actionRule = new ActionRule();
    actionRule.setHasTriggered(false);

    when(actionRuleRepository.findByTriggerDateTimeBeforeAndHasTriggeredIsFalse(
            any(OffsetDateTime.class)))
        .thenReturn(Collections.singletonList(actionRule));

    SQLException sqlException = new SQLException("rubbish SQL", "Broken", -137);
    BadSqlGrammarException badSqlGrammarException =
        new BadSqlGrammarException("A task", "rubbish SQL", sqlException);

    doThrow(badSqlGrammarException).when(actionRuleProcessor).processTriggeredActionRule(any());

    // When
    ActionRuleTriggerer underTest =
        new ActionRuleTriggerer(actionRuleRepository, actionRuleProcessor);
    underTest.triggerAllActionRules();

    // Then
    verify(actionRuleProcessor).processTriggeredActionRule(actionRule);
    assertThat(actionRule.isHasTriggered()).isTrue();
  }
}
