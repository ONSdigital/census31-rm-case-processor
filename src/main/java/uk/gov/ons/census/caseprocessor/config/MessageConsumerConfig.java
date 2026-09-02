package uk.gov.ons.census.caseprocessor.config;

import static com.google.cloud.spring.pubsub.support.PubSubSubscriptionUtils.toProjectSubscriptionName;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryListener;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.messaging.MessageChannel;
import uk.gov.ons.census.caseprocessor.messaging.ManagedMessageRecoverer;

@Configuration
public class MessageConsumerConfig {
  private final ManagedMessageRecoverer managedMessageRecoverer;
  private final PubSubTemplate pubSubTemplate;

  @Value("${spring.cloud.gcp.pubsub.project-id}")
  private String pubsubProject;

  @Value("${queueconfig.new-case-subscription}")
  private String newCaseSubscription;

  @Value("${queueconfig.fulfilment-request-subscription}")
  private String fulfilmentRequestSubscription;

  @Value("${queueconfig.receipt-subscription}")
  private String receiptSubscription;

  @Value("${queueconfig.refusal-subscription}")
  private String refusalSubscription;

  @Value("${queueconfig.invalid-case-subscription}")
  private String invalidCaseSubscription;

  @Value("${queueconfig.survey-launched-subscription}")
  private String surveyLaunchedSubscription;

  @Value("${queueconfig.deactivate-uac-subscription}")
  private String deactivateUacSubscription;

  @Value("${queueconfig.update-sample-subscription}")
  private String updateSampleSubscription;

  @Value("${queueconfig.update-sample-sensitive-subscription}")
  private String updateSampleSensitiveSubscription;

  @Value("${queueconfig.sms-confirmation-subscription}")
  private String smsConfirmationSubscription;

  @Value("${queueconfig.email-confirmation-subscription}")
  private String emailConfirmationSubscription;

  public MessageConsumerConfig(
      ManagedMessageRecoverer managedMessageRecoverer, PubSubTemplate pubSubTemplate) {
    this.managedMessageRecoverer = managedMessageRecoverer;
    this.pubSubTemplate = pubSubTemplate;
  }

  @Bean
  public MessageChannel newCaseInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel receiptInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel refusalInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel invalidCaseInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel surveyLaunchedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel fulfilmentRequestInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel deactivateUacInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel updateSampleInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel updateSampleSensitiveInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel smsConfirmationInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel emailConfirmationInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public PubSubInboundChannelAdapter newCaseInbound(
      @Qualifier("newCaseInputChannel") MessageChannel channel) {
    String subscription = toProjectSubscriptionName(newCaseSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  public PubSubInboundChannelAdapter receiptInbound(
      @Qualifier("receiptInputChannel") MessageChannel channel) {
    String subscription = toProjectSubscriptionName(receiptSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  public PubSubInboundChannelAdapter refusalInbound(
      @Qualifier("refusalInputChannel") MessageChannel channel) {
    String subscription = toProjectSubscriptionName(refusalSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter invalidCaseInbound(
      @Qualifier("invalidCaseInputChannel") MessageChannel channel) {
    String subscription =
        toProjectSubscriptionName(invalidCaseSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter surveyLaunchedInbound(
      @Qualifier("surveyLaunchedInputChannel") MessageChannel channel) {
    String subscription =
        toProjectSubscriptionName(surveyLaunchedSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter fulfilmentRequestInbound(
      @Qualifier("fulfilmentRequestInputChannel") MessageChannel channel) {
    String subscription =
        toProjectSubscriptionName(fulfilmentRequestSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter deactivateUacInbound(
      @Qualifier("deactivateUacInputChannel") MessageChannel channel) {
    String subscription =
        toProjectSubscriptionName(deactivateUacSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter updateSampleInbound(
      @Qualifier("updateSampleInputChannel") MessageChannel channel) {
    String subscription =
        toProjectSubscriptionName(updateSampleSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter updateSampleSensitiveInbound(
      @Qualifier("updateSampleSensitiveInputChannel") MessageChannel channel) {
    String subscription =
        toProjectSubscriptionName(updateSampleSensitiveSubscription, pubsubProject).toString();
    return makeAdapter(channel, subscription);
  }

  @Bean
  PubSubInboundChannelAdapter smsConfirmationInbound(
      @Qualifier("smsConfirmationInputChannel") MessageChannel channel) {
    return makeAdapter(channel, smsConfirmationSubscription);
  }

  @Bean
  PubSubInboundChannelAdapter emailConfirmationInbound(
      @Qualifier("emailConfirmationInputChannel") MessageChannel channel) {
    return makeAdapter(channel, emailConfirmationSubscription);
  }

  private PubSubInboundChannelAdapter makeAdapter(MessageChannel channel, String subscriptionName) {
    PubSubInboundChannelAdapter adapter =
        new PubSubInboundChannelAdapter(pubSubTemplate, subscriptionName);
    adapter.setOutputChannel(channel);
    adapter.setAckMode(AckMode.AUTO);
    return adapter;
  }

  @Bean
  public RequestHandlerRetryAdvice retryAdvice() {
    RequestHandlerRetryAdvice requestHandlerRetryAdvice = new RequestHandlerRetryAdvice();
    requestHandlerRetryAdvice.setRecoveryCallback(managedMessageRecoverer);
    return requestHandlerRetryAdvice;
  }

  @Bean
  public RetryListener retryListener() {
    RetryListener retryListener = new DefaultListenerSupport();

    return retryListener;
  }
}
