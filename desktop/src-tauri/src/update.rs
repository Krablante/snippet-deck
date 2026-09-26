use serde::Serialize;
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{
    fs::{self, File},
    io::{Read, Write},
    path::{Path, PathBuf},
    process::Command,
    time::Duration,
};

const API: &str = "https://api.github.com/repos/Krablante/snippet-deck/releases/latest";
const MAX_INSTALLER_BYTES: u64 = 200 * 1024 * 1024;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Release {
    pub version: String,
    pub size: u64,
    name: String,
    url: String,
    digest: String,
}

fn version(value: &str) -> Option<[u64; 3]> {
    let mut parts = value.strip_prefix('v').unwrap_or(value).split('.');
    let mut next = || {
        let part = parts.next()?;
        if part.is_empty() || !part.bytes().all(|byte| byte.is_ascii_digit()) {
            return None;
        }
        part.parse().ok()
    };
    let version = [next()?, next()?, next()?];
    parts.next().is_none().then_some(version)
}

fn installer_name(version: &str) -> Result<String, String> {
    #[cfg(target_os = "windows")]
    {
        if std::env::consts::ARCH == "x86_64" {
            return Ok(format!("SnippetDeck_{version}_x64_en-US.msi"));
        }
    }
    #[cfg(target_os = "macos")]
    {
        return match std::env::consts::ARCH {
            "aarch64" => Ok(format!("SnippetDeck_{version}_arm64.dmg")),
            "x86_64" => Ok(format!("SnippetDeck_{version}_intel.dmg")),
            _ => Err("No SnippetDeck installer for this Mac".into()),
        };
    }
    #[cfg(target_os = "linux")]
    {
        if std::env::consts::ARCH == "x86_64" {
            return Ok(format!("SnippetDeck_{version}_amd64.deb"));
        }
    }
    let _ = version;
    Err("No SnippetDeck installer for this system".into())
}

fn agent(timeout: u64) -> ureq::Agent {
    ureq::Agent::config_builder()
        .https_only(true)
        .max_redirects(5)
        .timeout_global(Some(Duration::from_secs(timeout)))
        .build()
        .into()
}

pub fn check(current: &str) -> Result<Option<Release>, String> {
    let mut response = agent(20)
        .get(API)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "SnippetDeck-Desktop")
        .call()
        .map_err(|error| format!("Cannot check GitHub releases: {error}"))?;
    let body = response
        .body_mut()
        .with_config()
        .limit(1_000_000)
        .read_to_string()
        .map_err(|_| "Invalid GitHub release response")?;
    let release: Value = serde_json::from_str(&body).map_err(|_| "Invalid GitHub release")?;
    parse_release(&release, current)
}

fn parse_release(release: &Value, current: &str) -> Result<Option<Release>, String> {
    let current = version(current).ok_or("Invalid installed version")?;
    if release["draft"] != false || release["prerelease"] != false {
        return Err("GitHub did not return a stable release".into());
    }
    let tag = release["tag_name"].as_str().ok_or("Missing release version")?;
    let display_version = tag.strip_prefix('v').ok_or("Invalid release tag")?;
    let latest = version(tag).ok_or("Invalid release version")?;
    if latest <= current {
        return Ok(None);
    }
    let name = installer_name(display_version)?;
    let asset = release["assets"]
        .as_array()
        .and_then(|assets| assets.iter().find(|asset| asset["name"] == name))
        .ok_or("No installer for this platform in the latest release")?;
    let size = asset["size"].as_u64().ok_or("Invalid installer size")?;
    if !(1..=MAX_INSTALLER_BYTES).contains(&size) {
        return Err("The release installer is too large or empty".into());
    }
    let digest = asset["digest"]
        .as_str()
        .and_then(|value| value.strip_prefix("sha256:"))
        .filter(|value| value.len() == 64 && value.bytes().all(|byte| byte.is_ascii_hexdigit()))
        .ok_or("The release has no valid SHA-256 digest")?;
    let url = format!(
        "https://github.com/Krablante/snippet-deck/releases/download/{tag}/{name}"
    );
    if asset["browser_download_url"] != url {
        return Err("The installer URL does not match the release".into());
    }
    Ok(Some(Release {
        version: display_version.to_owned(),
        size,
        name,
        url,
        digest: digest.to_ascii_lowercase(),
    }))
}

