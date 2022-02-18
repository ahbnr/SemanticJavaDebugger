from enum import Enum

import config
from project import Project


class Task(Enum):
    BUILDKB = 0


def genTaskFile(project: Project):
    with open(config.taskfile(project), "w") as f:
        lines = [
            "reasoner HermiT",
            "classpath '{}'".format(project.projectPath),
            "sourcepath '{}'".format(project.projectPath),
            "stop at '{}'".format(project.breakpoint),
            "run {}".format(project.main),
            "buildkb --linting=none",
            "infer isConsistent",
            "time"
        ]
        f.writelines('\n'.join(lines))
