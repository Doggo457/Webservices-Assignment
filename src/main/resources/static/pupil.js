let apiRoot;

const detailsForm = document.getElementById('pupil-details');
const matricInput = document.getElementById('matric');
const nameInput = document.getElementById('pupil-name');
const instrumentSelect = document.getElementById('instrument');
const daySelect = document.getElementById('day');
const slotGrid = document.getElementById('available-slots');
const lessonList = document.getElementById('my-lessons');

async function startPupilPage() {
    apiRoot = await callApi('/api');
    const tutor = await callApi(linkTo(apiRoot, 'tutor'));
    showTutor(tutor);
    fillDaySelect(daySelect);
    await loadAvailableSlots();
}

function showTutor(tutor) {
    document.getElementById('tutor-name').textContent = tutor.name;
    const instrumentList = document.getElementById('tutor-instruments');
    for (const [instrument, gradeRange] of Object.entries(tutor.instruments)) {
        instrumentSelect.add(new Option(capitalise(instrument), instrument));
        instrumentList.append(createListItem(`${capitalise(instrument)}: ${describeGradeRange(gradeRange)}`));
    }
}

function describeGradeRange(gradeRange) {
    const lowest = gradeRange.lowestGrade === 0 ? 'Beginner' : `Grade ${gradeRange.lowestGrade}`;
    return `${lowest} to grade ${gradeRange.highestGrade}`;
}

async function loadAvailableSlots() {
    const slots = embedded(await callApi(linkTo(apiRoot, 'slots', { day: daySelect.value })), 'slots');
    slotGrid.replaceChildren(...slots.map(slot => createButton(slot.time, () => runAction(() => bookLesson(slot)))));
    if (slots.length === 0) {
        slotGrid.textContent = 'No free slots on this day.';
    }
}

async function bookLesson(slot) {
    if (!detailsForm.reportValidity()) {
        return;
    }
    const lesson = await callApi(linkTo(apiRoot, 'lessons'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            matric: Number(matricInput.value),
            name: nameInput.value,
            instrument: instrumentSelect.value,
            day: slot.day,
            time: slot.time
        })
    });
    showMessage(`Booked a ${lesson.instrument} lesson on ${capitalise(lesson.day)} at ${lesson.time}.`);
    await loadAvailableSlots();
    await loadMyLessons();
}

async function loadMyLessons() {
    if (!matricInput.reportValidity()) {
        return;
    }
    const lessons = embedded(await callApi(linkTo(apiRoot, 'lessons', { matric: matricInput.value })), 'lessons');
    showList(lessonList, lessons.map(lesson => createListItem(
        `${capitalise(lesson.day)} at ${lesson.time}, ${lesson.instrument} for ${lesson.pupilName}`,
        createButton('Cancel', () => runAction(() => cancelLesson(lesson))))), 'No lessons booked.');
}

async function cancelLesson(lesson) {
    await callApi(lesson._links.cancel.href, { method: 'DELETE' });
    showMessage(`Cancelled the ${lesson.instrument} lesson on ${capitalise(lesson.day)} at ${lesson.time}.`);
    await loadAvailableSlots();
    await loadMyLessons();
}

daySelect.addEventListener('change', () => runAction(loadAvailableSlots));
document.getElementById('show-lessons').addEventListener('click', () => runAction(loadMyLessons));
runAction(startPupilPage);
