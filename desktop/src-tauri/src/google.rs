use crate::sync::Replica;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    io::{Read, Write},
    net::TcpListener,
    thread,
    time::{Duration, Instant},
};
use url::Url;

const CLIENT_ID: &str = "356275794423-basgnoa6re20s3daa4vep06l2g7fv04n.apps.googleusercontent.com";
const SCOPE: &str = "https://www.googleapis.com/auth/drive.appdata";
const MAX_BYTES: usize = 6_000_000;

pub struct Access {
    pub token: String,
    pub refresh: String,
    pub expires: Instant,
}

pub fn credential() -> Result<keyring::Entry, String> {
    keyring::Entry::new("io.github.krablante.snippetdeck", "google-drive")
        .map_err(|e| format!("System credential store is unavailable: {e}"))
}

pub fn saved_refresh() -> Option<String> {
    credential().ok()?.get_password().ok()
}

pub fn forget_refresh() {
    if let Ok(entry) = credential() {
        let _ = entry.delete_credential();
    }
}

pub fn refresh(token: &str) -> Result<Access, String> {
    let form = url::form_urlencoded::Serializer::new(String::new())
        .append_pair("client_id", CLIENT_ID)
        .append_pair("client_secret", desktop_client_secret()?)
        .append_pair("refresh_token", token)
        .append_pair("grant_type", "refresh_token")
        .finish();
    exchange(&form, Some(token.to_owned()))
}

pub fn authorize() -> Result<Access, String> {
    let client_secret = desktop_client_secret()?;
    let listener = TcpListener::bind("127.0.0.1:0")
        .map_err(|e| format!("Cannot open browser callback: {e}"))?;
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let redirect = format!(
        "http://127.0.0.1:{}",
        listener.local_addr().map_err(|e| e.to_string())?.port()
    );
    let state = uuid::Uuid::new_v4().to_string();
    let verifier = format!(
        "{}{}",
        uuid::Uuid::new_v4().simple(),
        uuid::Uuid::new_v4().simple()
    );
    let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));
    let mut url =
        Url::parse("https://accounts.google.com/o/oauth2/v2/auth").map_err(|e| e.to_string())?;
    url.query_pairs_mut()
        .append_pair("client_id", CLIENT_ID)
        .append_pair("redirect_uri", &redirect)
        .append_pair("response_type", "code")
        .append_pair("scope", SCOPE)
        .append_pair("access_type", "offline")
        .append_pair("prompt", "consent select_account")
        .append_pair("state", &state)
        .append_pair("code_challenge", &challenge)
        .append_pair("code_challenge_method", "S256");
    webbrowser::open(url.as_str()).map_err(|e| format!("Cannot open browser: {e}"))?;
    let deadline = Instant::now() + Duration::from_secs(120);
    let code = loop {
        if Instant::now() >= deadline {
            return Err("Google sign-in timed out".into());
        }
        match listener.accept() {
            Ok((mut stream, _)) => {
                stream
                    .set_read_timeout(Some(Duration::from_secs(5)))
                    .map_err(|e| e.to_string())?;
                let mut buffer = [0u8; 8192];
                let mut request_bytes = Vec::new();
                while request_bytes.len() < buffer.len() {
                    let count = stream
                        .read(&mut buffer[request_bytes.len()..])
                        .map_err(|e| e.to_string())?;
                    if count == 0 {
                        break;
                    }
                    request_bytes.extend_from_slice(
                        &buffer[request_bytes.len()..request_bytes.len() + count],
                    );
                    if request_bytes.windows(2).any(|part| part == b"\r\n") {
                        break;
                    }
                }
                let request =
                    std::str::from_utf8(&request_bytes).map_err(|_| "Invalid browser callback")?;
                let path = request
                    .split_whitespace()
                    .nth(1)
                    .ok_or("Invalid browser callback")?;
                if !path.starts_with('/') {
                    return Err("Invalid browser callback".into());
                }
                let callback = Url::parse(&format!("{redirect}{path}"))
                    .map_err(|_| "Invalid browser callback")?;
                let params: std::collections::HashMap<_, _> =
                    callback.query_pairs().into_owned().collect();
                if params.get("state") != Some(&state) {
                    continue;
                }
                let body = b"Return to SnippetDeck to finish connecting Google Drive.";
                let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Security-Policy: default-src 'none'\r\nCache-Control: no-store\r\nContent-Length: {}\r\n\r\n", body.len());
                let _ = stream.write_all(response.as_bytes());
                let _ = stream.write_all(body);
                if let Some(error) = params.get("error") {
                    return Err(format!("Google sign-in was denied: {error}"));
                }
                break params
                    .get("code")
                    .cloned()
                    .ok_or("Google did not return a code")?;
            }
            Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(100));
            }
            Err(error) => return Err(format!("Cannot receive browser callback: {error}")),
        }
    };
    let form = url::form_urlencoded::Serializer::new(String::new())
        .append_pair("code", &code)
        .append_pair("client_id", CLIENT_ID)
        .append_pair("client_secret", client_secret)
        .append_pair("code_verifier", &verifier)
        .append_pair("redirect_uri", &redirect)
        .append_pair("grant_type", "authorization_code")
        .finish();
    exchange(&form, None)
}

