package uk.ac.stir.cscu9yw.lessonbooking;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tutor")
public class TutorController {

    private final LessonBookingService bookingService;

    public TutorController(LessonBookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/lessons")
    public CollectionModel<Lesson> listLessons(@RequestParam(required = false) DayOfWeek day) {
        return CollectionModel.of(bookingService.findLessonsOn(day),
                linkTo(methodOn(TutorController.class).listLessons(day)).withSelfRel().expand());
    }

    @GetMapping("/blocked-slots")
    public CollectionModel<Slot> listBlockedSlots(@RequestParam(required = false) DayOfWeek day) {
        return CollectionModel.of(bookingService.findBlockedSlots(day),
                linkTo(methodOn(TutorController.class).listBlockedSlots(day)).withSelfRel().expand());
    }

    @PutMapping("/blocked-slots/{day}")
    public ResponseEntity<Void> blockDay(@PathVariable DayOfWeek day) {
        bookingService.blockSlots(day, null);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/blocked-slots/{day}/{time}")
    public ResponseEntity<Void> blockSlot(@PathVariable DayOfWeek day, @PathVariable LocalTime time) {
        bookingService.blockSlots(day, time);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/blocked-slots/{day}")
    public ResponseEntity<Void> unblockDay(@PathVariable DayOfWeek day) {
        bookingService.unblockSlots(day, null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/blocked-slots/{day}/{time}")
    public ResponseEntity<Void> unblockSlot(@PathVariable DayOfWeek day, @PathVariable LocalTime time) {
        bookingService.unblockSlots(day, time);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lesson-checks")
    public CollectionModel<LessonBookingService.CancelledLesson> checkLessonsAgainstTracker() {
        return CollectionModel.of(bookingService.cancelLessonsBreakingRules(),
                linkTo(methodOn(TutorController.class).listLessons(null)).withRel("tutor-lessons"));
    }
}
