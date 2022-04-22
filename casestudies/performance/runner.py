import datetime
import json
import os
import signal
import subprocess
from typing import Any
from typing import Dict
from typing import List
from typing import NamedTuple
from typing import Optional

import isodate

import config
import tasks
from tasks import Task


class SJDBResult(NamedTuple):
    times: Optional[Dict[str, datetime.timedelta]]
    memory: Optional[Dict[str, int]]
    stats: Optional[Dict[str, int]]


def runTask(task: Task) -> SJDBResult:
    compileProject(task.project.projectPath)
    tasks.genTaskFile(task)
    print("\n\n=== Running task {} ===\n\n".format(task.name))
    return runSJDB(task.project.projectPath, config.taskfile(project), task.timeout)


def averageDicts(
        dicts: List[Optional[Dict[Any, Any]]],
        defaultVal: Any
) -> Optional[Dict[Any, Any]]:
    if any(d is None for d in dicts):
        return None

    averaged_d: Optional[Dict[Any, Any]] = None
    for d in dicts:
        if averaged_d is None:
            averaged_d = {k: defaultVal for k in d}

        intersected_keys = set(averaged_d.keys()) & set(d.keys())
        averaged_d = {k: averaged_d[k] + d[k] for k in intersected_keys}

    return {k: averaged_d[k] / len(dicts) for k in averaged_d}


def runSJDB(
        projectPath: str,
        taskfile: str,
        timeout: Optional[datetime.timedelta],
        repeat: int  # run it this many times and average the results
) -> SJDBResult:
    cmdline = [
        "java",
        "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
        "-jar", config.sjdbJar,
        taskfile
    ]

    if repeat <= 0:
        raise "Must execute at least once."

    repeatResults: list[SJDBResult] = []
    for repeatIdx in range(0, repeat):
        stats_file = os.path.join(projectPath, "stats.json")
        if os.path.exists(stats_file):
            os.remove(stats_file)

        times_file = os.path.join(projectPath, "times.json")
        if os.path.exists(times_file):
            os.remove(times_file)

        memory_file = os.path.join(projectPath, "memory.json")
        if os.path.exists(memory_file):
            os.remove(memory_file)

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

            memory_result = None
            if os.path.exists(memory_file):
                with open(memory_file) as f:
                    memory_result = json.load(f)

        repeatResults.append(
            SJDBResult(
                times=times_result,
                memory=memory_result,
                stats=stats_result
            )
        )

    averaged_times: Optional[dict[str, datetime.timedelta]] = averageDicts([result.times for result in repeatResults],
                                                                           datetime.timedelta())

    averaged_memory: Optional[dict[str, int]] = averageDicts([result.memory for result in repeatResults], 0)

    averaged_stats: Optional[dict[str, int]] = averageDicts([result.stats for result in repeatResults], 0)

    return SJDBResult(
        times=averaged_times,
        memory=averaged_memory,
        stats=averaged_stats
    )


def compileProject(projectPath: str):
    compile_script = "{}/compile.sh".format(projectPath)
    if os.path.isfile(compile_script):
        subprocess.run([compile_script])