fn exchange(form: &str, old_refresh: Option<String>) -> Result<Access, String> {
    let mut response = agent(false)
        .post("https://oauth2.googleapis.com/token")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .send(form)
        .map_err(|e| format!("Cannot reach Google sign-in: {e}"))?;
    let status = response.status().as_u16();
    let result: Value = response
        .body_mut()
        .read_json()
        .map_err(|_| "Invalid Google sign-in response")?;
    if !(200..300).contains(&status) {
        let code = result.get("error").and_then(Value::as_str);
        return Err(match code {
            Some("invalid_client") => "Google rejected the desktop OAuth client (invalid_client)".into(),
            Some("invalid_request") => "Google rejected the desktop sign-in request (invalid_request)".into(),
            Some("invalid_grant") => "Google sign-in expired or was rejected (invalid_grant); try again".into(),
            _ => format!("Google sign-in failed (HTTP {status})"),
        });
    }
    let refresh = result
        .get("refresh_token")
        .and_then(Value::as_str)
        .map(str::to_owned)
        .or(old_refresh)
        .ok_or("Google did not grant offline access")?;
    Ok(Access {
        token: result
            .get("access_token")
            .and_then(Value::as_str)
            .ok_or("Google did not return an access token")?
            .to_owned(),
        refresh,
        expires: Instant::now()
            + Duration::from_secs(
                result
                    .get("expires_in")
                    .and_then(Value::as_u64)
                    .unwrap_or(3600)
                    .saturating_sub(60),
            ),
    })
}

fn desktop_client_secret() -> Result<&'static str, String> {
    option_env!("SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET")
        .map(str::trim)
        .filter(|secret| !secret.is_empty())
        .ok_or_else(|| "Google Drive sign-in is unavailable in this desktop build".into())
}

fn agent(http_status_as_error: bool) -> ureq::Agent {
    ureq::Agent::config_builder()
        .timeout_global(Some(Duration::from_secs(30)))
        .http_status_as_error(http_status_as_error)
        .build()
        .into()
}

pub struct RemoteFile {
    pub id: String,
    pub name: String,
    pub size: u64,
}

pub struct Drive {
    agent: ureq::Agent,
    token: String,
}

impl Drive {
    pub fn new(token: String) -> Self {
        Self {
            agent: agent(true),
            token,
        }
    }

    pub fn account_id(&self) -> Result<String, String> {
        let data: Value = serde_json::from_str(
            &self.get("https://www.googleapis.com/drive/v3/about?fields=user(permissionId)")?,
        )
        .map_err(|_| "Invalid Google Drive account response")?;
        data.pointer("/user/permissionId")
            .and_then(Value::as_str)
            .filter(|id| !id.is_empty())
            .map(str::to_owned)
            .ok_or_else(|| "Google Drive did not identify the account".into())
    }

