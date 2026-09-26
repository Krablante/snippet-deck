const $ = (id) => document.getElementById(id);
const invoke = (name, args) => window.__TAURI__?.core.invoke(name, args) ?? Promise.reject(new Error('Open SnippetDeck to edit your library.'));
let snippets = [];
let selected = null;
let notificationTimer;
let visible = 80;

function notify(message) {
  const notice = $('notice');
  notice.textContent = message;
  notice.hidden = false;
  clearTimeout(notificationTimer);
  notificationTimer = setTimeout(() => { notice.hidden = true; }, 4500);
}

function errorMessage(error) {
  return typeof error === 'string' ? error : error?.message || 'Something went wrong';
}

function ask(title, message, action) {
  $('confirm-title').textContent = title;
  $('confirm-message').textContent = message;
  $('confirm-action').textContent = action;
  $('confirm-action').classList.add('danger-action');
  const dialog = $('confirm');
  dialog.returnValue = 'cancel';
  return new Promise(resolve => {
    dialog.addEventListener('close', () => resolve(dialog.returnValue === 'yes'), { once: true });
    dialog.showModal();
  });
}

function theme(value) {
  document.documentElement.dataset.theme = value;
  localStorage.setItem('snippetdeck-theme', value);
  document.querySelectorAll('.themes button').forEach(button => {
    button.classList.toggle('selected', button.dataset.theme === value);
  });
}

function menu(open) {
  $('menu').hidden = !open;
  $('more').setAttribute('aria-expanded', String(open));
}

function render() {
  const query = $('search').value.toLocaleLowerCase().trim();
  const filtered = snippets.filter(s => [s.trigger, s.expansion, ...s.aliases]
    .some(value => value.toLocaleLowerCase().includes(query)));
  $('count').textContent = `${filtered.length} ${filtered.length === 1 ? 'snippet' : 'snippets'}`;
  $('empty').hidden = filtered.length !== 0;
  $('empty').textContent = snippets.length ? 'No matching snippets.' : 'No snippets yet. Add one to get started.';
  const list = $('list');
  list.replaceChildren();
  for (const snippet of filtered.slice(0, visible)) {
    const row = document.createElement('button');
    row.type = 'button';
    row.className = `snippet${snippet.enabled ? '' : ' disabled'}${selected === snippet.trigger ? ' selected' : ''}`;
    row.setAttribute('aria-label', `Edit ${snippet.trigger}`);
    const head = document.createElement('div');
    head.className = 'snippet-head';
    const trigger = document.createElement('span');
    trigger.className = 'snippet-trigger';
    const arrow = document.createElement('span');
    arrow.className = 'snippet-arrow';
    arrow.textContent = '→';
    trigger.append(arrow, document.createTextNode(snippet.trigger));
    head.append(trigger);
    if (!snippet.enabled) {
      const off = document.createElement('span');
      off.className = 'snippet-off';
      off.textContent = 'Off';
      head.append(off);
    }
    row.append(head);
    if (snippet.aliases.length) {
      const aliases = document.createElement('div');
      aliases.className = 'snippet-alias';
      aliases.textContent = snippet.aliases.join(', ');
      row.append(aliases);
    }
    const preview = document.createElement('div');
    preview.className = 'snippet-preview';
    preview.textContent = snippet.expansion.replace(/\s+/g, ' ');
    row.append(preview);
    row.addEventListener('click', () => edit(snippet));
    list.append(row);
  }
  if (filtered.length > visible) {
    const more = document.createElement('button');
    more.type = 'button';
    more.className = 'show-more';
    more.textContent = `Show more · ${filtered.length - visible} remaining`;
    more.addEventListener('click', () => { visible += 80; render(); });
    list.append(more);
  }
}

