package uk.ac.stir.cscu9yw.lessonbooking;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class LessonBookingApiTest {

    private static final Pupil BRIAN = new Pupil(875924, "Brian Allan", 9, Map.of("guitar", new Pupil.InstrumentGrade(4)));
    private static final Pupil SAMUEL = new Pupil(537697, "Samuel Paterson", 16, Map.of("guitar", new Pupil.InstrumentGrade(8)));
    private static final RequestPostProcessor TUTOR_LOGIN = httpBasic("anna", "scales2026");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LessonBookingService bookingService;

    @MockitoBean
    private PupilLevelTrackerClient pupilLevelTracker;

    @BeforeEach
    void putPupilsInTheTracker() {
        when(pupilLevelTracker.findPupil(BRIAN.matric())).thenReturn(Optional.of(BRIAN));
        when(pupilLevelTracker.findPupil(SAMUEL.matric())).thenReturn(Optional.of(SAMUEL));
    }

    @AfterEach
    void clearLessonsAndBlockedSlots() {
        bookingService.findLessonsOn(null).forEach(lesson -> bookingService.cancelLesson(lesson.id(), lesson.matric()));
        bookingService.findBlockedSlots(null).forEach(slot -> bookingService.unblockSlots(slot.day(), slot.time()));
    }

    @Test
    void apiRootLinksToEveryResource() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api"))
                .andExpect(jsonPath("$._links.tutor.href").value("http://localhost/api/tutor"))
                .andExpect(jsonPath("$._links.slots.href").value("http://localhost/api/slots{?day}"))
                .andExpect(jsonPath("$._links.lessons.href").value("http://localhost/api/lessons?matric={matric}"))
                .andExpect(jsonPath("$._links['tutor-lessons'].href").value("http://localhost/api/tutor/lessons{?day}"))
                .andExpect(jsonPath("$._links['blocked-slots'].href").value("http://localhost/api/tutor/blocked-slots{?day}"))
                .andExpect(jsonPath("$._links['lesson-checks'].href").value("http://localhost/api/tutor/lesson-checks"));
    }

    @Test
    void tutorDetailsArePublic() throws Exception {
        mockMvc.perform(get("/api/tutor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anna Clarke"))
                .andExpect(jsonPath("$.instruments.piano.lowestGrade").value(3))
                .andExpect(jsonPath("$.instruments.piano.highestGrade").value(7))
                .andExpect(jsonPath("$._links.slots.href").exists());
    }

    @Test
    void listsFreeSlotsForADay() throws Exception {
        mockMvc.perform(get("/api/slots").param("day", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.slots", hasSize(8)))
                .andExpect(jsonPath("$._embedded.slots[0].day").value("MONDAY"))
                .andExpect(jsonPath("$._embedded.slots[0].time").value("16:00"))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/slots?day=MONDAY"));
    }

    @Test
    void unknownDayIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/slots").param("day", "FUNDAY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingCreatesLessonWithLinks() throws Exception {
        String location = book(BRIAN, "MONDAY", "16:00")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("http://localhost/api/lessons/\\d+")))
                .andExpect(jsonPath("$.pupilName").value("Brian Allan"))
                .andExpect(jsonPath("$.instrument").value("guitar"))
                .andExpect(jsonPath("$.day").value("MONDAY"))
                .andExpect(jsonPath("$.time").value("16:00"))
                .andExpect(jsonPath("$._links.lessons.href").value("http://localhost/api/lessons?matric=875924"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value(location))
                .andExpect(jsonPath("$._links.cancel.href").value(location + "?matric=875924"));
    }

    @Test
    void incompleteBookingIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/lessons").contentType(MediaType.APPLICATION_JSON).content("{\"matric\": 875924}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A booking needs matric, name, instrument, day and time"));
    }

    @Test
    void bookingThatBreaksARuleIsUnprocessable() throws Exception {
        book(BRIAN, "MONDAY", "18:00")
                .andExpect(status().is(422))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Pupils under 16 cannot have lessons from 18:00"));
    }

    @Test
    void bookingATakenSlotIsConflict() throws Exception {
        book(SAMUEL, "MONDAY", "16:00").andExpect(status().isCreated());

        book(BRIAN, "MONDAY", "16:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The slot on MONDAY at 16:00 is not available"));
    }

    @Test
    void pupilCanCancelTheirOwnLessonButNotAnotherPupils() throws Exception {
        String location = book(BRIAN, "MONDAY", "16:00").andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        String lessons = mockMvc.perform(get("/api/lessons").param("matric", "875924"))
                .andExpect(jsonPath("$._embedded.lessons", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        String cancelLink = JsonPath.read(lessons, "$._embedded.lessons[0]._links.cancel.href");

        mockMvc.perform(delete(location).param("matric", "537697"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the pupil who booked a lesson can cancel it"));
        mockMvc.perform(delete(cancelLink))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(location))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(matchesPattern("There is no lesson with id \\d+")));
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/tutor/lessons",
            "GET, /api/tutor/blocked-slots",
            "PUT, /api/tutor/blocked-slots/MONDAY",
            "DELETE, /api/tutor/blocked-slots/MONDAY/16:00",
            "POST, /api/tutor/lesson-checks"})
    void tutorResourcesNeedTheTutorLogin(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
        mockMvc.perform(request(method, uri).with(httpBasic("anna", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(method, uri).with(TUTOR_LOGIN))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void browserScriptsGetNoLoginPopup() throws Exception {
        mockMvc.perform(get("/api/tutor/lessons").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void tutorListsLessonsForADay() throws Exception {
        book(BRIAN, "MONDAY", "16:00").andExpect(status().isCreated());
        book(SAMUEL, "TUESDAY", "18:00").andExpect(status().isCreated());

        mockMvc.perform(get("/api/tutor/lessons").param("day", "MONDAY").with(TUTOR_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.lessons[*].pupilName").value(contains("Brian Allan")))
                .andExpect(jsonPath("$._embedded.lessons[0].matric").value(875924));
    }

    @Test
    void tutorCanBlockAndUnblockASlot() throws Exception {
        mockMvc.perform(put("/api/tutor/blocked-slots/MONDAY/16:00").with(TUTOR_LOGIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tutor/blocked-slots").param("day", "MONDAY").with(TUTOR_LOGIN))
                .andExpect(jsonPath("$._embedded.slots[*].time").value(contains("16:00")));
        mockMvc.perform(get("/api/slots").param("day", "MONDAY"))
                .andExpect(jsonPath("$._embedded.slots", hasSize(7)));
        book(BRIAN, "MONDAY", "16:00")
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/tutor/blocked-slots/MONDAY/16:00").with(TUTOR_LOGIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/slots").param("day", "MONDAY"))
                .andExpect(jsonPath("$._embedded.slots", hasSize(8)));
    }

    @Test
    void blockingATimeThatIsNotALessonSlotIsNotFound() throws Exception {
        mockMvc.perform(put("/api/tutor/blocked-slots/MONDAY/16:45").with(TUTOR_LOGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("There is no lesson slot on MONDAY at 16:45"));
    }

    @Test
    void lessonCheckReportsCancelledLessons() throws Exception {
        book(BRIAN, "MONDAY", "16:00").andExpect(status().isCreated());
        when(pupilLevelTracker.findPupil(BRIAN.matric())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/tutor/lesson-checks").with(TUTOR_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.cancelledLessons[0].lesson.pupilName").value("Brian Allan"))
                .andExpect(jsonPath("$._embedded.cancelledLessons[0].reason").value("Pupil 875924 has left the school"))
                .andExpect(jsonPath("$._links['tutor-lessons'].href").exists());
        mockMvc.perform(get("/api/tutor/lessons").with(TUTOR_LOGIN))
                .andExpect(jsonPath("$._embedded").doesNotExist());
    }

    private ResultActions book(Pupil pupil, String day, String time) throws Exception {
        return mockMvc.perform(post("/api/lessons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"matric": %d, "name": "%s", "instrument": "guitar", "day": "%s", "time": "%s"}
                        """.formatted(pupil.matric(), pupil.name(), day, time)));
    }
}