    pub fn list(&self) -> Result<Vec<RemoteFile>, String> {
        let mut files = Vec::new();
        let mut next: Option<String> = None;
        loop {
            let mut url = Url::parse("https://www.googleapis.com/drive/v3/files")
                .map_err(|e| e.to_string())?;
            url.query_pairs_mut()
                .append_pair("spaces", "appDataFolder")
                .append_pair(
                    "q",
                    "name contains 'snippetdeck-sync-v1-' and trashed = false",
                )
                .append_pair("fields", "nextPageToken,files(id,name,size)")
                .append_pair("pageSize", "100");
            if let Some(page) = &next {
                url.query_pairs_mut().append_pair("pageToken", page);
            }
            let result: Value = serde_json::from_str(&self.get(url.as_str())?)
                .map_err(|_| "Invalid Google Drive listing")?;
            for item in result["files"]
                .as_array()
                .ok_or("Invalid Google Drive listing")?
            {
                let Some(name) = item["name"].as_str() else { continue };
                let Some(id) = item["id"].as_str() else { continue };
                if name.starts_with("snippetdeck-sync-v1-") && name.ends_with(".json") {
                    files.push(RemoteFile {
                        id: id.to_owned(),
                        name: name.to_owned(),
                        size: item["size"]
                            .as_str()
                            .and_then(|s| s.parse().ok())
                            .unwrap_or(0),
                    });
                }
            }
            if files.len() > 50 {
                return Err("Too many SnippetDeck devices in this Drive".into());
            }
            next = result["nextPageToken"].as_str().map(str::to_owned);
            if next.is_none() {
                return Ok(files);
            }
        }
    }

    pub fn read(&self, file: &RemoteFile) -> Result<Replica, String> {
        if file.size == 0 || file.size > MAX_BYTES as u64 {
            return Err("Cloud library is too large or empty".into());
        }
        let url = drive_file_url(
            "https://www.googleapis.com/drive/v3/files/",
            &file.id,
            "alt",
            "media",
        )?;
        let replica: Replica =
            serde_json::from_str(&self.get(&url)?).map_err(|_| "Invalid cloud library")?;
        replica.validate()?;
        if file.name != format!("snippetdeck-sync-v1-{}.json", replica.device_id) {
            return Err("Cloud library does not match its device".into());
        }
        Ok(replica)
    }

    pub fn write(&self, replica: &Replica, file_id: Option<&str>) -> Result<String, String> {
        let text = serde_json::to_string(replica).map_err(|e| e.to_string())?;
        if text.len() > MAX_BYTES {
            return Err("Cloud library is too large".into());
        }
        if let Some(id) = file_id {
            let url = drive_file_url(
                "https://www.googleapis.com/upload/drive/v3/files/",
                id,
                "uploadType",
                "media",
            )?;
            self.agent
                .post(&url)
                .header("X-HTTP-Method-Override", "PATCH")
                .header("Authorization", &format!("Bearer {}", self.token))
                .header("Content-Type", "application/json")
                .send(text.as_bytes())
                .map_err(|_| "Cannot update Google Drive library")?;
            return Ok(id.to_owned());
        }
        let boundary = format!("snippetdeck-{}", replica.device_id);
        let metadata = json!({
            "name": format!("snippetdeck-sync-v1-{}.json", replica.device_id),
            "parents": ["appDataFolder"],
            "mimeType": "application/json",
        });
        let body = format!("--{boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{metadata}\r\n--{boundary}\r\nContent-Type: application/json\r\n\r\n{text}\r\n--{boundary}--\r\n");
        let mut response = self
            .agent
            .post("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", &format!("Bearer {}", self.token))
            .header(
                "Content-Type",
                &format!("multipart/related; boundary={boundary}"),
            )
            .send(body.as_bytes())
            .map_err(|_| "Cannot create Google Drive library")?;
        let result: Value = response
            .body_mut()
            .read_json()
            .map_err(|_| "Invalid Google Drive upload response")?;
        result["id"]
            .as_str()
            .map(str::to_owned)
            .ok_or_else(|| "Drive did not return a file ID".into())
    }

    fn get(&self, url: &str) -> Result<String, String> {
        let mut response = self
            .agent
            .get(url)
            .header("Authorization", &format!("Bearer {}", self.token))
            .call()
            .map_err(|error| format!("Google Drive request failed: {error}"))?;
        let body = response
            .body_mut()
            .read_to_string()
            .map_err(|e| e.to_string())?;
        if body.len() > MAX_BYTES {
            return Err("Google Drive response is too large".into());
        }
        Ok(body)
    }
}

fn drive_file_url(base: &str, id: &str, parameter: &str, value: &str) -> Result<String, String> {
    let mut url = Url::parse(base).map_err(|e| e.to_string())?;
    url.path_segments_mut()
        .map_err(|_| "Invalid Drive address")?
        .pop_if_empty()
        .push(id);
    url.query_pairs_mut().append_pair(parameter, value);
    Ok(url.to_string())
}
