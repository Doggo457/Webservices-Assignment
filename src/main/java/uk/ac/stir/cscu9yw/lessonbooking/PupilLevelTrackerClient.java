package uk.ac.stir.cscu9yw.lessonbooking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PupilLevelTrackerClient {

    private final RestClient restClient;

    public PupilLevelTrackerClient(RestClient.Builder restClientBuilder, @Value("${pupil-level-tracker.url}") String trackerUrl) {
        this.restClient = restClientBuilder.baseUrl(trackerUrl).build();
    }

    public Optional<Pupil> findPupil(int matric) {
        try {
            return restClient.get()
                    .uri("/pupil/{matric}", matric)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                            return Optional.<Pupil>empty();
                        }
                        if (response.getStatusCode().isError()) {
                            throw trackerUnavailable();
                        }
                        PupilRecord pupilRecord = response.bodyTo(PupilRecord.class);
                        return Optional.ofNullable(pupilRecord).map(PupilRecord::pupil);
                    });
        } catch (RestClientException exception) {
            throw trackerUnavailable();
        }
    }

    private ResponseStatusException trackerUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The pupil level tracker is unavailable, please try again shortly");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PupilRecord(Pupil pupil) {
    }
}
