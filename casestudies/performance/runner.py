import datetime
import json
import os
import signal
import subprocess
from typing import NamedTuple
from typing import Optional

import isodate

import config
import tasks
from tasks import Task


class SJDBResult(NamedTuple):
    times: Optional[dict[str, datetime.timedelta]]
    stats: Optional[dict[str, int]]


def runTask(task: Task) -> SJDBResult:
    compileProject(task.project.projectPath)
    tasks.genTaskFile(task)
    print("\n\n=== Running task {} ===\n\n".format(task.name))
    return runSJDB(task.project.projectPath, config.taskfile(project), task.timeout)


def runSJDB(
        projectPath: str,
        taskfile: str,
        timeout: Optional[datetime.timedelta]
) -> SJDBResult:
    cmdline = [
        "java",
        "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
        "-jar", config.sjdbJar,
        taskfile
    ]

    stats_file = os.path.join(projectPath, "stats.json")
    if os.path.exists(stats_file):
        os.remove(stats_file)

    times_file = os.path.join(projectPath, "times.json")
    if os.path.exists(times_file):
        os.remove(times_file)

    print("Executing commandline {} in {}...".format(" ".join(cmdline), projectPath))
    with subprocess.Popen(cmdline, cwd=projectPath, stdout=subprocess.PIPE, shell=False,
                          preexec_fn=os.setsid) as process:
        try:
            timeoutAsSeconds = timeout.seconds if timeout else None
            try:
                output, _ = process.communicate(None, timeout=timeoutAsSeconds)
            except Exception:
                process.kill()
                raise

            output = output.decode('utf-8')

        except subprocess.TimeoutExpired as e:
            output = e.output.decode('utf-8')
            print("Timeout hit! ({}s)".format(e.timeout))
            try:
                os.killpg(os.getpgid(process.pid), signal.SIGKILL)
                process.kill()
            except ProcessLookupError as lookupError:
                print("Could not terminate {}!".format(process.pid))
                print("Error: {}".format(lookupError))
                pass
            process.wait()

        print(output)

        stats_result = None
        if os.path.exists(stats_file):
            with open(stats_file) as f:
                stats_result = json.load(f)

        times_result = None
        if os.path.exists(times_file):
            with open(times_file) as f:
                times_result = json.load(f)
                times_result = {
                    tag: isodate.parse_duration(times_result[tag]) for tag in times_result
                }

    return SJDBResult(
        times=times_result,
        stats=stats_result
    )


def compileProject(projectPath: str):
    compile_script = "{}/compile.sh".format(projectPath)
    if os.path.isfile(compile_script):
        subprocess.run([compile_script])
