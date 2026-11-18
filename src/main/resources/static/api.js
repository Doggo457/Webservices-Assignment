const LESSON_DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

async function callApi(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        const error = new Error(problem.detail || problem.title || `Request failed with status ${response.status}`);
        error.status = response.status;
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

function linkTo(resource, rel, params = {}) {
    const url = new URL(resource._links[rel].href.split(/[?{]/)[0]);
    for (const [name, value] of Object.entries(params)) {
        url.searchParams.set(name, value);
    }
    return url.toString();
}

function embedded(collection, rel) {
    return (collection._embedded && collection._embedded[rel]) || [];
}

function capitalise(word) {
    return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
}

function fillDaySelect(select) {
    for (const day of LESSON_DAYS) {
        select.add(new Option(capitalise(day), day));
    }
}

function createButton(label, onClick) {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.addEventListener('click', onClick);
    return button;
}

function createListItem(text, button) {
    const item = document.createElement('li');
    const label = document.createElement('span');
    label.textContent = text;
    item.append(label);
    if (button) {
        item.append(button);
    }
    return item;
}

function showList(list, items, emptyText) {
    list.replaceChildren(...(items.length > 0 ? items : [createListItem(emptyText)]));
}

function showMessage(text, isError = false) {
    const message = document.getElementById('message');
    message.textContent = text;
    message.className = isError ? 'message error' : 'message success';
}

async function runAction(action) {
    try {
        await action();
    } catch (error) {
        showMessage(error.message, true);
    }
}
