package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.CloudTaskMessage;
import uk.gov.ons.census.caseprocessor.model.dto.CloudTaskType;
import uk.gov.ons.census.caseprocessor.model.dto.EqFlushTaskPayload;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
class EqFlushProcessorTest {

  @Mock private MessageSender messageSender;

  @InjectMocks private EqFlushProcessor underTest;

  @Test
  void testProcessEqFlushRow() {
    // Given
    ReflectionTestUtils.setField(underTest, "cloudTaskQueueTopic", "testTopic");

    Case caze = new Case();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid("0123456789");
    uacQidLink.setReceiptReceived(false);
    uacQidLink.setSurveyLaunched(true);
    caze.setUacQidLinks(List.of(uacQidLink));

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setCreatedBy("foo@bar.com");

    // When
    underTest.process(caze, actionRule);

    // Then
    ArgumentCaptor<CloudTaskMessage> messageArgumentCaptor =
        ArgumentCaptor.forClass(CloudTaskMessage.class);
    verify(messageSender).sendMessage(eq("testTopic"), messageArgumentCaptor.capture());

    assertThat(messageArgumentCaptor.getValue().getCloudTaskType())
        .isEqualTo(CloudTaskType.EQ_FLUSH);
    assertThat(messageArgumentCaptor.getValue().getCorrelationId()).isEqualTo(actionRule.getId());

    EqFlushTaskPayload cloudTaskPayload =
        (EqFlushTaskPayload) messageArgumentCaptor.getValue().getPayload();
    assertThat(cloudTaskPayload.getTransactionId()).isEqualTo(actionRule.getId());
    assertThat(cloudTaskPayload.getQid()).isEqualTo(uacQidLink.getQid());
  }

  @Test
  void testProcessEqFlushRowIgnoresReceipted() {
    // Given
    ReflectionTestUtils.setField(underTest, "cloudTaskQueueTopic", "testTopic");

    Case caze = new Case();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid("0123456789");
    uacQidLink.setReceiptReceived(true);
    uacQidLink.setSurveyLaunched(true);
    caze.setUacQidLinks(List.of(uacQidLink));

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setCreatedBy("foo@bar.com");

    // When
    underTest.process(caze, actionRule);

    // Then
    verify(messageSender, never()).sendMessage(any(), any());
  }

  @Test
  void testProcessEqFlushRowIgnoresNotLaunched() {
    // Given
    ReflectionTestUtils.setField(underTest, "cloudTaskQueueTopic", "testTopic");

    Case caze = new Case();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid("0123456789");
    uacQidLink.setReceiptReceived(false);
    uacQidLink.setSurveyLaunched(false);
    caze.setUacQidLinks(List.of(uacQidLink));

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setCreatedBy("foo@bar.com");

    // When
    underTest.process(caze, actionRule);

    // Then
    verify(messageSender, never()).sendMessage(any(), any());
  }
}
