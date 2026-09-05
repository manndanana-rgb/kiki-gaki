#!/usr/bin/env bash
# GitHub Actions の最新成功ランから debug APK をダウンロードし、adb で実機へインストール
set -euo pipefail

cd "$(dirname "$0")/.."

echo "=== 最新の成功ランの成果物をダウンロード ==="
gh run list --workflow=build.yml --status=success --limit=1 --json databaseId --jq '.[0].databaseId' > /tmp/latest_run_id
RUN_ID=$(cat /tmp/latest_run_id)
echo "Run ID: $RUN_ID"

rm -rf /tmp/kiki-gaki-apk
gh run download "$RUN_ID" --name kiki-gaki-debug --dir /tmp/kiki-gaki-apk

APK=$(find /tmp/kiki-gaki-apk -name "*.apk" | head -1)
echo "APK: $APK"

echo "=== 実機へインストール ==="
"$HOME/workspace/platform-tools/adb" install -r "$APK" || \
  /home/nana/workspace/platform-tools/adb install -r "$APK"

echo "=== 起動 ==="
"$HOME/workspace/platform-tools/adb" shell am start -n app.kikigaki/.MainActivity || \
  /home/nana/workspace/platform-tools/adb shell am start -n app.kikigaki/.MainActivity

echo "完了"
