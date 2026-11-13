#!/usr/bin/env python3
"""生成当前项目专用的 Maven 依赖并校验所有 UI fork 来源；需要 Python 3.11+ 和 JDK 21。"""

import argparse
import os
from pathlib import Path
import re
import subprocess
import tomllib


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT / "build/ui-forks/maven"
FORKS = (
    ("coui", "io.github.itoseo", ("coui", "coui-material3", "coui-icons")),
    ("mochi", "io.github.feice11", ("mochi-compose", "mochi-ios")),
)
# Miuix 走 settings.gradle 的 includeBuild（composite build 源码替换），不发布 Maven 产物。
# 但它同样由本地源码编译：若不校验，开发机改了 vendored 源码，CI 会用干净子模块构建而
# 不报错，线上与本地就此分叉。因此只做来源校验，不做 publishToMavenLocal。
CHECK_ONLY_FORKS = ("miuix",)


def android_environment() -> dict[str, str]:
    """子模块继承根项目的 SDK 路径，不要求 Android Studio 用户另设环境变量。"""
    environment = os.environ.copy()
    sdk = None
    properties = ROOT / "local.properties"
    if properties.is_file():
        match = re.search(
            r"(?m)^[ \t]*sdk\.dir[ \t]*[=:][ \t]*(.*?)[ \t]*$",
            properties.read_text(encoding="utf-8-sig"),
        )
        if match:
            # local.properties 使用 Java Properties 转义，Windows 常见 D\:\\Android\\SDK。
            sdk = re.sub(
                r"\\(?:u([0-9a-fA-F]{4})|(.))",
                lambda escape: chr(int(escape[1], 16)) if escape[1] else
                {"t": "\t", "r": "\r", "n": "\n", "f": "\f"}.get(escape[2], escape[2]),
                match[1],
            )
    sdk = sdk or environment.get("ANDROID_HOME") or environment.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise RuntimeError("请在根项目 local.properties 设置 sdk.dir，或设置 ANDROID_HOME 指向 Android SDK")
    directory = (ROOT / sdk).resolve()
    if not (directory / "platforms").is_dir():
        raise RuntimeError(f"Android SDK 路径无效或缺少 platforms: {directory}")
    environment["ANDROID_HOME"] = str(directory)
    environment["ANDROID_SDK_ROOT"] = str(directory)
    print(f"Android SDK: {directory}", flush=True)
    return environment


def validate_fork(name: str, versions: dict[str, str], *, strict_suffix: bool) -> str:
    """校验子模块 HEAD == gitlink、版本串含 commit、工作树干净，返回当前 commit。"""
    directory = ROOT / "modules" / name
    commit = subprocess.check_output(
        ["git", "-C", str(directory), "rev-parse", "HEAD"], text=True
    ).strip()
    entry = subprocess.check_output(
        ["git", "-C", str(ROOT), "ls-files", "--stage", f"modules/{name}"], text=True
    ).split()
    if len(entry) != 4 or entry[0] != "160000" or entry[1] != commit:
        raise RuntimeError(f"modules/{name} 与记录的子模块提交不一致，请确认后更新子模块指针")
    version = versions[name]
    if strict_suffix and not version.endswith(f"-legado.{commit[:12]}"):
        raise RuntimeError(f"{name} 版本 {version} 与提交 {commit[:12]} 不一致，请更新 libs.versions.toml")
    if commit[:12] not in version:
        raise RuntimeError(f"{name} 版本 {version} 缺少提交 {commit[:12]}，请更新 libs.versions.toml")
    # porcelain 连未跟踪文件一起看：composite build 直接编译子模块源码目录，
    # 一个未跟踪的 .kt 也足以让本地与 CI 的构建结果不同。
    dirty = subprocess.check_output(
        ["git", "-C", str(directory), "status", "--porcelain"], text=True
    ).strip()
    if dirty:
        raise RuntimeError(f"modules/{name} 工作树不干净，请先提交或撤销本地改动：\n{dirty}")
    return commit


def prepare(check_only: bool) -> None:
    with (ROOT / "gradle/libs.versions.toml").open("rb") as source:
        versions = tomllib.load(source)["versions"]

    # 先验证所有来源，再开始构建；不修改开发者的子模块或已有 Maven Local。
    commands = []
    for name, group, artifacts in FORKS:
        directory = ROOT / "modules" / name
        wrapper = directory / ("gradlew.bat" if os.name == "nt" else "gradlew")
        if not wrapper.is_file():
            raise RuntimeError("请先运行 git submodule update --init modules/miuix modules/coui modules/mochi")
        commit = validate_fork(name, versions, strict_suffix=True)
        version = versions[name]
        command = ([str(wrapper)] if os.name == "nt" else ["bash", str(wrapper)]) + [
            *(f":{artifact}:publishToMavenLocal" for artifact in artifacts),
            f"-PVERSION_NAME={version}",
            f"-Dmaven.repo.local={REPOSITORY}",
            "-Pkotlin.compiler.execution.strategy=in-process",
            "--max-workers=2",
            "--no-daemon",
            "--no-configuration-cache",
            "--stacktrace",
        ]
        commands.append((directory, command, group, artifacts, version))
        print(f"{name}: {commit} -> {group}:{{{', '.join(artifacts)}}}:{version}", flush=True)

    for name in CHECK_ONLY_FORKS:
        directory = ROOT / "modules" / name
        if not (directory / "settings.gradle.kts").is_file():
            raise RuntimeError("请先运行 git submodule update --init modules/miuix modules/coui modules/mochi")
        commit = validate_fork(name, versions, strict_suffix=False)
        print(f"{name}: {commit} -> includeBuild 源码（只校验，不发布 Maven 产物）", flush=True)

    print(f"Maven repository: {REPOSITORY}", flush=True)
    if check_only:
        return
    environment = android_environment()
    for directory, command, group, artifacts, version in commands:
        subprocess.run(command, cwd=directory, env=environment, check=True)
        # 同时检查元数据，防止只复制 AAR 后遗漏传递依赖。
        for artifact in artifacts:
            base = REPOSITORY / group.replace(".", "/") / artifact / version
            for extension in ("aar", "pom", "module"):
                output = base / f"{artifact}-{version}.{extension}"
                if not output.is_file() or output.stat().st_size == 0:
                    raise RuntimeError(f"缺少有效 fork 产物: {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="仅校验子模块和版本，不执行 Gradle")
    arguments = parser.parse_args()
    prepare(arguments.check)
