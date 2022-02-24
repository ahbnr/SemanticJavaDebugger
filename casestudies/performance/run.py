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
    breakpoint="HelloWorld:Hello World",
    main="HelloWorld"
)

taskGen = TaskGenerator(
    projectSelection={hello_world},
    mappingOptionsSelection={
        MappingOptions(limitSdk=True, closeReferenceTypes=False),
        MappingOptions(limitSdk=True, closeReferenceTypes=True),
        MappingOptions(limitSdk=False, closeReferenceTypes=True),
    },
    taskTypeSelection={
        TaskType.KBBuilding,
        TaskType.Consistency,
        # TaskType.Classification
    }
)


class ResultSchema(pa.SchemaModel):
    taskId: Series[str] = pa.Field()
    time: Series[pandera.dtypes.Timedelta] = pa.Field()


results: pd.DataFrame = pd.DataFrame(np.empty((0, 2)), columns=["taskId", "time"])

for task in taskGen.generate():
    runResult: SJDBResult = runner.runTask(task)
    results = pd.concat([
        results,
        pd.DataFrame(
            np.array([[task.name, pd.Timedelta(runResult.time)]]),
            index=[task.name],
            columns=["taskId", "time"]
        )
    ])

# ResultSchema.validate(results)
store = pd.HDFStore('store.h5')
store['results'] = results

print(results.to_string())
