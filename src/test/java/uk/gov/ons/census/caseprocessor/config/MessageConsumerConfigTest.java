package uk.gov.ons.census.caseprocessor.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryListener;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import uk.gov.ons.census.caseprocessor.messaging.ManagedMessageRecoverer;

@ExtendWith(MockitoExtension.class)
class MessageConsumerConfigTest {

  @Mock private ManagedMessageRecoverer managedMessageRecoverer;

  @Mock private PubSubTemplate pubSubTemplate;

  @Test
  void shouldCreateRetryAdviceUsingRecovererCallback() {
    MessageConsumerConfig underTest =
        new MessageConsumerConfig(managedMessageRecoverer, pubSubTemplate);

    RequestHandlerRetryAdvice retryAdvice = underTest.retryAdvice();

    assertThat(
            org.springframework.test.util.ReflectionTestUtils.getField(
                retryAdvice, "recoveryCallback"))
        .isEqualTo(managedMessageRecoverer);
  }

  @Test
  void shouldExposeDefaultListenerSupportAsTheCoreRetryListener() {
    MessageConsumerConfig underTest =
        new MessageConsumerConfig(managedMessageRecoverer, pubSubTemplate);

    RetryListener retryListener = underTest.retryListener();

    assertThat(retryListener).isInstanceOf(DefaultListenerSupport.class);
    assertThat(retryListener).isInstanceOf(org.springframework.core.retry.RetryListener.class);
    assertThat(retryListener).isInstanceOf(org.springframework.retry.RetryListener.class);
  }
}
