let apiRoot;
let authorization = null;

const loginSection = document.getElementById('login');
const loginForm = document.getElementById('login-form');
const dashboard = document.getElementById('dashboard');
const daySelect = document.getElementById('day');
const lessonList = document.getElementById('booked-lessons');
const availableList = document.getElementById('available-slots');
const blockedList = document.getElementById('blocked-slots');
const checkResults = document.getElementById('check-results');

async function startTutorPage() {
    apiRoot = await callApi('/api');
    fillDaySelect(daySelect);
}

function callTutorApi(url, method = 'GET') {
    return callApi(url, {
        method,
        headers: { 'Authorization': authorization, 'X-Requested-With': 'XMLHttpRequest' }
    });
}

async function logIn(event) {
    event.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    authorization = 'Basic ' + btoa(`${username}:${password}`);
    try {
        await loadDay();
        loginSection.hidden = true;
        dashboard.hidden = false;
        showMessage(`Logged in as ${username}.`);
    } catch (error) {
        authorization = null;
        showMessage(error.status === 401 ? 'Incorrect username or password.' : error.message, true);
    }
}

function logOut() {
    authorization = null;
    loginForm.reset();
    dashboard.hidden = true;
    loginSection.hidden = false;
    showMessage('Logged out.');
}

async function loadDay() {
    const day = daySelect.value;
    const [lessons, availableSlots, blockedSlots] = await Promise.all([
        callTutorApi(linkTo(apiRoot, 'tutor-lessons', { day })),
        callApi(linkTo(apiRoot, 'slots', { day })),
        callTutorApi(linkTo(apiRoot, 'blocked-slots', { day }))
    ]);
    showList(lessonList, embedded(lessons, 'lessons').map(lesson => createListItem(
        `${lesson.time} ${lesson.pupilName} (${lesson.matric}), ${lesson.instrument}`)), 'No lessons booked.');
    showList(availableList, embedded(availableSlots, 'slots').map(slot => createListItem(slot.time,
        createButton('Block', () => runAction(() => changeBlock('PUT', slot.day, slot.time))))), 'No free slots.');
    showList(blockedList, embedded(blockedSlots, 'slots').map(slot => createListItem(slot.time,
        createButton('Unblock', () => runAction(() => changeBlock('DELETE', slot.day, slot.time))))), 'No blocked slots.');
}

async function changeBlock(method, day, time) {
    const path = time ? `${day}/${time}` : day;
    await callTutorApi(`${linkTo(apiRoot, 'blocked-slots')}/${path}`, method);
    const action = method === 'PUT' ? 'Blocked' : 'Unblocked';
    showMessage(`${action} ${capitalise(day)}${time ? ' at ' + time : ''}.`);
    await loadDay();
}

async function checkLessons() {
    const result = await callTutorApi(linkTo(apiRoot, 'lesson-checks'), 'POST');
    const cancelledLessons = embedded(result, 'cancelledLessons');
    showList(checkResults, cancelledLessons.map(cancelled => createListItem(
        `${capitalise(cancelled.lesson.day)} at ${cancelled.lesson.time}, ${cancelled.lesson.pupilName}: ${cancelled.reason}`)),
        'Every booking still meets the rules.');
    showMessage(`Checked bookings against the pupil level tracker, ${cancelledLessons.length} cancelled.`);
    await loadDay();
}

loginForm.addEventListener('submit', logIn);
daySelect.addEventListener('change', () => runAction(loadDay));
document.getElementById('block-day').addEventListener('click', () => runAction(() => changeBlock('PUT', daySelect.value)));
document.getElementById('unblock-day').addEventListener('click', () => runAction(() => changeBlock('DELETE', daySelect.value)));
document.getElementById('check-lessons').addEventListener('click', () => runAction(checkLessons));
document.getElementById('log-out').addEventListener('click', logOut);
runAction(startTutorPage);
