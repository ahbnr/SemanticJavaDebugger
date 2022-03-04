import datetime
from enum import Enum
from typing import NamedTuple, Set, Iterator, List, Optional

import config
from project import Project
from sjdboptions import MappingOptions


class TaskTypeProperties(NamedTuple):
    id: str
    name: str


class TaskType(Enum):
    KBBuilding = TaskTypeProperties(id="K", name="Knowledge Base Building")
    Consistency = TaskTypeProperties(id="C", name="Consistency")
    Classification = TaskTypeProperties(id="S", name="Classification")
    Realisation = TaskTypeProperties(id="R", name="Realisation")


class Task(NamedTuple):
    name: str
    project: Project
    mappingOptions: MappingOptions
    taskType: TaskType
    timeout: Optional[datetime.timedelta]


class TaskGenerator(NamedTuple):
    projectSelection: List[Project]
    mappingOptionsSelection: Set[MappingOptions]
    taskTypeSelection: Set[TaskType]
    timeout: Optional[datetime.timedelta]

    def generate(self) -> Iterator[Task]:
        for project in self.projectSelection:
            for mappingOptions in self.mappingOptionsSelection:
                for taskType in self.taskTypeSelection:
                    yield Task(
                        name="{}-{}{}".format(
                            project.name,
                            mappingOptions.id(),
                            taskType.value.id
                        ),
                        project=project,
                        mappingOptions=mappingOptions,
                        taskType=taskType,
                        timeout=self.timeout
                    )


def genTaskFile(task: Task):
    with open(config.taskfile(task.project), "w") as f:
        f.writelines('\n'.join([
            "reasoner HermiT",
            "classpaths '{}'".format(' '.join(task.project.classpaths)),
        ]) + "\n")

        if task.project.sourcePath:
            f.writelines('\n'.join([
                "sourcepath '{}'".format(task.project.projectPath),
            ]) + "\n")

        f.writelines('\n'.join([
            "mapping set limit-sdk {}".format(task.mappingOptions.limitSdk),
            "stop at '{}'".format(task.project.breakpoint),
            "run -- {}".format(task.project.main),
            "buildkb --linting=none",
        ]) + "\n")

        task_lines: List[str] = {
            TaskType.KBBuilding: [],
            TaskType.Consistency: ["infer isConsistent"],
            TaskType.Classification: ["infer classification"],
            TaskType.Realisation: ["infer realisation"]
        }[task.taskType]
        f.writelines('\n'.join(task_lines) + "\n")

        f.writelines('\n'.join([
            "time"
        ]) + "\n")
