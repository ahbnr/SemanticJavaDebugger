from typing import Dict

import runner
from project import Project
from runner import SJDBResult
from sjdboptions import MappingOptions
from tasks import Task

hello_world = Project(
    projectPath="java/minimal",
    breakpoint="HelloWorld:Hello World",
    main="HelloWorld"
)

tasks = [
    Task(
        name="HelloWorld-U",
        description="HelloWorld project with limit-sdk=false",
        project=hello_world,
        mappingOptions=MappingOptions(
            limitSdk=False
        )
    ),
    Task(
        name="HelloWorld-L",
        description="HelloWorld project with limit-sdk=true",
        project=hello_world,
        mappingOptions=MappingOptions(
            limitSdk=True
        )
    ),
]

results: Dict[Task, SJDBResult] = {}

for task in tasks:
    results[task] = runner.runTask(task)

for task in results:
    result = results[task]
    print("{}: {}".format(task.name, result["time"]))
