import datetime
from typing import Dict, Set, NamedTuple, Optional

import numpy as np
import pandas as pd
from tqdm import tqdm

from render_template import render_template
from runner import compileProject, runSJDB, retrieveAverageTime, retrieveMemory

starttime = datetime.datetime.now()

low_range_evaluation = False
infer_evaluation = False
high_range_evaluation = True

write_results = True

resultColumns = [
    "times",
    "memory"
]

warmup = 0
repeat = 1
timeout = 120

measure_memory = True
measure_times = False

tasks = ["buildkb", "sparql", "shacl", "infer"]


class ExperimentResult(NamedTuple):
    time: Optional[datetime.timedelta]
    memory: Optional[int]


def write_store(base_name: str, results: pd.DataFrame):
    if write_results:
        if measure_memory:
            name = f"{base_name}_memory.h5"
            with pd.HDFStore(name) as store:
                store['results'] = results[['memory']]
                store['meta'] = pd.DataFrame(
                    np.array([
                        [
                            repeat,
                            warmup,
                            timeout,
                        ]
                    ]),
                    columns=['repeat', 'warmup', 'timeout']
                )
        if measure_times:
            name = f"{base_name}_times.h5"
            with pd.HDFStore(name) as store:
                store['results'] = results[['times']]
                store['meta'] = pd.DataFrame(
                    np.array([
                        [
                            repeat,
                            warmup,
                            timeout,
                        ]
                    ]),
                    columns=['repeat', 'warmup', 'timeout']
                )


def run_single_experiment(
        java_env: dict[str, any],
        sjdb_script_env: dict[str, any]
) -> ExperimentResult:
    sjdb_script_env["warmup"] = warmup
    sjdb_script_env["repeat"] = repeat

    projectPath = "java/doublylinked"
    templatePrefix = "DoublyLinked"

    render_template(
        filePath=f"{projectPath}/{templatePrefix}.template.java",
        targetPath=f"{projectPath}/{templatePrefix}.java",
        env=java_env
    )

    compileProject(projectPath)

    time = None
    if measure_times:
        render_template(
            filePath=f"{projectPath}/{templatePrefix}-time.template.sjdb",
            targetPath=f"{projectPath}/{templatePrefix}.sjdb",
            env=sjdb_script_env
        )

        runSJDB(
            projectPath=projectPath,
            taskfile=f"{templatePrefix}.sjdb",
            timeout=None,
            monitorMemory=False,
            printer=tqdm.write
        )
        time = retrieveAverageTime(projectPath)

    # memory
    memory = None
    if measure_memory and repeat > 0:
        lastMemoryUsage: int = 0
        memory = 0
        with tqdm(range(0, repeat), total=repeat) as t:
            for repeatIdx in t:
                avgUsage = int(memory / repeatIdx / (1024 * 1024)) if repeatIdx > 0 else 0
                avgUsageStr = f"{avgUsage} MiB"
                lastUsageStr = f"{int(lastMemoryUsage / (1024 * 1024))} MiB"
                t.set_description(f"MEASURING MEMORY USE (avg: {avgUsageStr}, last: {lastUsageStr})")
                render_template(
                    filePath=f"{projectPath}/{templatePrefix}-memory.template.sjdb",
                    targetPath=f"{projectPath}/{templatePrefix}.sjdb",
                    env=sjdb_script_env
                )

                runSJDB(
                    projectPath=projectPath,
                    taskfile=f"{templatePrefix}.sjdb",
                    timeout=None,
                    monitorMemory=True,
                    printer=tqdm.write
                )
                lastMemoryUsage = retrieveMemory(projectPath)
                memory += lastMemoryUsage

        memory /= repeat

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
    with tqdm(tasks, total=len(tasks)) as t:
        for task in t:
            t.set_description(f"TASK {task}")
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
    num_nodes_options = [30, 40, 50, 100, 200, 300, 400, 500]
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_nodes_options), total=len(num_nodes_options)) as t:
        for i, num_nodes in t:
            t.set_description(f"LOW RANGE ({num_nodes} nodes)")

            java_env = {
                "num_nodes": num_nodes,
            }

            sjdb_script_env = {
                "timeout": timeout,
            }

            results = pd.concat([results, experiment_for_each_task([num_nodes], java_env, sjdb_script_env, set())])

    write_store('DoublyLinkedLowStore', results)

    tqdm.write(results.to_string())

if infer_evaluation:
    num_nodes_options = [500]
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_nodes_options), total=len(num_nodes_options)) as t:
        for i, num_nodes in t:
            t.set_description(f"INFER ({num_nodes} nodes)")

            java_env = {
                "num_nodes": num_nodes,
            }

            sjdb_script_env = {
                "timeout": timeout,
            }

            results = pd.concat(
                [results, experiment_for_each_task([num_nodes], java_env, sjdb_script_env, set(tasks) - {'infer'})])

    write_store('DoublyLinkedLowStore', results)

    tqdm.write(results.to_string())

if high_range_evaluation:
    start = 500
    step_size = 1000
    max_steps = 10
    num_nodes_options = [start + step * step_size for step in range(0, max_steps)]

    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_nodes_options), total=len(num_nodes_options)) as t:

        for i, num_nodes in t:
            t.set_description(f"HIGH RANGE ({num_nodes} nodes)")

            java_env = {
                "num_nodes": num_nodes,
            }

            sjdb_script_env = {
                "timeout": timeout,
            }

            results = pd.concat([results, experiment_for_each_task([num_nodes], java_env, sjdb_script_env, {'infer'})])

    write_store('DoublyLinkedHighStore', results)

    tqdm.write(results.to_string())

endtime = datetime.datetime.now()
duration = endtime - starttime

tqdm.write(f"Started experiments at {starttime}.")
tqdm.write(f"Completed experiments at {endtime}.")
tqdm.write(f"Total duration: {duration}")
