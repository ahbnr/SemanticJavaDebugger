import math
from typing import Dict, Set

import numpy as np
import pandas as pd

from render_template import render_template
from runner import compileProject, runSJDB, SJDBResult

# one class, instances increase
experiment_A = True
# one instance, classes increase
experiment_B = True
# fixed instances, classes increase. All instances are of the first class
experiment_C = True
# fixed instances, classes increase. Instances are equally distributed amongst the classes
experiment_D = True
# instances increase and classes increase. Instances are equally distributed amongst the classes
experiment_E = True

resultColumns = [
    "times",
    "memory",
    "stats"
]

warmup = 10
repeat = 5
timeout = 60

tasks = ["buildkb", "sparql", "infer"]

# num_instances_options = [int(np.floor(x)) for x in np.logspace(0, 6, num=20)]
step_size = 100
max_steps = 10
num_instances_options = [step * step_size for step in range(1, max_steps + 1)]
num_classes_options = num_instances_options

num_fixed_instances = 500


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

    for num_instances in num_instances_options:
        print("Evaluating for {} instances...".format(num_instances))

        java_env = {
            "gen_mode": 'A',
            "num_instances": num_instances,
            "num_classes": 1,
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        results = pd.concat([results, experiment_for_each_task([num_instances], java_env, sjdb_script_env, set())])

    with pd.HDFStore('ExperimentAStore.h5') as store:
        store['results'] = results

    print(results.to_string())

if experiment_B:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for num_classes in num_classes_options:
        print("Evaluating for {} classes...".format(num_classes))

        java_env = {
            "gen_mode": 'B',
            "num_instances": 1,
            "num_classes": num_classes
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        results = pd.concat([results, experiment_for_each_task([num_classes], java_env, sjdb_script_env, set())])

    with pd.HDFStore('ExperimentBStore.h5') as store:
        store['results'] = results

if experiment_C:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    print("We will be evaluating for the following class counts:")
    print(num_classes_options)

    for num_classes in num_classes_options:
        java_env = {
            "gen_mode": 'C',
            "num_instances": num_fixed_instances,
            "num_classes": num_classes,
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        results = pd.concat([results, experiment_for_each_task([num_classes], java_env, sjdb_script_env, set())])

    with pd.HDFStore('ExperimentCStore.h5') as store:
        store['results'] = results

if experiment_D:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for num_classes in num_classes_options:
        java_env = {
            "gen_mode": 'D',
            "num_classes": num_classes,
            "num_instances": num_fixed_instances,
            "instances_per_class": int(math.ceil(num_fixed_instances / num_classes)),
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        results = pd.concat([results, experiment_for_each_task([num_classes], java_env, sjdb_script_env, set())])

    with pd.HDFStore('ExperimentDStore.h5') as store:
        store['results'] = results

if experiment_E:
    results: pd.DataFrame = pd.DataFrame(index=pd.MultiIndex(
        levels=[[], []],
        codes=[[], []],
    ), columns=resultColumns)

    for num_classes in num_classes_options:
        for num_instances in num_instances_options:
            java_env = {
                "gen_mode": 'E',
                "num_classes": num_classes,
                "num_instances": num_instances
            }

            sjdb_script_env = {
                "timeout": timeout
            }

            index = pd.MultiIndex.from_tuples(
                [(num_classes, num_instances)]
            )

            frame = experiment_for_each_task(index, java_env, sjdb_script_env, set())
            results = pd.concat([results, frame])

    with pd.HDFStore('ExperimentEStore.h5') as store:
        store['results'] = results
