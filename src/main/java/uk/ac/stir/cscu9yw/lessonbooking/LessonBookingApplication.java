package uk.ac.stir.cscu9yw.lessonbooking;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LessonBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LessonBookingApplication.class, args);
    }

    @Bean
    public Tutor tutor() {
        return new Tutor("Anna Clarke", new TreeMap<>(Map.of(
                "piano", new Tutor.GradeRange(3, 7),
                "guitar", Tutor.GradeRange.ALL_GRADES,
                "trumpet", new Tutor.GradeRange(1, 5))));
    }
}
