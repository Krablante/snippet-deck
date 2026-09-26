const $ = (id) => document.getElementById(id);
const invoke = (name, args) => window.__TAURI__?.core.invoke(name, args) ?? Promise.reject(new Error('Open SnippetDeck to edit your library.'));
let snippets = [];
let selected = null;
let notificationTimer;
let visible = 80;
let syncConnected = false;
let syncBusy = false;
let syncPending = null;
let lastPull = 0;
let updateAvailable = null;
let updateBusy = false;

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

function ask(title, message, action, danger = true) {
  $('confirm-title').textContent = title;
  $('confirm-message').textContent = message;
  $('confirm-action').textContent = action;
  $('confirm-action').classList.toggle('danger-action', danger);
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

function showUpdate(release) {
  updateAvailable = release;
  $('update-row').hidden = !release;
  if (release) {
    $('update-version').textContent = release.version;
    if (!updateBusy) $('update-status').textContent = 'A newer release is ready.';
  }
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
    syncConnected = snapshot.syncConnected;
    $('sync').textContent = syncConnected ? 'Sync now' : 'Connect';
    $('replace-cloud').hidden = !syncConnected;
    $('use-other').hidden = !syncConnected;
    $('disconnect-sync').hidden = !syncConnected;
    $('reset-sync').hidden = !snapshot.syncHistory;
    if (snapshot.updateAvailable) showUpdate(snapshot.updateAvailable);
    render();
  } catch (error) { $('status').textContent = errorMessage(error); render(); }
}

async function syncDrive(interactive = false, keepLocal = false, useOther = false) {
  if (syncBusy) {
    if (interactive || !syncPending) syncPending = [interactive, keepLocal, useOther];
    return;
  }
  syncBusy = true;
  $('sync').disabled = true;
  $('sync-status').textContent = interactive && !syncConnected ? 'Opening Google sign-in…' : 'Syncing…';
  try {
    const result = await invoke('sync_now', { interactive, keepLocal, useOther });
    lastPull = Date.now();
    await refresh();
    if (result.conflicts.length) {
      $('sync-status').textContent = `Conflicting edits: ${result.conflicts.join(', ')}`;
      notify('Both devices edited the same snippet. Review the library before choosing a copy.');
    } else {
      $('sync-status').textContent = `Up to date · ${result.count} snippets`;
    }
  } catch (error) {
    $('sync-status').textContent = `Sync failed: ${errorMessage(error)}`;
    if (interactive) notify(errorMessage(error));
  } finally {
    syncBusy = false;
    $('sync').disabled = false;
    const next = syncPending;
    syncPending = null;
    if (next && (syncConnected || next[0])) {
      syncDrive(...next);
    }
  }
}

theme(localStorage.getItem('snippetdeck-theme') || 'white');
Promise.all([
  window.__TAURI__?.event?.listen('snippetdeck-update-available', event => showUpdate(event.payload)),
  window.__TAURI__?.event?.listen('snippetdeck-update-progress', event => {
    $('update-status').textContent = `Downloading installer… ${event.payload}%`;
  })
]).catch(() => {}).then(() => refresh().then(() => { if (syncConnected) syncDrive(); }));
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
window.addEventListener('focus', async () => {
  await refresh();
  if (syncConnected && Date.now() - lastPull > 60_000) syncDrive();
});
$('sync').addEventListener('click', () => syncDrive(true));
$('check-update').addEventListener('click', async () => {
  menu(false);
  const button = $('check-update');
  button.disabled = true;
  button.textContent = 'Checking…';
  try {
    const release = await invoke('check_for_updates');
    showUpdate(release);
    notify(release ? `SnippetDeck ${release.version} is available` : 'SnippetDeck is up to date');
  } catch (error) {
    notify(`Update check failed: ${errorMessage(error)}`);
  } finally {
    button.disabled = false;
    button.textContent = 'Check for updates';
  }
});
$('install-update').addEventListener('click', async () => {
  if (updateBusy || !updateAvailable) return;
  if (!await ask(`Install SnippetDeck ${updateAvailable.version}?`, 'Download the installer from GitHub, verify its SHA-256, and open the system installer? Your local library will remain on this device.', 'Download & open', false)) return;
  updateBusy = true;
  $('install-update').disabled = true;
  $('update-status').textContent = 'Downloading installer…';
  try {
    const path = await invoke('install_update');
    $('update-status').textContent = `Installer opened · ${path}`;
    notify('Finish the update in the system installer');
  } catch (error) {
    $('update-status').textContent = `Update failed: ${errorMessage(error)}`;
    notify(errorMessage(error));
  } finally {
    updateBusy = false;
    $('install-update').disabled = false;
  }
});
$('replace-cloud').addEventListener('click', async () => {
  menu(false);
  if (await ask('Use this device’s library?', `This will replace conflicting cloud versions with the ${snippets.length} snippets on this device, including deletions. Export a backup first if you need the other edits.`, 'Use this device')) syncDrive(true, true);
});
$('use-other').addEventListener('click', async () => {
  menu(false);
  if (await ask('Use the other device’s library?', 'Replace snippets on this device with the copy from Google Drive, including deletions? Export a backup first if you need this device’s edits.', 'Use other device')) syncDrive(true, false, true);
});
$('disconnect-sync').addEventListener('click', async () => {
  menu(false);
  try {
    await invoke('disconnect_sync');
    await refresh();
    $('sync-status').textContent = 'Not connected';
    notify('Google Drive disconnected on this device');
  } catch (error) { notify(errorMessage(error)); }
});
$('reset-sync').addEventListener('click', async () => {
  menu(false);
  if (!await ask('Switch Google account?', 'This clears sync history on this device, not your snippets or cloud files. Choose a different Google account on the next connection. Reconnecting the same account may restore old deleted snippets.', 'Reset sync')) return;
  try {
    await invoke('reset_sync');
    await refresh();
    $('sync-status').textContent = 'Not connected';
  } catch (error) { notify(errorMessage(error)); }
});
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
    if (syncConnected) syncDrive();
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
    if (syncConnected) syncDrive();
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
    if (syncConnected) syncDrive();
  } catch (error) { notify(errorMessage(error)); }
});
$('export').addEventListener('click', async () => {
  menu(false);
  try { if (await invoke('export_file')) notify('Backup exported'); }
  catch (error) { notify(errorMessage(error)); }
});