fn file_matches(path: &Path, release: &Release) -> Result<bool, String> {
    let mut file = File::open(path).map_err(|e| e.to_string())?;
    if file.metadata().map_err(|e| e.to_string())?.len() != release.size {
        return Ok(false);
    }
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let count = file.read(&mut buffer).map_err(|e| e.to_string())?;
        if count == 0 { break; }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hasher.finalize()) == release.digest)
}

pub fn download(
    release: &Release,
    directory: &Path,
    mut progress: impl FnMut(u64),
) -> Result<PathBuf, String> {
    fs::create_dir_all(directory).map_err(|e| format!("Cannot create update cache: {e}"))?;
    let target = directory.join(&release.name);
    if target.exists() && file_matches(&target, release)? {
        progress(release.size);
        return Ok(target);
    }
    let partial = directory.join(format!("{}.part", release.name));
    let result = (|| -> Result<(), String> {
        let mut response = agent(180)
            .get(&release.url)
            .header("User-Agent", "SnippetDeck-Desktop")
            .call()
            .map_err(|e| format!("Cannot download the installer: {e}"))?;
        if response.headers().get("Content-Length")
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.parse::<u64>().ok())
            .is_some_and(|size| size != release.size)
        {
            return Err("Installer size differs from the GitHub release".into());
        }
        let mut output = File::create(&partial).map_err(|e| e.to_string())?;
        let mut hasher = Sha256::new();
        let mut received = 0u64;
        let mut buffer = [0u8; 64 * 1024];
        let mut reader = response.body_mut().as_reader();
        loop {
            let count = reader.read(&mut buffer)
                .map_err(|e| format!("Installer download interrupted: {e}"))?;
            if count == 0 { break; }
            received += count as u64;
            if received > release.size {
                return Err("Installer is larger than the GitHub release".into());
            }
            output.write_all(&buffer[..count]).map_err(|e| e.to_string())?;
            hasher.update(&buffer[..count]);
            progress(received);
        }
        output.sync_all().map_err(|e| e.to_string())?;
        if received != release.size || format!("{:x}", hasher.finalize()) != release.digest {
            return Err("Installer size or SHA-256 does not match GitHub".into());
        }
        drop(output);
        if target.exists() {
            fs::remove_file(&target).map_err(|e| e.to_string())?;
        }
        fs::rename(&partial, &target).map_err(|e| e.to_string())?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(partial);
    }
    result.map(|()| target)
}

pub fn open_installer(path: &Path) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    let command = "msiexec.exe";
    #[cfg(target_os = "windows")]
    let args = ["/i".as_ref(), path.as_os_str()];
    #[cfg(target_os = "macos")]
    let command = "open";
    #[cfg(target_os = "macos")]
    let args = [path.as_os_str()];
    #[cfg(target_os = "linux")]
    let command = "xdg-open";
    #[cfg(target_os = "linux")]
    let args = [path.as_os_str()];
    Command::new(command)
        .args(args)
        .spawn()
        .map_err(|e| format!("Cannot open installer at {}: {e}", path.display()))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn release_requires_the_expected_installer_and_digest() {
        let name = installer_name("1.8.0").unwrap();
        let url = format!("https://github.com/Krablante/snippet-deck/releases/download/v1.8.0/{name}");
        let mut release = json!({
            "draft": false, "prerelease": false, "tag_name": "v1.8.0",
            "assets": [{"name": name, "size": 1234, "digest": format!("sha256:{}", "a".repeat(64)), "browser_download_url": url}]
        });
        assert!(parse_release(&release, "1.8.0").unwrap().is_none());
        assert_eq!(parse_release(&release, "1.7.1").unwrap().unwrap().version, "1.8.0");
        release["assets"][0]["browser_download_url"] = json!("https://example.com/installer.deb");
        assert!(parse_release(&release, "1.7.1").is_err());
        release["assets"][0]["browser_download_url"] = json!(url);
        release["assets"][0]["digest"] = json!("sha256:bad");
        assert!(parse_release(&release, "1.7.1").is_err());
    }
}
