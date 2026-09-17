package uk.gov.ons.census.caseprocessor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.test.util.ReflectionTestUtils;
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
  void shouldRetryThreeTotalInvocations() {
    MessageConsumerConfig underTest =
        new MessageConsumerConfig(managedMessageRecoverer, pubSubTemplate);

    RequestHandlerRetryAdvice retryAdvice = underTest.retryAdvice();
    RetryTemplate retryTemplate =
        (RetryTemplate)
            Objects.requireNonNull(ReflectionTestUtils.getField(retryAdvice, "retryTemplate"));
    AtomicInteger attempts = new AtomicInteger();

    assertThrows(
        RetryException.class,
        () ->
            retryTemplate.execute(
                () -> {
                  attempts.incrementAndGet();
                  throw new IllegalStateException("test");
                }));

    assertThat(attempts).hasValue(3);
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
