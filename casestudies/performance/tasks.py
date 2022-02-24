from enum import Enum
from typing import NamedTuple, Set, Iterator, List

import config
from project import Project
from sjdboptions import MappingOptions


class TaskTypeProperties(NamedTuple):
    id: str


class TaskType(Enum):
    KBBuilding = TaskTypeProperties(id="K")
    Consistency = TaskTypeProperties(id="C")
    Classification = TaskTypeProperties(id="S")


class Task(NamedTuple):
    name: str
    project: Project
    mappingOptions: MappingOptions
    taskType: TaskType


class TaskGenerator(NamedTuple):
    projectSelection: Set[Project]
    mappingOptionsSelection: Set[MappingOptions]
    taskTypeSelection: Set[TaskType]

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
                        taskType=taskType
                    )


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
        ]) + "\n")

        task_lines: List[str] = {
            TaskType.KBBuilding: [],
            TaskType.Consistency: ["infer isConsistent"],
            TaskType.Classification: ["infer classification"]
        }[task.taskType]
        f.writelines('\n'.join(task_lines) + "\n")

        f.writelines('\n'.join([
            "time"
        ]) + "\n")
