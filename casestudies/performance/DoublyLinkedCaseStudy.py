import datetime
from typing import Dict, Set

import numpy as np
import pandas as pd

from render_template import render_template
from runner import compileProject, runSJDB, SJDBResult

starttime = datetime.datetime.now()

low_range_evaluation = True
high_range_evaluation = True

write_results = True

resultColumns = [
    "times",
    "memory",
    "stats"
]

warmup = 5
repeat = 5
timeout = 60

tasks = ["buildkb", "sparql", "shacl", "infer"]


def run_single_experiment(
        java_env: dict[str, any],
        sjdb_script_env: dict[str, any]
) -> SJDBResult:
    sjdb_script_env["warmup"] = warmup

    print("Rendering Java template...")
    render_template(
        filePath="java/doublylinked/DoublyLinked.template.java",
        targetPath="java/doublylinked/DoublyLinked.java",
        env=java_env
    )

    print("Rendering SJDB script template...")
    render_template(
        filePath="java/doublylinked/DoublyLinked.template.sjdb",
        targetPath="java/doublylinked/DoublyLinked.sjdb",
        env=sjdb_script_env
    )

    print("Compiling...")
    compileProject("java/doublylinked")

    print("Running sjdb...")
    return runSJDB(
        projectPath="java/doublylinked",
        taskfile="DoublyLinked.sjdb",
        timeout=None,
        repeat=repeat
    )


def experiment_for_each_task(
        index,
        java_env: Dict[str, any],
        sjdb_script_env: Dict[str, any],
        excluded_tasks: Set[str]
) -> pd.DataFrame:
    times = None
    memory = None
    stats = None
    for task in tasks:
        if task in excluded_tasks:
            continue

        sjdb_script_env['task'] = task

        result = run_single_experiment(java_env, sjdb_script_env)

        if times is None:
            times = result.times
        else:
            times |= result.times

        new_mem = {task: result.memory['peak']}
        if memory is None:
            memory = new_mem
        else:
            memory |= new_mem

        if result.stats is not None:
            stats = result.stats

    return pd.DataFrame(
        np.array([
            [
                times,
                memory,
                stats
            ]
        ]),
        index=[index],
        columns=resultColumns
    )


if low_range_evaluation:
    num_nodes_options = [30, 40, 50, 100, 200, 500]
    print("We will be evaluating for the following node counts:")
    print(num_nodes_options)

    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for i, num_nodes in enumerate(num_nodes_options):
        print("Evaluating for {} nodes...".format(num_nodes))

        java_env = {
            "num_nodes": num_nodes,
        }

        sjdb_script_env = {
            "timeout": timeout,
        }

        print(f"LOW RANGE: {i + 1}/{len(num_nodes_options)}")

        results = pd.concat([results, experiment_for_each_task(num_nodes, java_env, sjdb_script_env, set())])

    if write_results:
        with pd.HDFStore('DoublyLinkedStore.h5') as store:
            store['results'] = results

    print(results.to_string())

if high_range_evaluation:
    start = 500
    step_size = 1000
    max_steps = 10
    num_nodes_options = [start + step * step_size for step in range(0, max_steps)]
    print("We will be evaluating for the following node counts:")
    print(num_nodes_options)

    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for i, num_nodes in enumerate(num_nodes_options):
        print("Evaluating for {} nodes...".format(num_nodes))

        java_env = {
            "num_nodes": num_nodes,
        }

        sjdb_script_env = {
            "timeout": timeout,
        }

        print(f"HIGH RANGE: {i + 1}/{len(num_nodes_options)}")

        results = pd.concat([results, experiment_for_each_task(num_nodes, java_env, sjdb_script_env, {'infer'})])

    if write_results:
        with pd.HDFStore('DoublyLinkedHighStore.h5') as store:
            store['results'] = results

    print(results.to_string())

endtime = datetime.datetime.now()
duration = endtime - starttime

print(f"Started experiments at {starttime}.")
print(f"Completed experiments at {endtime}.")
print(f"Total duration: {duration}")
