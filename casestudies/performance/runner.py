import datetime
import json
import os
import signal
import subprocess
from typing import Optional

import isodate

import config


def timesPath(projectPath: str) -> str:
    return os.path.join(projectPath, "times.json")


def memoryPath(projectPath: str) -> str:
    return os.path.join(projectPath, "memory.json")


def retrieveAverageTime(projectPath: str) -> datetime.timedelta:
    times_file = timesPath(projectPath)

    results = None
    if os.path.exists(times_file):
        with open(times_file) as f:
            times_result = json.load(f)
            results = [
                isodate.parse_duration(times_result[repeatIdx]) for repeatIdx in times_result
            ]

    if results is None:
        return datetime.timedelta()

    return sum(results, datetime.timedelta()) / len(results)


def retrieveMemory(projectPath: str) -> int:
    memory_file = memoryPath(projectPath)

    result = None
    if os.path.exists(memory_file):
        with open(memory_file) as f:
            content = json.load(f)
            result = content['peak'] if 'peak' in content else None

    return result if result is not None else 0


def runSJDB(
        projectPath: str,
        taskfile: str,
        timeout: Optional[datetime.timedelta],
        monitorMemory: bool,
        printer
):
    cmdline = [
        "java",
        "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
        "-jar", config.sjdbJar,
    ]

    if monitorMemory:
        cmdline += [
            "--monitor-memory",
        ]

    cmdline += [
        taskfile
    ]

    time_file = timesPath(projectPath)
    if os.path.exists(time_file):
        os.remove(time_file)

    memory_file = memoryPath(projectPath)
    if os.path.exists(memory_file):
        os.remove(memory_file)

    printer("Executing commandline {} in {}...".format(" ".join(cmdline), projectPath))
    with subprocess.Popen(cmdline, cwd=projectPath, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=False,
                          preexec_fn=os.setsid) as process:
        try:
            timeoutAsSeconds = timeout.seconds if timeout else None
            try:
                output, err = process.communicate(None, timeout=timeoutAsSeconds)
            except Exception:
                process.kill()
                raise

            output = output.decode('utf-8')
            err = err.decode('utf-8')

        except subprocess.TimeoutExpired as e:
            output = e.output.decode('utf-8')
            err = e.stderr.decode('utf-8')
            printer("Timeout hit! ({}s)".format(e.timeout))
            try:
                os.killpg(os.getpgid(process.pid), signal.SIGKILL)
                process.kill()
            except ProcessLookupError as lookupError:
                printer("Could not terminate {}!".format(process.pid))
                printer("Error: {}".format(lookupError))
                pass
            process.wait()

        printer(output)
        printer(err)


def compileProject(projectPath: str):
    compile_script = "{}/compile.sh".format(projectPath)
    if os.path.isfile(compile_script):
        subprocess.run([compile_script])
