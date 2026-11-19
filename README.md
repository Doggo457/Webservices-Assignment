# Music Lesson Booking

A RESTful web service for booking one-to-one music lessons with a tutor, plus browser clients for pupils and tutors. Built with Java 17, Spring Boot 4.1, Spring HATEOAS and Spring Security. Pupil records come from the external pupil level tracker service.

## Running

You need JDK 17 or newer. Maven does not need to be installed because the Maven wrapper is included.

On Windows the quickest way is to double-click `run.bat`. It compiles the service, starts it and opens the pupil and tutor pages.

To run it by hand:

```
mvnw.cmd spring-boot:run        (Windows)
./mvnw spring-boot:run          (macOS or Linux)
```

To build and run a standalone jar instead:

```
mvnw.cmd package
java -jar target/lesson-booking-1.0.0.jar
```

Then open:

| Page | URL |
|---|---|
| Pupil client | http://localhost:8080/ |
| Tutor client | http://localhost:8080/tutor.html |
| API root | http://localhost:8080/api |

The demo tutor login is username `anna`, password `scales2026`. Only a BCrypt hash of the password is stored, in `application.properties`.

## API

All responses are HAL JSON with `_links`. Errors are returned as `application/problem+json` with a `detail` message.

| Method | URI | Purpose | Access |
|---|---|---|---|
| GET | `/api` | API root with links to every resource | Public |
| GET | `/api/tutor` | Tutor name, instruments and grade ranges | Public |
| GET | `/api/slots?day=MONDAY` | Free lesson slots, day optional | Public |
| POST | `/api/lessons` | Book a lesson | Public |
| GET | `/api/lessons?matric=123456` | A pupil's booked lessons | Public |
| GET | `/api/lessons/{lessonId}` | One booked lesson | Public |
| DELETE | `/api/lessons/{lessonId}?matric=123456` | Cancel a lesson | Public, own lessons only |
| GET | `/api/tutor/lessons?day=MONDAY` | Booked lessons, day optional | Tutor |
| GET | `/api/tutor/blocked-slots?day=MONDAY` | Blocked slots, day optional | Tutor |
| PUT, DELETE | `/api/tutor/blocked-slots/{day}` | Block or unblock a whole day | Tutor |
| PUT, DELETE | `/api/tutor/blocked-slots/{day}/{time}` | Block or unblock one slot | Tutor |
| POST | `/api/tutor/lesson-checks` | Recheck bookings against the tracker and cancel any that break the rules | Tutor |

Example booking:

```
curl -X POST http://localhost:8080/api/lessons -H "Content-Type: application/json" -d "{\"matric\":875924,\"name\":\"Brian Allan\",\"instrument\":\"guitar\",\"day\":\"MONDAY\",\"time\":\"16:00\"}"
```

Example tutor request:

```
curl -u anna:scales2026 "http://localhost:8080/api/tutor/lessons?day=MONDAY"
```

## Booking rules

* Lessons are 30 minutes, Monday to Friday 16:00 to 20:00 and Saturday 09:00 to 20:00, for one week.
* The tutor teaches piano grades 3 to 7, guitar at all grades and trumpet grades 1 to 5. Pupils with no grade yet count as grade 0.
* The name must match the tracker record for the matriculation number.
* A pupil can book at most one lesson a day.
* Pupils under 16 cannot book lessons starting at 18:00 or later.
* Blocked or already booked slots cannot be booked. Blocking a slot does not cancel a lesson already booked in it.

## Status codes

| Code | Meaning |
|---|---|
| 201 | Lesson booked, `Location` header holds the new lesson URI |
| 204 | Cancelled, blocked or unblocked |
| 400 | Missing fields, bad day or time format, or not a lesson time |
| 401 | Tutor login missing or wrong |
| 403 | Cancelling another pupil's lesson |
| 404 | Lesson or slot does not exist |
| 409 | Slot not available, or pupil already has a lesson that day |
| 422 | Pupil unknown, name mismatch, instrument not taught, grade out of range, or under 16 in the evening |
| 503 | Pupil level tracker unavailable or rate limiting |

## Configuration

Settings are in `src/main/resources/application.properties`: the tracker URL and timeouts, and the tutor login. To change the tutor password, generate a BCrypt hash and set `spring.security.user.password={bcrypt}<hash>`.

Data is held in memory, so bookings and blocked slots reset when the service restarts.
