#!/usr/bin/env bash

set -euo pipefail

channel="${1:-}"
version="${2:-}"

if [[ "$channel" != "official" && "$channel" != "beta" ]]; then
  echo "Unsupported update channel: $channel" >&2
  exit 1
fi
if [[ -z "$version" ]]; then
  echo "Release version is required" >&2
  exit 1
fi
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"

manifest_branch="update-manifests"
manifest_dir="$RUNNER_TEMP/$manifest_branch"
release_tmp="$manifest_dir/release.tmp"
release_list_tmp="$manifest_dir/releases.tmp"

write_manifest() {
  local source_file="$1"
  local target_channel="$2"
  local expected_version="$3"
  local target_file="$manifest_dir/$target_channel.json"
  local target_tmp="$target_file.tmp"
  local expected_prerelease="false"
  if [[ "$target_channel" == "beta" ]]; then
    expected_prerelease="true"
  fi

  jq -e \
    --arg version "$expected_version" \
    --argjson prerelease "$expected_prerelease" \
    'if .tag_name == $version and .prerelease == $prerelease and (.assets | type == "array") then
      {
        tag_name,
        name,
        body: (.body // ""),
        prerelease,
        created_at,
        assets: [.assets[] | {
          browser_download_url,
          content_type,
          created_at,
          download_count: 0,
          id,
          name,
          state,
          url
        }]
      }
    else
      error("Release response does not match the requested channel and version")
    end' \
    "$source_file" > "$target_tmp"
  mv "$target_tmp" "$target_file"
}

rm -rf "$manifest_dir"
if git fetch --no-tags origin "$manifest_branch"; then
  git worktree add --detach "$manifest_dir" FETCH_HEAD
else
  git worktree add --detach "$manifest_dir" HEAD
  git -C "$manifest_dir" checkout --orphan "$manifest_branch"
  git -C "$manifest_dir" rm -q -rf --ignore-unmatch .
fi
trap 'git worktree remove --force "$manifest_dir" >/dev/null 2>&1 || true' EXIT

gh api \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2026-03-10" \
  "repos/$GITHUB_REPOSITORY/releases/tags/$version" > "$release_tmp"
write_manifest "$release_tmp" "$channel" "$version"

other_channel="official"
if [[ "$channel" == "official" ]]; then
  other_channel="beta"
fi

if [[ ! -f "$manifest_dir/$other_channel.json" ]]; then
  if [[ "$other_channel" == "official" ]]; then
    gh api \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2026-03-10" \
      "repos/$GITHUB_REPOSITORY/releases/latest" > "$release_tmp"
    other_version="$(jq -r '.tag_name' "$release_tmp")"
    write_manifest "$release_tmp" "$other_channel" "$other_version"
  else
    gh api \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2026-03-10" \
      "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$release_list_tmp"
    if jq -e \
      '[.[] | select(.draft == false and .prerelease == true)] | max_by(.created_at) | select(. != null)' \
      "$release_list_tmp" > "$release_tmp"; then
      other_version="$(jq -r '.tag_name' "$release_tmp")"
      write_manifest "$release_tmp" "$other_channel" "$other_version"
    fi
  fi
fi
rm -f "$release_tmp" "$release_list_tmp"

git -C "$manifest_dir" config user.name "github-actions[bot]"
git -C "$manifest_dir" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git -C "$manifest_dir" add --all

if git -C "$manifest_dir" diff --cached --quiet; then
  echo "$channel update manifest is already current"
  exit 0
fi

git -C "$manifest_dir" commit -m "Update release manifests for $version"
git -C "$manifest_dir" push origin "HEAD:refs/heads/$manifest_branch"
