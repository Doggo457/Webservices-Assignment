package uk.ac.stir.cscu9yw.lessonbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class PupilLevelTrackerClientTest {

    private static final String TRACKER_URL = "https://tracker.example/plt";
    private static final String DONNAS_RECORD_URL = TRACKER_URL + "/pupil/744621";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer tracker = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final PupilLevelTrackerClient trackerClient = new PupilLevelTrackerClient(restClientBuilder, TRACKER_URL);

    @AfterEach
    void checkTheTrackerWasCalled() {
        tracker.verify();
    }

    @Test
    void readsPupilRecordFromTheTracker() {
        tracker.expect(requestTo(DONNAS_RECORD_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"_links": {"self": {"href": "https://tracker.example/plt/pupil/744621"}},
                         "pupil": {"age": 19, "instruments": {"trumpet": {"grade": 8}}, "matric": 744621, "name": "Donna Cameron"}}
                        """, MediaType.APPLICATION_JSON));

        Pupil donna = trackerClient.findPupil(744621).orElseThrow();

        assertEquals(new Pupil(744621, "Donna Cameron", 19, Map.of("trumpet", new Pupil.InstrumentGrade(8))), donna);
        assertEquals(Optional.of(8), donna.gradeFor("trumpet"));
        assertEquals(Optional.empty(), donna.gradeFor("piano"));
    }

    @Test
    void treatsMissingGradeAsUngraded() {
        tracker.expect(requestTo(DONNAS_RECORD_URL))
                .andRespond(withSuccess("""
                        {"pupil": {"age": 7, "instruments": {"guitar": {"grade": null}}, "matric": 744621, "name": "Donna Cameron"}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(Pupil.UNGRADED), trackerClient.findPupil(744621).orElseThrow().gradeFor("guitar"));
    }

    @Test
    void findsNoPupilForUnknownMatric() {
        tracker.expect(requestTo(TRACKER_URL + "/pupil/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Not Found\", \"status\": 404}"));

        assertEquals(Optional.empty(), trackerClient.findPupil(1));
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"TOO_MANY_REQUESTS", "INTERNAL_SERVER_ERROR", "BAD_GATEWAY"})
    void reportsTrackerUnavailableWhenItRespondsWithAnError(HttpStatus trackerStatus) {
        tracker.expect(requestTo(DONNAS_RECORD_URL)).andRespond(withStatus(trackerStatus));

        assertTrackerUnavailable();
    }

    @Test
    void reportsTrackerUnavailableWhenItCannotBeReached() {
        tracker.expect(requestTo(DONNAS_RECORD_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertTrackerUnavailable();
    }

    private void assertTrackerUnavailable() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> trackerClient.findPupil(744621));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("The pupil level tracker is unavailable, please try again shortly", exception.getReason());
    }
}
