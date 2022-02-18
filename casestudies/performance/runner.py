import datetime
import re
import subprocess
from typing import TypedDict

import isodate

import config
from project import Project

time_regex = re.compile("> time\n.*\\((?P<iso8601>PT.+\\.\\d+S)\\)")


class SJDBResult(TypedDict):
    time: datetime.timedelta


def runSJDB(project: Project) -> SJDBResult:
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

        return {
            "time": isodate.parse_duration(time_result),
        }


def compileProject(project: Project):
    subprocess.run(["{}/compile.sh".format(project.projectPath)])
