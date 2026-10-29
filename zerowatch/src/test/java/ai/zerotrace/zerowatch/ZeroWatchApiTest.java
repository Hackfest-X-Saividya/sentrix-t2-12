package ai.zerotrace.zerowatch;

import ai.zerotrace.zerowatch.api.PayloadLimitFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ZeroWatchApiTest {
    private static final String BASE = "/api/v1/zerowatch";
    private static final String NOW = "2026-10-28T10:00:00Z";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC); }
    }

    @Test void healthEndpointWorks() throws Exception {
        mvc.perform(get(BASE + "/health")).andExpect(status().isOk()).andExpect(jsonPath("$.component").value("zerowatch"));
    }

    @Test void validEventIsAcceptedAndRetrievable() throws Exception {
        MvcResult result = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(appStarted("read-case")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.event.eventId").isNotEmpty()).andReturn();
        String eventId = mapper.readTree(result.getResponse().getContentAsString()).path("event").path("eventId").asText();
        mvc.perform(get(BASE + "/events/" + eventId)).andExpect(status().isOk()).andExpect(jsonPath("$.eventType").value("APP_STARTED"));
    }

    @Test void missingRequiredFieldsAreRejected() throws Exception {
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test void malformedTimestampAndUnknownTypeAreRejected() throws Exception {
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(appStarted("bad-time").replace(NOW, "not-a-time")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(appStarted("bad-type").replace("APP_STARTED", "UNKNOWN")))
                .andExpect(status().isBadRequest());
    }

    @Test void oversizedPayloadReturns413() throws Exception {
        String body = "{" + " ".repeat(PayloadLimitFilter.MAX_BODY_BYTES + 1) + "}";
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.error").value("payload_too_large"));
    }

    @Test void deniedBurstTriggersAlertWithEvidenceEventIds() throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/access-denied-sequence.json")) {
            fixture = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        JsonNode events = mapper.readTree(fixture);
        JsonNode created = null;
        for (JsonNode event : events) {
            MvcResult result = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(event.toString()))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode alerts = mapper.readTree(result.getResponse().getContentAsString()).path("alerts");
            if (!alerts.isEmpty()) created = alerts.get(0);
        }
        org.junit.jupiter.api.Assertions.assertNotNull(created);
        org.junit.jupiter.api.Assertions.assertEquals("zerowatch.access-denied-burst", created.path("ruleId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(3, created.path("eventIds").size());
        String alertId = created.path("alertId").asText();
        mvc.perform(get(BASE + "/alerts/" + alertId)).andExpect(status().isOk()).andExpect(jsonPath("$.eventIds.length()").value(3));
    }

    @Test void accessDeniedBurstTriggersAtInclusiveFiveMinuteBoundary() throws Exception {
        String actor = "actor-5e0000000001";
        String service = "burst-window-inclusive";
        String[] timestamps = {"2026-10-28T09:50:00Z", "2026-10-28T09:52:30Z", "2026-10-28T09:55:00Z"};
        JsonNode alert = null;
        for (int i = 0; i < timestamps.length; i++) {
            String payload = accessDenied("inclusive-" + i, actor, timestamps[i])
                    .replace("\"serviceName\":\"test-service\"", "\"serviceName\":\"" + service + "\"");
            MvcResult result = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode alerts = mapper.readTree(result.getResponse().getContentAsString()).path("alerts");
            if (i < 2) org.junit.jupiter.api.Assertions.assertTrue(alerts.isEmpty(), "fewer than three events must not alert");
            else {
                org.junit.jupiter.api.Assertions.assertEquals(1, alerts.size());
                alert = alerts.get(0);
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(alert, "the event exactly five minutes after the first is included at the lower boundary");
        org.junit.jupiter.api.Assertions.assertEquals("zerowatch.access-denied-burst", alert.path("ruleId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(3, alert.path("eventIds").size());
    }

    @Test void accessDeniedBurstDoesNotTriggerJustOutsideFiveMinuteWindow() throws Exception {
        String actor = "actor-5e0000000002";
        String service = "burst-window-outside";
        String[] timestamps = {"2026-10-28T09:50:00Z", "2026-10-28T09:52:30Z", "2026-10-28T09:55:00.001Z"};
        for (int i = 0; i < timestamps.length; i++) {
            String payload = accessDenied("outside-" + i, actor, timestamps[i])
                    .replace("\"serviceName\":\"test-service\"", "\"serviceName\":\"" + service + "\"");
            mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.alerts").isEmpty());
        }
    }

    @Test void alertEvidenceIdsMatchSubmittedAndStoredEvents() throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/access-denied-sequence.json")) {
            fixture = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<String> submittedEventIds = new ArrayList<>();
        List<JsonNode> submittedEvents = new ArrayList<>();
        String alertId = null;
        int sequenceIndex = 0;
        for (JsonNode fixtureEvent : mapper.readTree(fixture)) {
            ObjectNode submitted = fixtureEvent.deepCopy();
            submitted.put("serviceName", "evidence-traceability");
            submitted.put("requestId", "traceability-" + sequenceIndex++);
            ((ObjectNode) submitted.path("attributes")).put("actorRef", "actor-fedcba987654");
            submittedEvents.add(submitted.deepCopy());

            MvcResult result = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(submitted.toString()))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
            submittedEventIds.add(response.path("event").path("eventId").asText());
            JsonNode alerts = response.path("alerts");
            if (!alerts.isEmpty()) {
                org.junit.jupiter.api.Assertions.assertEquals(1, alerts.size());
                org.junit.jupiter.api.Assertions.assertNull(alertId, "the sequence should create only one alert");
                alertId = alerts.get(0).path("alertId").asText();
            }
        }

        org.junit.jupiter.api.Assertions.assertNotNull(alertId, "the denied-event sequence should trigger an alert");
        MvcResult alertResult = mvc.perform(get(BASE + "/alerts/" + alertId)).andExpect(status().isOk()).andReturn();
        JsonNode alert = mapper.readTree(alertResult.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals("zerowatch.access-denied-burst", alert.path("ruleId").asText());
        List<String> evidenceEventIds = new ArrayList<>();
        alert.path("eventIds").forEach(id -> evidenceEventIds.add(id.asText()));
        org.junit.jupiter.api.Assertions.assertEquals(submittedEventIds.size(), evidenceEventIds.size(), "evidence must contain each submitted event once");
        org.junit.jupiter.api.Assertions.assertEquals(new HashSet<>(submittedEventIds), new HashSet<>(evidenceEventIds),
                "evidence must contain exactly the event IDs returned for this submitted sequence");

        for (int i = 0; i < submittedEventIds.size(); i++) {
            String eventId = submittedEventIds.get(i);
            MvcResult eventResult = mvc.perform(get(BASE + "/events/" + eventId)).andExpect(status().isOk()).andReturn();
            JsonNode stored = mapper.readTree(eventResult.getResponse().getContentAsString());
            JsonNode submitted = submittedEvents.get(i);
            org.junit.jupiter.api.Assertions.assertEquals(eventId, stored.path("eventId").asText());
            org.junit.jupiter.api.Assertions.assertEquals(submitted.path("schemaVersion"), stored.path("schemaVersion"));
            org.junit.jupiter.api.Assertions.assertEquals(submitted.path("timestamp"), stored.path("timestamp"));
            org.junit.jupiter.api.Assertions.assertEquals(submitted.path("serviceName"), stored.path("serviceName"));
            org.junit.jupiter.api.Assertions.assertEquals(submitted.path("eventType"), stored.path("eventType"));
            org.junit.jupiter.api.Assertions.assertEquals(submitted.path("requestId"), stored.path("requestId"));
            org.junit.jupiter.api.Assertions.assertEquals(submitted.path("attributes"), stored.path("attributes"));
        }
    }

    @Test void normalEventsDoNotTriggerTheBurstRule() throws Exception {
        String json = new String(getClass().getResourceAsStream("/fixtures/normal-activity.json").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        for (JsonNode event : mapper.readTree(json)) {
            mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(event.toString()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.alerts").isEmpty());
        }
    }

    @Test void allowedRawSqlOperationTriggersItsSpecificRule() throws Exception {
        String body = "{\"schemaVersion\":\"1.0\",\"timestamp\":\"" + NOW + "\",\"serviceName\":\"raw-sql-demo\",\"eventType\":\"SENSITIVE_OPERATION\",\"attributes\":{\"operation\":\"RAW_SQL_EXECUTION\",\"outcome\":\"ALLOWED\"}}";
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.alerts[0].ruleId").value("zerowatch.unexpected-raw-sql-operation"))
                .andExpect(jsonPath("$.alerts[0].eventIds.length()").value(1));
    }

    @Test void repeatedEquivalentAlertIsDeduplicated() throws Exception {
        String actor = "actor-acde1234abcd";
        for (int i = 0; i < 4; i++) {
            String event = accessDenied("dedup-" + i, actor, Instant.parse(NOW).plusSeconds(i * 20L).toString());
            var result = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isCreated()).andReturn();
            JsonNode alerts = mapper.readTree(result.getResponse().getContentAsString()).path("alerts");
            org.junit.jupiter.api.Assertions.assertEquals(i == 2 ? 1 : 0, alerts.size());
        }
    }

    @Test void duplicateEventIdIsRejectedWithoutDuplicatingStorageOrAlertEvidence() throws Exception {
        String actor = "actor-badc0ffee123";
        String service = "duplicate-id-check";
        String firstId = "00000000-0000-0000-0000-000000000101";
        String secondId = "00000000-0000-0000-0000-000000000102";
        String thirdId = "00000000-0000-0000-0000-000000000103";
        String firstPayload = accessDeniedWithEventId(firstId, "duplicate-first", service, actor, NOW);

        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(firstPayload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.event.eventId").value(firstId));
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(firstPayload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("duplicate_event"));

        String secondPayload = accessDeniedWithEventId(secondId, "duplicate-second", service, actor,
                Instant.parse(NOW).plusSeconds(30).toString());
        MvcResult secondResult = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(secondPayload))
                .andExpect(status().isCreated()).andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(mapper.readTree(secondResult.getResponse().getContentAsString()).path("alerts").isEmpty(),
                "the rejected duplicate must not count as a second denied event");

        String thirdPayload = accessDeniedWithEventId(thirdId, "duplicate-third", service, actor,
                Instant.parse(NOW).plusSeconds(60).toString());
        MvcResult thirdResult = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(thirdPayload))
                .andExpect(status().isCreated()).andReturn();
        JsonNode thirdResponse = mapper.readTree(thirdResult.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(1, thirdResponse.path("alerts").size());
        String alertId = thirdResponse.path("alerts").get(0).path("alertId").asText();

        MvcResult alertResult = mvc.perform(get(BASE + "/alerts/" + alertId)).andExpect(status().isOk()).andReturn();
        JsonNode alert = mapper.readTree(alertResult.getResponse().getContentAsString());
        List<String> evidenceIds = new ArrayList<>();
        alert.path("eventIds").forEach(id -> evidenceIds.add(id.asText()));
        Set<String> expectedIds = Set.of(firstId, secondId, thirdId);
        org.junit.jupiter.api.Assertions.assertEquals(expectedIds, new HashSet<>(evidenceIds));
        org.junit.jupiter.api.Assertions.assertEquals(expectedIds.size(), evidenceIds.size(), "alert evidence must not repeat an event ID");

        MvcResult eventsResult = mvc.perform(get(BASE + "/events?limit=500")).andExpect(status().isOk()).andReturn();
        Map<String, Integer> storedCounts = new HashMap<>();
        for (JsonNode stored : mapper.readTree(eventsResult.getResponse().getContentAsString())) {
            String storedId = stored.path("eventId").asText();
            if (expectedIds.contains(storedId)) storedCounts.merge(storedId, 1, Integer::sum);
        }
        org.junit.jupiter.api.Assertions.assertEquals(Map.of(firstId, 1, secondId, 1, thirdId, 1), storedCounts,
                "each distinct submission should be stored once and the duplicate should not add another event");
    }

    @Test void concurrentUniqueEventsAreAllRetrievable() throws Exception {
        int eventCount = 24;
        List<String> eventIds = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            String eventId = new UUID(1L, i + 1L).toString();
            eventIds.add(eventId);
            payloads.add(appStartedWithEventId(eventId, "parallel-unique-" + i));
        }

        List<MvcResult> responses = postConcurrently(payloads);
        org.junit.jupiter.api.Assertions.assertEquals(eventCount, responses.size());
        for (int i = 0; i < responses.size(); i++) {
            MvcResult response = responses.get(i);
            org.junit.jupiter.api.Assertions.assertEquals(201, response.getResponse().getStatus());
            JsonNode accepted = mapper.readTree(response.getResponse().getContentAsString()).path("event");
            org.junit.jupiter.api.Assertions.assertEquals(eventIds.get(i), accepted.path("eventId").asText());

            MvcResult retrieved = mvc.perform(get(BASE + "/events/" + eventIds.get(i))).andExpect(status().isOk()).andReturn();
            JsonNode stored = mapper.readTree(retrieved.getResponse().getContentAsString());
            org.junit.jupiter.api.Assertions.assertEquals(eventIds.get(i), stored.path("eventId").asText());
            org.junit.jupiter.api.Assertions.assertEquals("APP_STARTED", stored.path("eventType").asText());
        }
    }

    @Test void concurrentDuplicateEventIdHasOneWinnerAndOneStoredRecord() throws Exception {
        String eventId = new UUID(2L, 1L).toString();
        String payload = appStartedWithEventId(eventId, "parallel-duplicate");
        int submissions = 8;
        List<MvcResult> responses = postConcurrently(java.util.Collections.nCopies(submissions, payload));
        int created = 0;
        int conflicts = 0;
        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 201) {
                created++;
                org.junit.jupiter.api.Assertions.assertEquals(eventId,
                        mapper.readTree(response.getResponse().getContentAsString()).path("event").path("eventId").asText());
            } else {
                org.junit.jupiter.api.Assertions.assertEquals(409, response.getResponse().getStatus());
                org.junit.jupiter.api.Assertions.assertEquals("duplicate_event",
                        mapper.readTree(response.getResponse().getContentAsString()).path("error").asText());
                conflicts++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, created);
        org.junit.jupiter.api.Assertions.assertEquals(submissions - 1, conflicts);

        MvcResult listResult = mvc.perform(get(BASE + "/events?limit=500")).andExpect(status().isOk()).andReturn();
        int storedCopies = 0;
        for (JsonNode event : mapper.readTree(listResult.getResponse().getContentAsString())) {
            if (eventId.equals(event.path("eventId").asText())) storedCopies++;
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, storedCopies);
        mvc.perform(get(BASE + "/events/" + eventId)).andExpect(status().isOk()).andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test void concurrentDeniedBurstProducesOneAlertWithValidUniqueStoredEvidence() throws Exception {
        int eventCount = 12;
        String service = "parallel-burst-check";
        String actor = "actor-c0ffee123456";
        List<String> eventIds = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            String eventId = new UUID(3L, i + 1L).toString();
            eventIds.add(eventId);
            payloads.add(accessDeniedWithEventId(eventId, "parallel-denied-" + i, service, actor, NOW));
        }

        List<MvcResult> responses = postConcurrently(payloads);
        Set<String> submittedIds = new HashSet<>(eventIds);
        List<String> createdAlertIds = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            MvcResult response = responses.get(i);
            org.junit.jupiter.api.Assertions.assertEquals(201, response.getResponse().getStatus());
            JsonNode body = mapper.readTree(response.getResponse().getContentAsString());
            org.junit.jupiter.api.Assertions.assertEquals(eventIds.get(i), body.path("event").path("eventId").asText());
            body.path("alerts").forEach(alert -> createdAlertIds.add(alert.path("alertId").asText()));
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, createdAlertIds.size(), "the concurrent burst should create one alert, not duplicates");

        MvcResult alertResult = mvc.perform(get(BASE + "/alerts/" + createdAlertIds.get(0))).andExpect(status().isOk()).andReturn();
        JsonNode alert = mapper.readTree(alertResult.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals("zerowatch.access-denied-burst", alert.path("ruleId").asText());
        List<String> evidenceIds = new ArrayList<>();
        alert.path("eventIds").forEach(id -> evidenceIds.add(id.asText()));
        org.junit.jupiter.api.Assertions.assertTrue(evidenceIds.size() >= 3);
        org.junit.jupiter.api.Assertions.assertEquals(evidenceIds.size(), new HashSet<>(evidenceIds).size(), "evidence IDs must be unique");
        org.junit.jupiter.api.Assertions.assertTrue(submittedIds.containsAll(evidenceIds), "evidence must refer only to this concurrent submission batch");
        for (String evidenceId : evidenceIds) {
            MvcResult eventResult = mvc.perform(get(BASE + "/events/" + evidenceId)).andExpect(status().isOk()).andReturn();
            JsonNode stored = mapper.readTree(eventResult.getResponse().getContentAsString());
            org.junit.jupiter.api.Assertions.assertEquals(evidenceId, stored.path("eventId").asText());
            org.junit.jupiter.api.Assertions.assertEquals("ACCESS_DENIED", stored.path("eventType").asText());
            org.junit.jupiter.api.Assertions.assertEquals(service, stored.path("serviceName").asText());
            org.junit.jupiter.api.Assertions.assertEquals(actor, stored.path("attributes").path("actorRef").asText());
        }

        MvcResult alertListResult = mvc.perform(get(BASE + "/alerts?limit=500")).andExpect(status().isOk()).andReturn();
        int alertsForBatch = 0;
        for (JsonNode listedAlert : mapper.readTree(alertListResult.getResponse().getContentAsString())) {
            boolean referencesBatchEvent = false;
            for (JsonNode id : listedAlert.path("eventIds")) {
                if (submittedIds.contains(id.asText())) referencesBatchEvent = true;
            }
            if (referencesBatchEvent) alertsForBatch++;
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, alertsForBatch, "only one stored alert should reference this burst");
    }

    @Test void findingReferenceIsPreservedButNotCorrelatedWithoutMetadata() throws Exception {
        String body = appStarted("reference-case").replace("\"attributes\":{}", "\"findingId\":\"ZT-SQLI-123\",\"attributes\":{}");
        MvcResult result = mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        JsonNode event = mapper.readTree(result.getResponse().getContentAsString()).path("event");
        org.junit.jupiter.api.Assertions.assertEquals("ZT-SQLI-123", event.path("findingId").asText());
    }

    @Test void sensitiveAttributesAndSeverityAreRejected() throws Exception {
        String sensitive = accessDenied("sensitive", "actor-aaaa1111bbbb", NOW)
                .replace("\"resource\":\"admin-console\"", "\"resource\":\"admin-console\",\"password\":\"secret\"");
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(sensitive)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(appStarted("severity").replace("\"eventType\"", "\"severity\":\"CRITICAL\",\"eventType\"")))
                .andExpect(status().isBadRequest());
    }

    @Test void badLimitsAndUnknownIdsHaveStructuredResponses() throws Exception {
        mvc.perform(get(BASE + "/events?limit=501")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_event"));
        mvc.perform(get(BASE + "/events/unknown-id")).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/alerts/unknown-id")).andExpect(status().isNotFound());
    }

    private String appStarted(String requestId) {
        return "{\"schemaVersion\":\"1.0\",\"timestamp\":\"" + NOW + "\",\"serviceName\":\"test-service\",\"eventType\":\"APP_STARTED\",\"requestId\":\"" + requestId + "\",\"attributes\":{}}";
    }
    private String appStartedWithEventId(String eventId, String requestId) {
        return appStarted(requestId).replace("{\"schemaVersion\":", "{\"eventId\":\"" + eventId + "\",\"schemaVersion\":");
    }
    private String accessDenied(String requestId, String actor, String timestamp) {
        return "{\"schemaVersion\":\"1.0\",\"timestamp\":\"" + timestamp + "\",\"serviceName\":\"test-service\",\"eventType\":\"ACCESS_DENIED\",\"requestId\":\"" + requestId + "\",\"attributes\":{\"actorRef\":\"" + actor + "\",\"resource\":\"admin-console\"}}";
    }
    private String accessDeniedWithEventId(String eventId, String requestId, String serviceName, String actor, String timestamp) {
        return accessDenied(requestId, actor, timestamp)
                .replace("{\"schemaVersion\":", "{\"eventId\":\"" + eventId + "\",\"schemaVersion\":")
                .replace("\"serviceName\":\"test-service\"", "\"serviceName\":\"" + serviceName + "\"");
    }

    private List<MvcResult> postConcurrently(List<String> payloads) throws Exception {
        int workerCount = Math.min(8, payloads.size());
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (String payload : payloads) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent request start.");
                    return mvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
                }));
            }
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS), "worker threads should reach the start gate");
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            org.junit.jupiter.api.Assertions.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "concurrency executor must terminate");
        }
    }
}
