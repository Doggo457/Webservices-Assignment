package uk.ac.stir.cscu9yw.lessonbooking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Pupil(int matric, String name, int age, Map<String, InstrumentGrade> instruments) {

    public static final int UNGRADED = 0;

    public Pupil {
        instruments = instruments == null ? Map.of() : instruments;
    }

    public boolean hasName(String otherName) {
        return name.equalsIgnoreCase(otherName.trim());
    }

    public Optional<Integer> gradeFor(String instrument) {
        InstrumentGrade instrumentGrade = instruments.get(instrument);
        if (instrumentGrade == null) {
            return Optional.empty();
        }
        return Optional.of(instrumentGrade.grade() == null ? UNGRADED : instrumentGrade.grade());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstrumentGrade(Integer grade) {
    }
}
