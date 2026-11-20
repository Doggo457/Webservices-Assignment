package uk.ac.stir.cscu9yw.lessonbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LessonBookingServiceTest {

    private static final Slot MONDAY_AT_FOUR = slot(DayOfWeek.MONDAY, "16:00");
    private static final Slot MONDAY_AT_HALF_FOUR = slot(DayOfWeek.MONDAY, "16:30");
    private static final Slot TUESDAY_AT_FOUR = slot(DayOfWeek.TUESDAY, "16:00");

    private final PupilLevelTrackerClient pupilLevelTracker = mock(PupilLevelTrackerClient.class);
    private final LessonBookingService bookingService =
            new LessonBookingService(new LessonBookingApplication().tutor(), pupilLevelTracker);

    @Test
    void booksLessonForPupilWhoMeetsEveryRule() {
        Pupil brian = givenPupil(875924, "Brian Allan", 9, "guitar", 4);

        Lesson lesson = book(brian, "guitar", MONDAY_AT_FOUR);

        assertEquals(new Lesson(1, 875924, "Brian Allan", "guitar", DayOfWeek.MONDAY, LocalTime.of(16, 0)), lesson);
        assertEquals(List.of(lesson), bookingService.findLessonsForPupil(875924));
        assertFalse(bookingService.findAvailableSlots(DayOfWeek.MONDAY).contains(MONDAY_AT_FOUR));
    }

    @Test
    void acceptsNameAndInstrumentInAnyCase() {
        givenPupil(875924, "Brian Allan", 9, "guitar", 4);

        Lesson lesson = bookingService.bookLesson(875924, " brian ALLAN ", "Guitar ", MONDAY_AT_FOUR);

        assertEquals("Brian Allan", lesson.pupilName());
        assertEquals("guitar", lesson.instrument());
    }

    @ParameterizedTest
    @CsvSource({"MONDAY, 15:30", "MONDAY, 16:15", "FRIDAY, 20:00", "SATURDAY, 08:30", "SUNDAY, 10:00"})
    void refusesTimesOutsideTheLessonWeek(DayOfWeek day, LocalTime time) {
        assertRefused(HttpStatus.BAD_REQUEST,
                "Lessons start on the hour or half hour, Monday to Friday 16:00 to 19:30 and Saturday 09:00 to 19:30",
                () -> bookingService.bookLesson(875924, "Brian Allan", "guitar", new Slot(day, time)));
        verifyNoInteractions(pupilLevelTracker);
    }

    @Test
    void refusesPupilTheTrackerDoesNotKnow() {
        when(pupilLevelTracker.findPupil(123456)).thenReturn(Optional.empty());

        assertRefused(HttpStatus.UNPROCESSABLE_CONTENT, "There is no pupil with matriculation number 123456",
                () -> bookingService.bookLesson(123456, "Nobody", "guitar", MONDAY_AT_FOUR));
    }

    @Test
    void refusesNameThatDoesNotMatchTheTracker() {
        givenPupil(875924, "Brian Allan", 9, "guitar", 4);

        assertRefused(HttpStatus.UNPROCESSABLE_CONTENT, "The name given does not match matriculation number 875924",
                () -> bookingService.bookLesson(875924, "Bob Smith", "guitar", MONDAY_AT_FOUR));
    }

    @Test
    void refusesInstrumentTheTutorDoesNotTeach() {
        Pupil elizabeth = givenPupil(730292, "Elizabeth Allan", 11, "bass", 6);

        assertRefused(HttpStatus.UNPROCESSABLE_CONTENT, "Anna Clarke does not teach bass",
                () -> book(elizabeth, "bass", MONDAY_AT_FOUR));
    }

    @Test
    void refusesInstrumentThePupilDoesNotPlay() {
        Pupil lisa = givenPupil(258847, "Lisa Wilson", 13, "trumpet", 3);

        assertRefused(HttpStatus.UNPROCESSABLE_CONTENT, "Lisa Wilson does not play guitar",
                () -> book(lisa, "guitar", MONDAY_AT_FOUR));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            piano,   2, Anna Clarke teaches piano from grade 3 to grade 7 but Sam Reid is grade 2
            piano,   8, Anna Clarke teaches piano from grade 3 to grade 7 but Sam Reid is grade 8
            trumpet, 6, Anna Clarke teaches trumpet from grade 1 to grade 5 but Sam Reid is grade 6
            trumpet,  , Anna Clarke teaches trumpet from grade 1 to grade 5 but Sam Reid is grade 0
            """)
    void refusesGradesOutsideTheTutorsRange(String instrument, Integer grade, String reason) {
        Pupil sam = givenPupil(100001, "Sam Reid", 12, instrument, grade);

        assertRefused(HttpStatus.UNPROCESSABLE_CONTENT, reason, () -> book(sam, instrument, MONDAY_AT_FOUR));
    }

    @ParameterizedTest
    @CsvSource({"piano, 3", "piano, 7", "trumpet, 1", "trumpet, 5", "guitar, 8", "guitar,"})
    void acceptsGradesAtTheEdgesOfTheTutorsRange(String instrument, Integer grade) {
        Pupil sam = givenPupil(100001, "Sam Reid", 12, instrument, grade);

        assertEquals(instrument, book(sam, instrument, MONDAY_AT_FOUR).instrument());
    }

    @ParameterizedTest
    @ValueSource(strings = {"18:00", "19:30"})
    void refusesEveningLessonsForPupilsUnder16(LocalTime time) {
        Pupil corey = givenPupil(263228, "Corey Watson", 15, "guitar", 4);

        assertRefused(HttpStatus.UNPROCESSABLE_CONTENT, "Pupils under 16 cannot have lessons from 18:00",
                () -> book(corey, "guitar", new Slot(DayOfWeek.WEDNESDAY, time)));
    }

    @Test
    void allowsPupilsUnder16ToBookJustBeforeTheEvening() {
        Pupil corey = givenPupil(263228, "Corey Watson", 15, "guitar", 4);

        assertEquals(LocalTime.of(17, 30), book(corey, "guitar", slot(DayOfWeek.WEDNESDAY, "17:30")).time());
    }

    @Test
    void allowsPupilsAged16ToBookEveningLessons() {
        Pupil samuel = givenPupil(537697, "Samuel Paterson", 16, "guitar", 8);

        assertEquals(LocalTime.of(19, 30), book(samuel, "guitar", slot(DayOfWeek.WEDNESDAY, "19:30")).time());
    }

    @Test
    void refusesSecondLessonOnTheSameDay() {
        Pupil brian = givenPupil(875924, "Brian Allan", 9, "guitar", 4);
        book(brian, "guitar", MONDAY_AT_FOUR);

        assertRefused(HttpStatus.CONFLICT,
                "Brian Allan already has a lesson on MONDAY and pupils can book one lesson a day",
                () -> book(brian, "guitar", MONDAY_AT_HALF_FOUR));
    }

    @Test
    void allowsLessonsOnDifferentDays() {
        Pupil brian = givenPupil(875924, "Brian Allan", 9, "guitar", 4);

        Lesson mondayLesson = book(brian, "guitar", MONDAY_AT_FOUR);
        Lesson tuesdayLesson = book(brian, "guitar", TUESDAY_AT_FOUR);

        assertEquals(List.of(mondayLesson, tuesdayLesson), bookingService.findLessonsForPupil(875924));
    }

    @Test
    void refusesSlotThatIsAlreadyBooked() {
        book(givenPupil(875924, "Brian Allan", 9, "guitar", 4), "guitar", MONDAY_AT_FOUR);
        Pupil corey = givenPupil(263228, "Corey Watson", 9, "guitar", 4);

        assertRefused(HttpStatus.CONFLICT, "The slot on MONDAY at 16:00 is not available",
                () -> book(corey, "guitar", MONDAY_AT_FOUR));
    }

    @Test
    void refusesSlotThatIsBlocked() {
        Pupil brian = givenPupil(875924, "Brian Allan", 9, "guitar", 4);
        bookingService.blockSlots(DayOfWeek.MONDAY, LocalTime.of(16, 0));

        assertRefused(HttpStatus.CONFLICT, "The slot on MONDAY at 16:00 is not available",
                () -> book(brian, "guitar", MONDAY_AT_FOUR));
    }

    @Test
    void blockingADayBlocksEverySlotButKeepsLessonsAlreadyBooked() {
        Lesson lesson = book(givenPupil(875924, "Brian Allan", 9, "guitar", 4), "guitar", MONDAY_AT_FOUR);

        bookingService.blockSlots(DayOfWeek.MONDAY, null);

        assertEquals(List.of(), bookingService.findAvailableSlots(DayOfWeek.MONDAY));
        assertEquals(8, bookingService.findBlockedSlots(DayOfWeek.MONDAY).size());
        assertEquals(List.of(lesson), bookingService.findLessonsOn(DayOfWeek.MONDAY));
    }

    @Test
    void unblockingASlotMakesItAvailableAgain() {
        bookingService.blockSlots(DayOfWeek.MONDAY, null);

        bookingService.unblockSlots(DayOfWeek.MONDAY, LocalTime.of(16, 0));

        assertEquals(List.of(MONDAY_AT_FOUR), bookingService.findAvailableSlots(DayOfWeek.MONDAY));
        assertEquals(7, bookingService.findBlockedSlots(DayOfWeek.MONDAY).size());
    }

    @Test
    void refusesToBlockTimesThatAreNotLessonSlots() {
        assertRefused(HttpStatus.NOT_FOUND, "There is no lesson slot on MONDAY at 16:45",
                () -> bookingService.blockSlots(DayOfWeek.MONDAY, LocalTime.of(16, 45)));
        assertRefused(HttpStatus.NOT_FOUND, "There is no lesson slot on SUNDAY",
                () -> bookingService.blockSlots(DayOfWeek.SUNDAY, null));
    }

    @Test
    void offersEveryHalfHourOfTheLessonWeek() {
        List<Slot> saturdaySlots = bookingService.findAvailableSlots(DayOfWeek.SATURDAY);

        assertEquals(8, bookingService.findAvailableSlots(DayOfWeek.FRIDAY).size());
        assertEquals(22, saturdaySlots.size());
        assertEquals(slot(DayOfWeek.SATURDAY, "09:00"), saturdaySlots.get(0));
        assertEquals(slot(DayOfWeek.SATURDAY, "19:30"), saturdaySlots.get(21));
        assertEquals(List.of(), bookingService.findAvailableSlots(DayOfWeek.SUNDAY));
        assertEquals(5 * 8 + 22, bookingService.findAvailableSlots(null).size());
    }

    @Test
    void cancelsLessonForThePupilWhoBookedIt() {
        Lesson lesson = book(givenPupil(875924, "Brian Allan", 9, "guitar", 4), "guitar", MONDAY_AT_FOUR);

        bookingService.cancelLesson(lesson.id(), 875924);

        assertEquals(List.of(), bookingService.findLessonsForPupil(875924));
        assertTrue(bookingService.findAvailableSlots(DayOfWeek.MONDAY).contains(MONDAY_AT_FOUR));
        assertRefused(HttpStatus.NOT_FOUND, "There is no lesson with id 1", () -> bookingService.findLesson(lesson.id()));
    }

    @Test
    void refusesToCancelAnotherPupilsLesson() {
        Lesson lesson = book(givenPupil(875924, "Brian Allan", 9, "guitar", 4), "guitar", MONDAY_AT_FOUR);

        assertRefused(HttpStatus.FORBIDDEN, "Only the pupil who booked a lesson can cancel it",
                () -> bookingService.cancelLesson(lesson.id(), 263228));
        assertEquals(lesson, bookingService.findLesson(lesson.id()));
    }

    @Test
    void listsLessonsInDayAndTimeOrder() {
        Lesson saturdayLesson = book(givenPupil(875924, "Brian Allan", 9, "guitar", 4), "guitar",
                slot(DayOfWeek.SATURDAY, "09:00"));
        Lesson lateMondayLesson = book(givenPupil(263228, "Corey Watson", 9, "guitar", 4), "guitar", MONDAY_AT_HALF_FOUR);
        Lesson earlyMondayLesson = book(givenPupil(537697, "Samuel Paterson", 16, "guitar", 8), "guitar", MONDAY_AT_FOUR);

        assertEquals(List.of(earlyMondayLesson, lateMondayLesson, saturdayLesson), bookingService.findLessonsOn(null));
    }

    @Test
    void lessonCheckCancelsOnlyLessonsThatNowBreakTheRules() {
        Lesson briansLesson = book(givenPupil(875924, "Brian Allan", 9, "guitar", 4), "guitar", MONDAY_AT_FOUR);
        Lesson lisasLesson = book(givenPupil(258847, "Lisa Wilson", 13, "trumpet", 3), "trumpet", MONDAY_AT_HALF_FOUR);
        Lesson coreysLesson = book(givenPupil(263228, "Corey Watson", 9, "guitar", 4), "guitar", TUESDAY_AT_FOUR);
        givenPupil(258847, "Lisa Wilson", 13, "trumpet", 6);
        when(pupilLevelTracker.findPupil(263228)).thenReturn(Optional.empty());

        List<LessonBookingService.CancelledLesson> cancelledLessons = bookingService.cancelLessonsBreakingRules();

        assertEquals(List.of(
                new LessonBookingService.CancelledLesson(lisasLesson,
                        "Anna Clarke teaches trumpet from grade 1 to grade 5 but Lisa Wilson is grade 6"),
                new LessonBookingService.CancelledLesson(coreysLesson, "Pupil 263228 has left the school")),
                cancelledLessons);
        assertEquals(List.of(briansLesson), bookingService.findLessonsOn(null));
    }

    private Pupil givenPupil(int matric, String name, int age, String instrument, Integer grade) {
        Pupil pupil = new Pupil(matric, name, age, Map.of(instrument, new Pupil.InstrumentGrade(grade)));
        when(pupilLevelTracker.findPupil(matric)).thenReturn(Optional.of(pupil));
        return pupil;
    }

    private Lesson book(Pupil pupil, String instrument, Slot slot) {
        return bookingService.bookLesson(pupil.matric(), pupil.name(), instrument, slot);
    }

    private static Slot slot(DayOfWeek day, String time) {
        return new Slot(day, LocalTime.parse(time));
    }

    private static void assertRefused(HttpStatus status, String reason, Executable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);
        assertEquals(status, exception.getStatusCode());
        assertEquals(reason, exception.getReason());
    }
}
