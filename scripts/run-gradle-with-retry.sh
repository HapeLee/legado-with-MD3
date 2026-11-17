#!/usr/bin/env bash
# 运行 Gradle 命令，仅当失败原因是依赖下载（仓库限流 / 网络抖动）时重试一次。
#
# 不做无条件重试：Verify 的 Gradle 步骤含全量单测与 lint，一次约 45 分钟，
# 真实编译或测试失败重跑一遍只是把 CI 时间翻倍。
# 已知触发场景：GitHub runner 出口 IP 命中 Maven Central 限流时，Gradle 在配置阶段
# 解析插件 classpath 直接失败（证书、产物都正常，但日志里是
# `Received status code 429 from server: Too Many Requests`）。
#
# 用法：bash scripts/run-gradle-with-retry.sh ./gradlew <args...>
# 环境变量：GRADLE_RETRY_MAX_ATTEMPTS（默认 2）、GRADLE_RETRY_DELAY_SECONDS（默认 20）
set -o pipefail

max_attempts="${GRADLE_RETRY_MAX_ATTEMPTS:-2}"
retry_delay_seconds="${GRADLE_RETRY_DELAY_SECONDS:-20}"
log_file="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/gradle-retry.log"
transient_pattern='Received status code 429|Too Many Requests|Could not resolve all dependencies|Could not (GET|HEAD) |Read timed out|Connect timed out|Connection reset|Received status code 5[0-9][0-9]'

attempt=1
while :; do
  echo "::group::Gradle attempt ${attempt}/${max_attempts}: $*"
  "$@" 2>&1 | tee "$log_file"
  status="${PIPESTATUS[0]}"
  echo "::endgroup::"

  if [ "$status" -eq 0 ]; then
    exit 0
  fi

  if [ "$attempt" -ge "$max_attempts" ] || ! grep -Eqi "$transient_pattern" "$log_file"; then
    exit "$status"
  fi

  attempt=$((attempt + 1))
  echo "::warning::Gradle 因依赖下载失败（仓库限流或网络抖动）退出码 ${status}，${retry_delay_seconds}s 后重试（第 ${attempt}/${max_attempts} 次）"
  sleep "$retry_delay_seconds"
done
