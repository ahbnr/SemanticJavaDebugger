from typing import NamedTuple

import config
from project import Project
from sjdboptions import MappingOptions


class Task(NamedTuple):
    name: str
    description: str
    project: Project
    mappingOptions: MappingOptions


def genTaskFile(task: Task):
    with open(config.taskfile(task.project), "w") as f:
        f.writelines('\n'.join([
            "reasoner HermiT",
            "classpath '{}'".format(task.project.projectPath),
            "sourcepath '{}'".format(task.project.projectPath),
            "mapping set limit-sdk {}".format(task.mappingOptions.limitSdk),
            "stop at '{}'".format(task.project.breakpoint),
            "run {}".format(task.project.main),
            "buildkb --linting=none",
            "infer isConsistent",
            "time"
        ]))
