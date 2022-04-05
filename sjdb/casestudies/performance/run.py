import datetime

import numpy as np
import pandas as pd
import pandera as pa
import pandera.dtypes
from pandera.typing import Series

import runner
from project import Project
from runner import SJDBResult
from sjdboptions import MappingOptions
from tasks import TaskGenerator, TaskType

hello_world = Project(
    name="HelloWorld",
    projectPath="java/minimal",
    sourcePath="./",
    classpaths=["./"],
    breakpoint="HelloWorld:Hello World",
    main="HelloWorld"
)

dacapo_lusearch = Project(
    name="lusearch",
    projectPath="java/dacapo/lusearch",
    sourcePath=None,
    classpaths=["dacapo-evaluation-git-f480064.jar"],
    breakpoint="org.apache.lucene.search.IndexSearcher:443",
    main="Harness --no-validation -t 1 lusearch"
)

taskGen = TaskGenerator(
    projectSelection=[
        hello_world,
        dacapo_lusearch
    ],
    mappingOptionsSelection={
        MappingOptions(limitSdk=True, closeReferenceTypes=False),
        MappingOptions(limitSdk=True, closeReferenceTypes=True),
        MappingOptions(limitSdk=False, closeReferenceTypes=True),
    },
    taskTypeSelection={
        TaskType.KBBuilding,
        TaskType.Consistency,
        TaskType.Classification,
        TaskType.Realisation
    },
    timeout=datetime.timedelta(seconds=60)
)


class ResultSchema(pa.SchemaModel):
    taskId: Series[str] = pa.Field()
    taskType: Series[str] = pa.Field()
    limitSdk: Series[bool] = pa.Field()
    closeReferenceTypes: Series[bool] = pa.Field()
    projectName: Series[str] = pa.Field()
    time: Series[pandera.dtypes.Timedelta] = pa.Field()


resultColumns = [
    "taskId",
    "taskType",
    "limitSdk",
    "closeReferenceTypes",
    "projectName",
    "time"
]
results: pd.DataFrame = pd.DataFrame(np.empty((0, 6)), columns=resultColumns)

for task in taskGen.generate():
    runResult: SJDBResult = runner.runTask(task)
    results = pd.concat([
        results,
        pd.DataFrame(
            np.array([
                [
                    task.name,
                    task.taskType.value.name,
                    task.mappingOptions.limitSdk,
                    task.mappingOptions.closeReferenceTypes,
                    task.project.name,
                    pd.Timedelta(runResult.time)
                ]
            ]),
            index=[task.name],
            columns=resultColumns
        )
    ])

# ResultSchema.validate(results)
with pd.HDFStore('store.h5') as store:
    store['results'] = results

print(results.to_string())
