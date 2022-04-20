import math

import numpy as np
import pandas as pd

from render_template import render_template
from runner import compileProject, runSJDB, SJDBResult

evaluate_instances = False
evaluate_classes = False
evaluate_instances_and_classes = False
evaluate_instances_and_classes_equalized_instance_counts = False
threed = True

# num_instances_options = [int(np.floor(x)) for x in np.logspace(0, 6, num=20)]
step_size = 100
max_steps = 10
num_instances_options = [step * step_size for step in range(1, max_steps + 1)]
num_classes_options = num_instances_options
timeout = 60
print("We will be evaluating for the following instance counts:")
print(num_instances_options)

print("We will be evaluating for the following class counts:")
print(num_classes_options)

resultColumns = [
    "times",
    "stats"
]


def run_single_experiment(
        index,
        java_env: dict[str, any],
        sjdb_script_env: dict[str, any]
):
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
    result: SJDBResult = runSJDB(
        projectPath="java/instances",
        taskfile="Instances.sjdb",
        timeout=None
    )

    return pd.DataFrame(
        np.array([
            [
                result.times,
                result.stats
            ]
        ]),
        index=[index],
        columns=resultColumns
    )


if evaluate_instances:
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

        results = pd.concat([results, run_single_experiment(num_instances, java_env, sjdb_script_env)])

    with pd.HDFStore('ListInstancesStore.h5') as store:
        store['results'] = results

    print(results.to_string())

if evaluate_classes:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    print("We will be evaluating for the following class counts:")
    print(num_classes_options)

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

        results = pd.concat([results, run_single_experiment(num_classes, java_env, sjdb_script_env)])

    with pd.HDFStore('ListClassesStore.h5') as store:
        store['results'] = results

if evaluate_instances_and_classes:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    print("We will be evaluating for the following class counts:")
    print(num_classes_options)

    for num_classes in num_classes_options:
        java_env = {
            "gen_mode": 'C',
            "num_instances": 500,
            "num_classes": num_classes,
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        results = pd.concat([results, run_single_experiment(num_classes, java_env, sjdb_script_env)])

    with pd.HDFStore('ListInstancesAndClassesStore.h5') as store:
        store['results'] = results

if evaluate_instances_and_classes_equalized_instance_counts:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for num_classes in num_classes_options:
        java_env = {
            "gen_mode": 'D',
            "num_classes": num_classes,
            "num_instances": 500,
            "instances_per_class": int(math.ceil(500 / num_classes)),
        }

        sjdb_script_env = {
            "timeout": timeout
        }

        results = pd.concat([results, run_single_experiment(num_classes, java_env, sjdb_script_env)])

    with pd.HDFStore('ListInstancesAndClassesEqualizedInstanceCountsStore.h5') as store:
        store['results'] = results

if threed:

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
            result: SJDBResult = runSJDB(
                projectPath="java/instances",
                taskfile="Instances.sjdb",
                timeout=None
            )

            index = pd.MultiIndex.from_tuples(
                [(num_classes, num_instances)]
            )

            frame = pd.DataFrame(
                np.array([
                    [
                        result.times,
                        result.stats
                    ]
                ]),
                index=index,
                columns=resultColumns
            )

            results = pd.concat([results, frame])

    with pd.HDFStore('threedStore.h5') as store:
        store['results'] = results
