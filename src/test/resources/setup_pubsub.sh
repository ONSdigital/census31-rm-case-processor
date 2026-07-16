#!/bin/sh

# Wait for pubsub-emulator to come up
bash -c 'while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' '$PUBSUB_SETUP_HOST')" != "200" ]]; do sleep 1; done'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-request
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-request-enriched_notify-service -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-request"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-confirmation
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-confirmation_case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-confirmation"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-email-request
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-email-request-enriched_notify-service -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-email-request"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-email-confirmation
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-email-confirmation_case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-email-confirmation"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_new-case
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_new-case_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_new-case"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_receipt
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_receipt_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_receipt"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_refusal
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_refusal_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_refusal"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_invalid-case
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_invalid-case_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_invalid-case"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_survey-launched
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_survey-launched_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_survey-launched"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_print-fulfilment
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_print-fulfilment_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_print-fulfilment"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_deactivate-uac
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_deactivate-uac_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_deactivate-uac"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_update-sample
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_update-sample_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_update-sample"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_update-sample-sensitive
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_update-sample-sensitive_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_update-sample-sensitive"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_case-update
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_case-update_rh -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_case-update"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/event_uac-update
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/event_uac-update_rh -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/event_uac-update"}'

