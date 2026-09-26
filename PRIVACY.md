# SnippetDeck privacy

SnippetDeck expands text you type into editable fields. The Android accessibility service and desktop keyboard agent observe enough input to recognize triggers. They do not store or upload the text you type in other apps. Snippets you create are stored locally on your device.

## Optional Google Drive sync

If you choose **Connect Google Drive**, SnippetDeck requests the `drive.appdata` permission. It can read and write only its own app-data folder in your Google Drive, not your other Drive files. To sync devices, it sends your snippets, aliases, expansions, enabled states, timestamps, deletion records, and random device identifiers to that folder. The files count against your Google Drive storage. Google processes and stores this data under your Google account; **SnippetDeck does not end-to-end encrypt it**. The SnippetDeck maintainer has no server that receives the library or your Google tokens.

On Android, Google Play services handles authorization. On desktop, the app opens the system browser for Google sign-in and stores a refresh token in the operating system's credential store when available. If no persistent credential store is available, authorization lasts only for the current app session. Tokens are never included in exported backups. Disconnecting stops future sync on that device and keeps its local snippets; it does not remove files already in Google Drive. You can revoke access in your [Google Account's connected apps](https://myaccount.google.com/connections) and delete the app's hidden data through Google Drive's app management controls.

## Other data flows

On Android and desktop, SnippetDeck contacts GitHub to check public release metadata and downloads an installer only with your approval. No snippet content is included in update requests. Manual file and clipboard backups contain your snippets and go only where you choose to save or paste them. Desktop multiline insertion temporarily uses the system clipboard; local clipboard managers may retain that text.

There is no analytics, advertising, SnippetDeck account, or SnippetDeck backend. For questions or privacy requests, open a [GitHub issue](https://github.com/Krablante/snippet-deck/issues).
