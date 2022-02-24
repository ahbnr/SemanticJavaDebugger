import datetime
import re
import subprocess
from typing import NamedTuple

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
    return runSJDB(task.project)


def runSJDB(project: Project) -> SJDBResult:
    compileProject(project)

    with subprocess.Popen(
            [config.sjdb, config.taskfile(project)],
            stdout=subprocess.PIPE,
            universal_newlines=True
    ) as popen:
        def linegen():
            for line in iter(popen.stdout.readline, ""):
                print(line, end='')
                yield line

        stdout = ''.join(linegen())
        time_result = time_regex.search(stdout).group('iso8601')

        return SJDBResult(
            time=isodate.parse_duration(time_result),
        )


def compileProject(project: Project):
    subprocess.run(["{}/compile.sh".format(project.projectPath)])
