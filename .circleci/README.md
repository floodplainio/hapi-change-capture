## HAPI CDC


This is a rough version of providing change data capture to a HAPI-FHIR system.

It will publish a create, update or delete message to a Kafka topic whenever one of those operations is performed on a HAPI resource.

It relies on spring-kafka to publish actual messages to Kafka, so if that is configured correctly it should be able to publish.

Additionally, there is an additional '/snapshot' endpoint that will publish all existing resources to the corresponding topics.

So the general workflow for enabling CDC on a HAPI FHIR should be something like:
- Add the cdc component to you HAPI instance
- Run the HAPI server, preferrably without any traffic reaching it.
- Call the /snapshot endpoint
- enable traffic.

Now the Kafka topics should show a near-realtime view of the HAPI cluster. Allowing for Kafka Streams or Flink - like systems to create materialized views on the HAPI data.

### Known issues:
There is a long standing bug:
https://github.com/hapifhir/hapi-fhir/issues/3204
which causes the CDC to miss delete messages. It's easy to patch, it would be nice if it was upstreamed ¯\_(ツ)_/¯.
