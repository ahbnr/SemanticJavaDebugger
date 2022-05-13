import datetime
import itertools
import json
import os.path
from math import floor
from typing import Dict, Set, NamedTuple, Optional

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
experiment_B2 = True
# instances increase and classes increase. Instances are equally distributed amongst the classes
experiment_C = True

triple_stats = False

write_results = True

measure_times = True
measure_memory = True

measure_low = True
measure_high = True

resultColumns = [
    "times",
    "memory"
]

warmup = 5
repeat = 10
timeout = 60

tasks = ["buildkb", "sparql", "infer"]

# num_instances_options = [int(np.floor(x)) for x in np.logspace(0, 6, num=20)]
step_size = 100
max_steps = 10
num_instances_options_low = [step * step_size for step in range(1, max_steps + 1)]
num_classes_options_low = num_instances_options_low

step_size_high = 100
max_steps_high = 10
num_instances_options_high = [5000, 10000, 15000, 20000]  # [step * step_size for step in range(1, max_steps + 1)]
num_classes_options_high = num_instances_options_high

num_classes_options_dict = {}
num_instances_options_dict = {}

if measure_low:
    num_classes_options_dict['low'] = num_classes_options_low
    num_instances_options_dict['low'] = num_instances_options_low

if measure_high:
    num_classes_options_dict['high'] = num_classes_options_high
    num_instances_options_dict['high'] = num_instances_options_high


def check_if_paused(t):
    while os.path.exists("suspend"):
        t.set_description("Detected suspend file. Suspending until ENTER is pressed")
        input()


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

    projectPath = "java/instances"
    templatePrefix = "Instances"

    render_template(
        filePath=f"{projectPath}/{templatePrefix}.template.java",
        targetPath=f"{projectPath}/{templatePrefix}.java",
        env=java_env
    )

    compileProject("java/instances")

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
                check_if_paused(t)

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
            check_if_paused(t)

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


if experiment_A:
    with tqdm(num_instances_options_dict) as tOuter:
        for key in tOuter:
            check_if_paused(tOuter)

            results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

            tOuter.set_description(f"EXPERIMENT A ({key})")
            num_instances_options = num_instances_options_dict[key]

            with tqdm(enumerate(num_instances_options), total=len(num_instances_options)) as t:
                for i, num_instances in t:
                    check_if_paused(t)
                    t.set_description(f"Instances: {num_instances}")

                    tqdm.write("Evaluating for {} instances...".format(num_instances))

                    java_env = {
                        "gen_mode": 'A',
                        "num_instances": num_instances,
                        "num_classes": 1,
                    }

                    sjdb_script_env = {
                        "timeout": timeout
                    }

                    results = pd.concat(
                        [results, experiment_for_each_task([num_instances], java_env, sjdb_script_env, set())])

            write_store(f'ExperimentAStore_{key}', results)

    tqdm.write(results.to_string())

if experiment_B:
    with tqdm({'low': num_classes_options_low}) as tOuter:
        for key in tOuter:
            check_if_paused(t)
            results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

            tOuter.set_description(f"EXPERIMENT B ({key})")
            num_classes_options = num_instances_options_dict[key]

            with tqdm(enumerate(num_classes_options), total=len(num_classes_options)) as t:
                for i, num_classes in t:
                    check_if_paused(t)
                    t.set_description(f"NUM CLASSES: {num_classes})")

                    tqdm.write("Evaluating for {} classes...".format(num_classes))

                    java_env = {
                        "gen_mode": 'B',
                        "num_instances": 1,
                        "num_classes": num_classes
                    }

                    sjdb_script_env = {
                        "timeout": timeout
                    }

                    results = pd.concat(
                        [results, experiment_for_each_task([num_classes], java_env, sjdb_script_env, set())])

            write_store(f'ExperimentBStore_{key}', results)

if experiment_B2:
    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    num_classes_options = [100 * n for n in range(1, 16)]

    with tqdm(enumerate(num_classes_options), total=len(num_classes_options)) as t:
        for i, num_classes in t:
            check_if_paused(t)
            t.set_description(f"EXPERIMENT B2 - num classes: {num_classes})")

            tqdm.write("Evaluating for {} classes...".format(num_classes))

            java_env = {
                "gen_mode": 'B',
                "num_instances": 1,
                "num_classes": num_classes
            }

            sjdb_script_env = {
                "timeout": 120,
                "num_classes": num_classes
            }

            results = pd.concat(
                [results,
                 experiment_for_each_task([num_classes], java_env, sjdb_script_env, set(tasks) - {'infer'})])

    write_store(f'ExperimentB2Store', results)

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
            itertools.product(num_classes_options_low, num_instances_options_low)
        )
    )

    with tqdm(enumerate(steps), total=len(steps)) as t:
        for i, step in t:
            check_if_paused(t)
            num_classes, num_instances = step

            t.set_description(f"EXPERIMENT C ({num_classes} classes, {num_instances} instances)")

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

    write_store('ExperimentCStore', results)

if triple_stats:
    projectPath = "java/instances"
    templatePrefix = "Instances"

    java_env = {
        "gen_mode": 'B',
        "num_classes": 5000
    }

    sjdb_script_env = {}

    statFilePath = f"{projectPath}/stats.json"
    if os.path.exists(statFilePath):
        os.remove(statFilePath)

    render_template(
        filePath=f"{projectPath}/{templatePrefix}.template.java",
        targetPath=f"{projectPath}/{templatePrefix}.java",
        env=java_env
    )

    compileProject("java/instances")

    render_template(
        filePath=f"{projectPath}/{templatePrefix}-stats.template.sjdb",
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

    with open(statFilePath) as f:
        stats = json.load(f)
        numTriples = stats["numTriples"]
        print(f"NUM TRIPLES: {numTriples}")

endtime = datetime.datetime.now()

duration = endtime - starttime

tqdm.write(f"Started experiments at {starttime}.")
tqdm.write(f"Completed experiments at {endtime}.")
tqdm.write(f"Total duration: {duration}")
