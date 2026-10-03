fn main() {
    println!("cargo:rerun-if-env-changed=SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET");
    println!("cargo:rerun-if-env-changed=SNIPPETDECK_OFFICIAL_BUILD");
    if std::env::var("SNIPPETDECK_OFFICIAL_BUILD").is_ok_and(|value| value == "1")
        && std::env::var("SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET")
            .map_or(true, |secret| secret.trim().is_empty())
    {
        panic!("Official desktop builds require SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET");
    }
    tauri_build::build()
}
