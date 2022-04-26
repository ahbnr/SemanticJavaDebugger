import datetime
import itertools
from math import floor
from typing import Dict, Set, NamedTuple

import numpy as np
import pandas as pd
from tqdm import tqdm

from render_template import render_template
from runner import compileProject, runSJDB, retrieveAverageTime, retrieveMemory

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
    "memory"
]

warmup = 5
repeat = 4
timeout = 60

tasks = ["buildkb", "sparql", "infer"]

# num_instances_options = [int(np.floor(x)) for x in np.logspace(0, 6, num=20)]
step_size = 100
max_steps = 10
num_instances_options = [step * step_size for step in range(1, max_steps + 1)]
num_classes_options = num_instances_options


class ExperimentResult(NamedTuple):
    time: datetime.timedelta
    memory: int


def run_single_experiment(
        java_env: dict[str, any],
        sjdb_script_env: dict[str, any]
) -> ExperimentResult:
    sjdb_script_env["warmup"] = warmup
    sjdb_script_env["repeat"] = repeat

    projectPath = "java/instances"
    templatePrefix = "Instances"

    render_template(
        filePath=f"{projectPath}/{templatePrefix}.template.java",
        targetPath=f"{projectPath}/{templatePrefix}.java",
        env=java_env
    )

    compileProject("java/instances")

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


if experiment_A:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_instances_options), total=len(num_instances_options)) as t:
        t.set_description("EXPERIMENT A")

        for i, num_instances in t:
            tqdm.write("Evaluating for {} instances...".format(num_instances))

            java_env = {
                "gen_mode": 'A',
                "num_instances": num_instances,
                "num_classes": 1,
            }

            sjdb_script_env = {
                "timeout": timeout
            }

            tqdm.write(f"EXPERIMENT A: {i + 1}/{len(num_instances_options)}")

            results = pd.concat([results, experiment_for_each_task([num_instances], java_env, sjdb_script_env, set())])

    if write_results:
        with pd.HDFStore('ExperimentAStore.h5') as store:
            store['results'] = results

    tqdm.write(results.to_string())

if experiment_B:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    with tqdm(enumerate(num_classes_options), total=len(num_classes_options)) as t:
        t.set_description("EXPERIMENT B")

        for i, num_classes in t:
            tqdm.write("Evaluating for {} classes...".format(num_classes))

            java_env = {
                "gen_mode": 'B',
                "num_instances": 1,
                "num_classes": num_classes
            }

            sjdb_script_env = {
                "timeout": timeout
            }

            tqdm.write(f"EXPERIMENT B: {i + 1}/{len(num_classes_options)}")

            results = pd.concat([results, experiment_for_each_task([num_classes], java_env, sjdb_script_env, set())])

    if write_results:
        with pd.HDFStore('ExperimentBStore.h5') as store:
            store['results'] = results

if experiment_C:
    results: pd.DataFrame = pd.DataFrame(index=pd.MultiIndex(
        levels=[[], []],
        codes=[[], []],
    ), columns=resultColumns)


    def filter_step(step):
        num_classes, num_instances = step
        return num_classes <= num_instances


    steps = list(
        filter(
            filter_step,
            itertools.product(num_classes_options, num_instances_options)
        )
    )

    with tqdm(enumerate(steps), total=len(steps)) as t:
        t.set_description("EXPERIMENT C")

        for i, step in t:
            num_classes, num_instances = step

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

            tqdm.write(f"EXPERIMENT C: {i + 1}/{len(steps)}")

            frame = experiment_for_each_task(index, java_env, sjdb_script_env, set())
            results = pd.concat([results, frame])

            i = i + 1

    if write_results:
        with pd.HDFStore('ExperimentCStore.h5') as store:
            store['results'] = results

endtime = datetime.datetime.now()

duration = endtime - starttime

tqdm.write(f"Started experiments at {starttime}.")
tqdm.write(f"Completed experiments at {endtime}.")
tqdm.write(f"Total duration: {duration}")
