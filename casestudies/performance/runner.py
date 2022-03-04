import datetime
import os
import re
import signal
import subprocess
from typing import NamedTuple
from typing import Optional

import isodate

import config
import tasks
from project import Project
from tasks import Task

time_regex = re.compile("> time\n.*\\((?P<iso8601>PT.+\\.\\d+S)\\)")


class SJDBResult(NamedTuple):
    time: datetime.timedelta


def runTask(task: Task) -> SJDBResult:
    compileProject(task.project)
    tasks.genTaskFile(task)
    print("\n\n=== Running task {} ===\n\n".format(task.name))
    return runSJDB(task.project, task.timeout)


def runSJDB(project: Project, timeout: Optional[datetime.timedelta]) -> SJDBResult:
    compileProject(project)

    time_result: datetime.timedelta
    cmdline = [config.sjdb, config.taskfile(project)]

    with subprocess.Popen(cmdline, cwd=project.projectPath, stdout=subprocess.PIPE, shell=False,
                          preexec_fn=os.setsid) as process:
        try:
            timeoutAsSeconds = timeout.seconds if timeout else None
            output, _ = process.communicate(None, timeout=timeoutAsSeconds)

            time_result = isodate.parse_duration(time_regex.search(output.decode('utf-8')).group('iso8601'))
        except subprocess.TimeoutExpired as e:
            print("Timeout hit! ({}s)".format(e.timeout))
            try:
                os.killpg(os.getpgid(process.pid), signal.SIGKILL)
                process.kill()
            except ProcessLookupError as lookupError:
                print("Could not terminate {}!".format(process.pid))
                print("Error: {}".format(lookupError))
                pass
            process.wait()

            time_result = datetime.timedelta(seconds=e.timeout)

    print("Completed in: {}".format(time_result))

    return SJDBResult(
        time=time_result,
    )


def compileProject(project: Project):
    compile_script = "{}/compile.sh".format(project.projectPath)
    if os.path.isfile(compile_script):
        subprocess.run([compile_script])
