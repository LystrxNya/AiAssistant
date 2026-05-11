#!/usr/bin/env bash
# Claude Code status line script
# Displays: model name, working directory, context usage

input=$(cat)

# Extract values from JSON
model=$(echo "$input" | jq -r '.model.display_name // "unknown"')
cwd=$(echo "$input" | jq -r '.workspace.current_dir // .cwd // empty')
used=$(echo "$input" | jq -r '.context_window.used_percentage // empty')

# Shorten cwd to just the last 2 path components
short_cwd=$(echo "$cwd" | sed 's|/[^/]*$||' | xargs basename 2>/dev/null)
if [ -z "$short_cwd" ]; then
  short_cwd=$(basename "$cwd" 2>/dev/null || echo "$cwd")
fi

# Build output
parts=()

[ -n "$model" ] && parts+=("$model")
[ -n "$short_cwd" ] && parts+=("$short_cwd")
if [ -n "$used" ]; then
  # Round to integer
  used_int=$(printf '%.0f' "$used")
  parts+=("ctx:${used_int}%")
fi

IFS=' | '
echo "${parts[*]}"
