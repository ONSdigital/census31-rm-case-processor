package uk.gov.ons.census.caseprocessor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class DefaultListenerSupportTest {
  private final DefaultListenerSupport underTest = new DefaultListenerSupport();

  @Test
  void shouldSupportLegacyRetryableCallbacks() {
    org.springframework.retry.RetryContext retryContext =
        mock(org.springframework.retry.RetryContext.class);
    @SuppressWarnings("unchecked")
    org.springframework.retry.RetryCallback<Object, RuntimeException> retryCallback =
        (org.springframework.retry.RetryCallback<Object, RuntimeException>)
            mock(org.springframework.retry.RetryCallback.class);

    assertThat(underTest.open(retryContext, retryCallback)).isTrue();
    assertThatCode(
            () -> {
              underTest.onError(retryContext, retryCallback, new RuntimeException("failure"));
              underTest.close(retryContext, retryCallback, new RuntimeException("failure"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowCoreRetryListenerDefaultsToBeCalled() {
    org.springframework.core.retry.RetryListener coreRetryListener = underTest;
    org.springframework.core.retry.RetryPolicy retryPolicy =
        mock(org.springframework.core.retry.RetryPolicy.class);
    @SuppressWarnings("unchecked")
    org.springframework.core.retry.Retryable<Object> retryable =
        (org.springframework.core.retry.Retryable<Object>)
            mock(org.springframework.core.retry.Retryable.class);

    assertThatCode(
            () -> {
              coreRetryListener.beforeRetry(retryPolicy, retryable);
              coreRetryListener.onRetryFailure(
                  retryPolicy, retryable, new RuntimeException("failure"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void shouldImplementBothRetryListenerContracts() {
    assertThat(underTest)
        .isInstanceOf(org.springframework.core.retry.RetryListener.class)
        .isInstanceOf(org.springframework.retry.RetryListener.class);
  }
}
