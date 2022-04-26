import datetime
from typing import Dict, Set, NamedTuple

import numpy as np
import pandas as pd
from tqdm import tqdm

from render_template import render_template
from runner import compileProject, runSJDB, retrieveAverageTime, retrieveMemory

starttime = datetime.datetime.now()

low_range_evaluation = True
high_range_evaluation = True

write_results = True

resultColumns = [
    "times",
    "memory"
]

warmup = 5
repeat = 4
timeout = 60

tasks = ["buildkb", "sparql", "shacl", "infer"]


class ExperimentResult(NamedTuple):
    time: datetime.timedelta
    memory: int


def run_single_experiment(
        java_env: dict[str, any],
        sjdb_script_env: dict[str, any]
) -> ExperimentResult:
    sjdb_script_env["warmup"] = warmup
    sjdb_script_env["repeat"] = repeat

    projectPath = "java/doublylinked"

    render_template(
        filePath=f"{projectPath}/DoublyLinked.template.java",
        targetPath=f"{projectPath}/DoublyLinked.java",
        env=java_env
    )

    compileProject(projectPath)

    render_template(
        filePath=f"{projectPath}/DoublyLinked-time.template.sjdb",
        targetPath=f"{projectPath}/DoublyLinked.sjdb",
        env=sjdb_script_env
    )

    runSJDB(
        projectPath=projectPath,
        taskfile="DoublyLinked.sjdb",
        timeout=None,
        monitorMemory=False,
        printer=tqdm.write
    )
    time = retrieveAverageTime(projectPath)

    # memory
    render_template(
        filePath=f"{projectPath}/DoublyLinked-memory.template.sjdb",
        targetPath=f"{projectPath}/DoublyLinked.sjdb",
        env=sjdb_script_env
    )

    runSJDB(
        projectPath=projectPath,
        taskfile="DoublyLinked.sjdb",
        timeout=None,
        monitorMemory=True,
        printer=tqdm.write
    )
    memory = retrieveMemory(projectPath)

    return ExperimentResult(
        time=time,
        memory=memory
    )


def experiment_for_each_task(
        index,
        java_env: Dict[str, any],
        sjdb_script_env: Dict[str, any],
        excluded_tasks: Set[str]
) -> pd.DataFrame:
    times: Dict[str, datetime.timedelta] = dict()
    memory: Dict[str, int] = dict()
    for task in tasks:
        if task in excluded_tasks:
            continue

        sjdb_script_env['task'] = task

        result = run_single_experiment(java_env, sjdb_script_env)

        times |= {task: result.time}
        memory |= {task: result.memory}

    return pd.DataFrame(
        np.array([
            [
                times,
                memory
            ]
        ]),
        index=index,
        columns=resultColumns
    )


if low_range_evaluation:
    num_nodes_options = [30, 40, 50, 100, 200, 500]
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_nodes_options), total=len(num_nodes_options)) as t:
        t.set_description("LOW RANGE")
        for i, num_nodes in t:
            tqdm.write("Evaluating for {} nodes...".format(num_nodes))

            java_env = {
                "num_nodes": num_nodes,
            }

            sjdb_script_env = {
                "timeout": timeout,
            }

            tqdm.write(f"LOW RANGE: {i + 1}/{len(num_nodes_options)}")

            results = pd.concat([results, experiment_for_each_task([num_nodes], java_env, sjdb_script_env, set())])

    if write_results:
        with pd.HDFStore('DoublyLinkedStore.h5') as store:
            store['results'] = results

    tqdm.write(results.to_string())

if high_range_evaluation:
    start = 500
    step_size = 1000
    max_steps = 10
    num_nodes_options = [start + step * step_size for step in range(0, max_steps)]
    tqdm.write("We will be evaluating for the following node counts:")
    tqdm.write(num_nodes_options)

    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_nodes_options), total=len(num_nodes_options)) as t:
        t.set_description("HIGH RANGE")

        for i, num_nodes in t:
            tqdm.write("Evaluating for {} nodes...".format(num_nodes))

            java_env = {
                "num_nodes": num_nodes,
            }

            sjdb_script_env = {
                "timeout": timeout,
            }

            tqdm.write(f"HIGH RANGE: {i + 1}/{len(num_nodes_options)}")

            results = pd.concat([results, experiment_for_each_task([num_nodes], java_env, sjdb_script_env, {'infer'})])

    if write_results:
        with pd.HDFStore('DoublyLinkedHighStore.h5') as store:
            store['results'] = results

    tqdm.write(results.to_string())

endtime = datetime.datetime.now()
duration = endtime - starttime

tqdm.write(f"Started experiments at {starttime}.")
tqdm.write(f"Completed experiments at {endtime}.")
tqdm.write(f"Total duration: {duration}")
