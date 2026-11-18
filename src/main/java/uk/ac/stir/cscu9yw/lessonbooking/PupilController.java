package uk.ac.stir.cscu9yw.lessonbooking;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PupilController {

    private final Tutor tutor;
    private final LessonBookingService bookingService;

    public PupilController(Tutor tutor, LessonBookingService bookingService) {
        this.tutor = tutor;
        this.bookingService = bookingService;
    }

    @GetMapping
    public RepresentationModel<?> getApiRoot() {
        List<Link> links = List.of(
                linkTo(methodOn(PupilController.class).getApiRoot()).withSelfRel(),
                linkTo(methodOn(PupilController.class).getTutor()).withRel("tutor"),
                linkTo(methodOn(PupilController.class).listAvailableSlots(null)).withRel("slots"),
                linkTo(methodOn(PupilController.class).listLessons(null)).withRel("lessons"),
                linkTo(methodOn(TutorController.class).listLessons(null)).withRel("tutor-lessons"),
                linkTo(methodOn(TutorController.class).listBlockedSlots(null)).withRel("blocked-slots"),
                linkTo(methodOn(TutorController.class).checkLessonsAgainstTracker()).withRel("lesson-checks"));
        return new RepresentationModel<>(links);
    }

    @GetMapping("/tutor")
    public EntityModel<Tutor> getTutor() {
        return EntityModel.of(tutor,
                linkTo(methodOn(PupilController.class).getTutor()).withSelfRel(),
                linkTo(methodOn(PupilController.class).listAvailableSlots(null)).withRel("slots"));
    }

    @GetMapping("/slots")
    public CollectionModel<Slot> listAvailableSlots(@RequestParam(required = false) DayOfWeek day) {
        return CollectionModel.of(bookingService.findAvailableSlots(day),
                linkTo(methodOn(PupilController.class).listAvailableSlots(day)).withSelfRel().expand());
    }

    @PostMapping("/lessons")
    public ResponseEntity<EntityModel<Lesson>> bookLesson(@RequestBody BookingRequest request) {
        if (!request.isComplete()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A booking needs matric, name, instrument, day and time");
        }
        Slot slot = new Slot(request.day(), request.time());
        Lesson lesson = bookingService.bookLesson(request.matric(), request.name(), request.instrument(), slot);
        EntityModel<Lesson> lessonModel = toLessonModel(lesson);
        return ResponseEntity.created(lessonModel.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(lessonModel);
    }

    @GetMapping("/lessons")
    public CollectionModel<EntityModel<Lesson>> listLessons(@RequestParam Integer matric) {
        List<EntityModel<Lesson>> lessons = bookingService.findLessonsForPupil(matric).stream()
                .map(this::toLessonModel)
                .toList();
        return CollectionModel.of(lessons, linkTo(methodOn(PupilController.class).listLessons(matric)).withSelfRel());
    }

    @GetMapping("/lessons/{lessonId}")
    public EntityModel<Lesson> getLesson(@PathVariable Long lessonId) {
        return toLessonModel(bookingService.findLesson(lessonId));
    }

    @DeleteMapping("/lessons/{lessonId}")
    public ResponseEntity<Void> cancelLesson(@PathVariable Long lessonId, @RequestParam Integer matric) {
        bookingService.cancelLesson(lessonId, matric);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<Lesson> toLessonModel(Lesson lesson) {
        return EntityModel.of(lesson,
                linkTo(methodOn(PupilController.class).getLesson(lesson.id())).withSelfRel(),
                linkTo(methodOn(PupilController.class).cancelLesson(lesson.id(), lesson.matric())).withRel("cancel"),
                linkTo(methodOn(PupilController.class).listLessons(lesson.matric())).withRel("lessons"));
    }

    public record BookingRequest(Integer matric, String name, String instrument, DayOfWeek day, LocalTime time) {

        boolean isComplete() {
            return matric != null && name != null && !name.isBlank()
                    && instrument != null && !instrument.isBlank() && day != null && time != null;
        }
    }
}