function edit(snippet = null) {
  selected = snippet?.trigger ?? null;
  $('editor-title').textContent = snippet ? 'Edit snippet' : 'New snippet';
  $('delete').hidden = !snippet;
  $('trigger').value = snippet?.trigger ?? '';
  $('aliases').value = snippet?.aliases.join(', ') ?? '';
  $('expansion').value = snippet?.expansion ?? '';
  $('snippet-enabled').checked = snippet?.enabled ?? true;
  $('form-error').hidden = true;
  $('editor').hidden = false;
  $('welcome').hidden = true;
  render();
  $('trigger').focus();
}

function closeEditor() {
  selected = null;
  $('editor').hidden = true;
  $('welcome').hidden = false;
  render();
}

async function refresh() {
  try {
    const snapshot = await invoke('snapshot');
    snippets = snapshot.snippets;
    $('active').checked = snapshot.active;
    $('startup').checked = snapshot.startAtLogin;
    $('status').textContent = snapshot.status;
    render();
  } catch (error) { $('status').textContent = errorMessage(error); render(); }
}

theme(localStorage.getItem('snippetdeck-theme') || 'white');
refresh();
$('new').addEventListener('click', () => edit());
$('back').addEventListener('click', closeEditor);
$('search').addEventListener('input', () => { visible = 80; render(); });
$('more').addEventListener('click', () => menu($('menu').hidden));
document.addEventListener('click', event => {
  if (!$('more').contains(event.target) && !$('menu').contains(event.target)) menu(false);
});
document.querySelectorAll('.themes button').forEach(button => button.addEventListener('click', () => {
  theme(button.dataset.theme);
  menu(false);
}));
$('active').addEventListener('change', async () => {
  try { await invoke('set_active', { active: $('active').checked }); }
  catch (error) { notify(errorMessage(error)); await refresh(); }
});
$('startup').addEventListener('change', async () => {
  try { await invoke('set_start_at_login', { enabled: $('startup').checked }); }
  catch (error) { notify(errorMessage(error)); await refresh(); }
});
window.addEventListener('focus', refresh);
$('form').addEventListener('submit', async event => {
  event.preventDefault();
  const previous = selected;
  const snippet = {
    trigger: $('trigger').value.trim(),
    expansion: $('expansion').value,
    aliases: $('aliases').value.split(/[,;\n]/).map(s => s.trim()).filter(Boolean),
    enabled: $('snippet-enabled').checked,
    createdAt: 0, updatedAt: 0
  };
  try {
    await invoke('save_snippet', { previous, snippet });
    await refresh();
    const saved = snippets.find(s => s.trigger.toLocaleLowerCase() === (snippet.trigger.startsWith('!') ? snippet.trigger : `!${snippet.trigger}`).toLocaleLowerCase());
    if (saved) edit(saved);
    notify('Snippet saved');
  } catch (error) {
    $('form-error').textContent = errorMessage(error);
    $('form-error').hidden = false;
  }
});
$('delete').addEventListener('click', async () => {
  if (!selected || !await ask('Delete snippet?', `${selected} will be removed from this device.`, 'Delete')) return;
  try {
    await invoke('remove_snippet', { trigger: selected });
    closeEditor();
    await refresh();
    notify('Snippet deleted');
  } catch (error) { notify(errorMessage(error)); }
});
$('import').addEventListener('click', async () => {
  menu(false);
  try {
    const count = await invoke('choose_import');
    if (count === null) return;
    if (!await ask('Replace your library?', `Import ${count} ${count === 1 ? 'snippet' : 'snippets'} and remove everything currently on this device?\n\nExport a backup first if you want to keep it.`, 'Replace')) return;
    await invoke('finish_import');
    closeEditor();
    await refresh();
    notify(`${count} ${count === 1 ? 'snippet' : 'snippets'} imported`);
  } catch (error) { notify(errorMessage(error)); }
});
$('export').addEventListener('click', async () => {
  menu(false);
  try { if (await invoke('export_file')) notify('Backup exported'); }
  catch (error) { notify(errorMessage(error)); }
});
