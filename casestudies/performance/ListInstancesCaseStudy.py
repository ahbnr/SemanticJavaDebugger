import datetime
from math import floor
from typing import Dict, Set

import numpy as np
import pandas as pd

from render_template import render_template
from runner import compileProject, runSJDB, SJDBResult

starttime = datetime.datetime.now()

# one class, instances increase
experiment_A = True
# one instance per class, classes increase
experiment_B = True
# instances increase and classes increase. Instances are equally distributed amongst the classes
experiment_C = True

write_results = True

resultColumns = [
    "times",
    "memory",
    "stats"
]

warmup = 5
repeat = 5
timeout = 60

tasks = ["buildkb", "sparql", "infer"]

# num_instances_options = [int(np.floor(x)) for x in np.logspace(0, 6, num=20)]
step_size = 100
max_steps = 10
num_instances_options = [step * step_size for step in range(1, max_steps + 1)]
num_classes_options = num_instances_options


def run_single_experiment(
        java_env: dict[str, any],
        sjdb_script_env: dict[str, any]
) -> SJDBResult:
    sjdb_script_env["warmup"] = warmup

    print("Rendering Java template...")
    render_template(
        filePath="java/instances/Instances.template.java",
        targetPath="java/instances/Instances.java",
        env=java_env
    )

    print("Rendering SJDB script template...")
    render_template(
        filePath="java/instances/Instances.template.sjdb",
        targetPath="java/instances/Instances.sjdb",
        env=sjdb_script_env
    )

    print("Compiling...")
    compileProject("java/instances")

    print("Running sjdb...")
    return runSJDB(
        projectPath="java/instances",
        taskfile="Instances.sjdb",
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
        index=index,
        columns=resultColumns
    )


if experiment_A:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for i, num_instances in enumerate(num_instances_options):
        print("Evaluating for {} instances...".format(num_instances))

        java_env = {
            "gen_mode": 'A',
            "num_instances": num_instances,
            "num_classes": 1,
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        print(f"EXPERIMENT A: {i + 1}/{len(num_instances_options)}")

        results = pd.concat([results, experiment_for_each_task([num_instances], java_env, sjdb_script_env, set())])

    if write_results:
        with pd.HDFStore('ExperimentAStore.h5') as store:
            store['results'] = results

    print(results.to_string())

if experiment_B:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for i, num_classes in enumerate(num_classes_options):
        print("Evaluating for {} classes...".format(num_classes))

        java_env = {
            "gen_mode": 'B',
            "num_instances": 1,
            "num_classes": num_classes
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        print(f"EXPERIMENT B: {i + 1}/{len(num_classes_options)}")

        results = pd.concat([results, experiment_for_each_task([num_classes], java_env, sjdb_script_env, set())])

    if write_results:
        with pd.HDFStore('ExperimentBStore.h5') as store:
            store['results'] = results

if experiment_C:
    results: pd.DataFrame = pd.DataFrame(index=pd.MultiIndex(
        levels=[[], []],
        codes=[[], []],
    ), columns=resultColumns)

    steps = 0
    for num_classes in num_classes_options:
        for num_instances in num_instances_options:
            if num_classes > num_instances:
                continue
            steps = steps + 1

    i = 0
    for num_classes in num_classes_options:
        for num_instances in num_instances_options:
            if num_classes > num_instances:
                continue

            instance_counts = [
                int(floor(num_instances / num_classes)) + (1 if j < (num_instances % num_classes) else 0)
                for j in range(0, num_classes)
            ]

            java_env = {
                "gen_mode": 'C',
                "num_classes": num_classes,
                "num_instances": num_instances,
                "instance_counts": instance_counts
            }

            sjdb_script_env = {
                "timeout": timeout
            }

            index = pd.MultiIndex.from_tuples(
                [(num_classes, num_instances)]
            )

            print(f"EXPERIMENT C: {i + 1}/{steps}")

            frame = experiment_for_each_task(index, java_env, sjdb_script_env, set())
            results = pd.concat([results, frame])

            i = i + 1

    if write_results:
        with pd.HDFStore('ExperimentCStore.h5') as store:
            store['results'] = results

endtime = datetime.datetime.now()

duration = endtime - starttime

print(f"Started experiments at {starttime}.")
print(f"Completed experiments at {endtime}.")
print(f"Total duration: {duration}")
