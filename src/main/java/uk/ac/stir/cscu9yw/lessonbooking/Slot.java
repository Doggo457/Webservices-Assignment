package uk.ac.stir.cscu9yw.lessonbooking;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "slots")
public record Slot(DayOfWeek day, @JsonFormat(pattern = "HH:mm") LocalTime time) implements Comparable<Slot> {

    private static final Duration LESSON_LENGTH = Duration.ofMinutes(30);
    private static final LocalTime WEEKDAY_OPENING = LocalTime.of(16, 0);
    private static final LocalTime SATURDAY_OPENING = LocalTime.of(9, 0);
    private static final LocalTime CLOSING = LocalTime.of(20, 0);
    private static final List<Slot> LESSON_WEEK = createLessonWeek();
    private static final Comparator<Slot> BY_DAY_THEN_TIME = Comparator.comparing(Slot::day).thenComparing(Slot::time);

    public static List<Slot> lessonWeek() {
        return LESSON_WEEK;
    }

    @Override
    public int compareTo(Slot other) {
        return BY_DAY_THEN_TIME.compare(this, other);
    }

    @Override
    public String toString() {
        return day + " at " + time;
    }

    private static List<Slot> createLessonWeek() {
        List<Slot> slots = new ArrayList<>();
        for (DayOfWeek day : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY)) {
            LocalTime opening = day == DayOfWeek.SATURDAY ? SATURDAY_OPENING : WEEKDAY_OPENING;
            for (LocalTime time = opening; time.isBefore(CLOSING); time = time.plus(LESSON_LENGTH)) {
                slots.add(new Slot(day, time));
            }
        }
        return List.copyOf(slots);
    }
}
/*
⠀⣠⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣰⣄⡀⠀⠀⠀
⣿⠋⣻⢲⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⡀⠀⠙⣆⠀⠀
⣿⣦⡀⠀⢘⢷⣀⠀⢀⣰⢟⠛⣦⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⠀⠀⢀⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⡀⡄⠀⠀⠈⢷⡀⠀⢹⢷⠀
⠈⢿⡄⠀⠈⠉⠈⠹⣟⠋⠀⣵⠟⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⣿⣤⣤⣼⣷⣄⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢰⡏⠙⣦⡀⠀⣼⡇⠀⠀⢸⡇
⠀⠈⣇⠀⠀⠀⠀⠞⠉⢀⡾⠁⠀⠀⠀⠀⠀⠀⠀⣠⡶⣟⢟⣉⠀⠀⣀⣀⣉⠉⠉⠓⠦⣄⠀⠀⠀⠀⠀⠸⣇⠀⢘⣧⣴⠿⠀⠀⠀⢸⡇
⠀⠀⠛⣇⠀⢈⠀⠀⠀⠘⡇⠀⠀⠀⠀⠀⢀⣤⢾⣷⡶⠛⠛⠛⠀⠀⠈⠉⠉⠙⠳⡄⠀⠈⠳⣄⠀⠀⠀⠀⢸⡇⠀⠙⠁⠀⠀⠀⠀⢺⡇
⠀⠀⠀⢿⡀⠀⠀⠒⠀⣈⡇⠀⠀⠀⠀⢠⡟⠑⠁⠀⢀⡀⣠⣴⣷⣶⣶⣶⣤⣀⠀⠀⠀⠀⠀⠈⢳⡄⠀⠀⢸⡇⠀⠀⠀⠀⠀⢠⢾⠟⠁
⠀⠀⠀⠈⠳⣄⠀⠀⢨⣿⠉⠀⠀⠀⢠⡟⠀⠀⠀⠀⣤⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⣄⠀⠀⠀⠀⢈⢻⡄⠈⠉⠳⣇⠀⢀⢄⣤⠞⠁⠀⠀
⠀⠀⠀⠀⠀⠈⠹⠏⠏⠁⠀⠀⠀⠀⡿⠀⠀⠀⢀⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡆⠀⠀⠀⠈⢏⡇⠀⠀⠀⠈⠹⠉⠉⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⡇⠀⠀⠀⣰⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡆⠀⠀⠀⠈⣻⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣇⠀⠀⢰⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⡄⠀⠀⠀⣿⠂⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣯⠀⠂⣿⣿⣿⣿⣿⡿⠿⠉⠉⠉⠛⠿⣿⣿⣿⣿⣿⣧⠀⠀⢰⣿⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢻⠀⠠⣿⣿⣿⣿⠯⠀⠀⠀⠀⠀⠀⠀⠛⢿⣿⣿⣿⣿⠏⠀⣾⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⣇⠺⣿⣿⡿⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢹⢿⣿⣿⡃⢼⡏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢹⣤⠛⠏⠀⠀⠀⠀⠀⡀⠀⠀⠀⠀⠀⠀⠀⠈⣛⣿⣳⣿⠇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠉⠛⠓⠶⣄⡀⠀⠀⠀⠀⠀⠀⠀⢀⡀⣶⣶⠿⠟⠻⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠙⠛⠒⠶⠶⠒⠲⠶⠞⠛⠉⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
*/