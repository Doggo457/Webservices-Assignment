package uk.ac.stir.cscu9yw.lessonbooking;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.hateoas.server.core.Relation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LessonBookingService {

    private static final int MINIMUM_AGE_FOR_EVENING_LESSONS = 16;
    private static final LocalTime EVENING_START = LocalTime.of(18, 0);
    private static final Comparator<Lesson> BY_SLOT = Comparator.comparing(Lesson::slot);

    private final Tutor tutor;
    private final PupilLevelTrackerClient pupilLevelTracker;
    private final Map<Long, Lesson> lessonsById = new LinkedHashMap<>();
    private final Set<Slot> blockedSlots = new HashSet<>();
    private long nextLessonId = 1;

    public LessonBookingService(Tutor tutor, PupilLevelTrackerClient pupilLevelTracker) {
        this.tutor = tutor;
        this.pupilLevelTracker = pupilLevelTracker;
    }

    public synchronized List<Slot> findAvailableSlots(DayOfWeek day) {
        return Slot.lessonWeek().stream()
                .filter(slot -> isOnDay(slot, day))
                .filter(slot -> !blockedSlots.contains(slot) && !isBooked(slot))
                .toList();
    }

    public synchronized List<Slot> findBlockedSlots(DayOfWeek day) {
        return blockedSlots.stream()
                .filter(slot -> isOnDay(slot, day))
                .sorted()
                .toList();
    }

    public synchronized List<Lesson> findLessonsOn(DayOfWeek day) {
        return lessonsById.values().stream()
                .filter(lesson -> isOnDay(lesson.slot(), day))
                .sorted(BY_SLOT)
                .toList();
    }

    public synchronized List<Lesson> findLessonsForPupil(int matric) {
        return lessonsById.values().stream()
                .filter(lesson -> lesson.matric() == matric)
                .sorted(BY_SLOT)
                .toList();
    }

    public synchronized Lesson findLesson(long lessonId) {
        Lesson lesson = lessonsById.get(lessonId);
        if (lesson == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no lesson with id " + lessonId);
        }
        return lesson;
    }

    public Lesson bookLesson(int matric, String pupilName, String instrument, Slot slot) {
        if (!Slot.lessonWeek().contains(slot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lessons start on the hour or half hour, Monday to Friday 16:00 to 19:30 and Saturday 09:00 to 19:30");
        }
        Pupil pupil = pupilLevelTracker.findPupil(matric)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "There is no pupil with matriculation number " + matric));
        if (!pupil.hasName(pupilName)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The name given does not match matriculation number " + matric);
        }
        String chosenInstrument = instrument.trim().toLowerCase(Locale.ROOT);
        Optional<String> brokenRule = findBrokenRule(pupil, chosenInstrument, slot);
        if (brokenRule.isPresent()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, brokenRule.get());
        }
        return reserveSlot(pupil, chosenInstrument, slot);
    }

    public synchronized void cancelLesson(long lessonId, int matric) {
        Lesson lesson = findLesson(lessonId);
        if (lesson.matric() != matric) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the pupil who booked a lesson can cancel it");
        }
        lessonsById.remove(lessonId);
    }

    public synchronized void blockSlots(DayOfWeek day, LocalTime time) {
        blockedSlots.addAll(findLessonSlots(day, time));
    }

    public synchronized void unblockSlots(DayOfWeek day, LocalTime time) {
        blockedSlots.removeAll(findLessonSlots(day, time));
    }

    public List<CancelledLesson> cancelLessonsBreakingRules() {
        List<CancelledLesson> cancelledLessons = new ArrayList<>();
        for (Lesson lesson : findLessonsOn(null)) {
            Optional<Pupil> pupil = pupilLevelTracker.findPupil(lesson.matric());
            Optional<String> brokenRule = pupil.isPresent()
                    ? findBrokenRule(pupil.get(), lesson.instrument(), lesson.slot())
                    : Optional.of("Pupil " + lesson.matric() + " has left the school");
            if (brokenRule.isPresent()) {
                removeLesson(lesson.id());
                cancelledLessons.add(new CancelledLesson(lesson, brokenRule.get()));
            }
        }
        return cancelledLessons;
    }

    private Optional<String> findBrokenRule(Pupil pupil, String instrument, Slot slot) {
        Optional<Tutor.GradeRange> gradeRange = tutor.gradeRangeFor(instrument);
        Optional<Integer> grade = pupil.gradeFor(instrument);
        if (gradeRange.isEmpty()) {
            return Optional.of(tutor.name() + " does not teach " + instrument);
        }
        if (grade.isEmpty()) {
            return Optional.of(pupil.name() + " does not play " + instrument);
        }
        if (!gradeRange.get().includes(grade.get())) {
            return Optional.of(tutor.name() + " teaches " + instrument + " from grade " + gradeRange.get().lowestGrade()
                    + " to grade " + gradeRange.get().highestGrade() + " but " + pupil.name() + " is grade " + grade.get());
        }
        if (pupil.age() < MINIMUM_AGE_FOR_EVENING_LESSONS && !slot.time().isBefore(EVENING_START)) {
            return Optional.of("Pupils under " + MINIMUM_AGE_FOR_EVENING_LESSONS + " cannot have lessons from " + EVENING_START);
        }
        return Optional.empty();
    }

    private synchronized Lesson reserveSlot(Pupil pupil, String instrument, Slot slot) {
        if (blockedSlots.contains(slot) || isBooked(slot)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The slot on " + slot + " is not available");
        }
        boolean hasLessonThatDay = lessonsById.values().stream()
                .anyMatch(lesson -> lesson.matric() == pupil.matric() && lesson.day() == slot.day());
        if (hasLessonThatDay) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    pupil.name() + " already has a lesson on " + slot.day() + " and pupils can book one lesson a day");
        }
        Lesson lesson = new Lesson(nextLessonId++, pupil.matric(), pupil.name(), instrument, slot.day(), slot.time());
        lessonsById.put(lesson.id(), lesson);
        return lesson;
    }

    private synchronized void removeLesson(long lessonId) {
        lessonsById.remove(lessonId);
    }

    private List<Slot> findLessonSlots(DayOfWeek day, LocalTime time) {
        List<Slot> slots = Slot.lessonWeek().stream()
                .filter(slot -> slot.day() == day && (time == null || slot.time().equals(time)))
                .toList();
        if (slots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "There is no lesson slot on " + day + (time == null ? "" : " at " + time));
        }
        return slots;
    }

    private boolean isBooked(Slot slot) {
        return lessonsById.values().stream().anyMatch(lesson -> lesson.slot().equals(slot));
    }

    private boolean isOnDay(Slot slot, DayOfWeek day) {
        return day == null || slot.day() == day;
    }

    @Relation(collectionRelation = "cancelledLessons")
    public record CancelledLesson(Lesson lesson, String reason) {
    }
}
/*
⠀⠀⠀⣀⣀⣀⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣀⣀⡤⣄⡀⠀
⠶⡿⠟⠛⠛⠛⠛⠛⠿⠷⠶⡶⠦⠀⠀⠀⠻⢶⡶⠿⠿⠟⠛⠛⠛⠛⠷⢿⠲
⠀⠀⠀⢀⡠⢮⣭⣭⣼⣏⡓⢦⠀⠀⠀⠀⢀⡴⢛⣻⣿⣽⣿⡷⠤⣀⠀⠀⠈
⠀⠀⠐⠙⠤⠼⠿⠿⠇⠙⡄⠸⠀⠀⠀⠀⠈⠇⠠⠧⠼⠿⠿⠧⠴⠚⠁⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⠏⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⡴⠀⢠⠏⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⡀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣀⣠⠴⠋⠀⠀⡞⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠿⠉⠉⠛⠛⠛⠋⠉⠉⠉⠉⠀⠀⠀⠀⠀⣰⡁⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠀⠀⠀⠀⠀
*/