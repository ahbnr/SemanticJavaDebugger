import numpy as np
import pandas as pd

from render_template import render_template
from runner import compileProject, runSJDB, SJDBResult

low_range_evaluation = False
high_range_evaluation = True

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
    result: SJDBResult = runSJDB(
        projectPath="java/doublylinked",
        taskfile="DoublyLinked.sjdb",
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


if low_range_evaluation:
    num_nodes_options = [30, 40, 50, 100, 200, 500]
    timeout = 60
    print("We will be evaluating for the following node counts:")
    print(num_nodes_options)

    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for num_nodes in num_nodes_options:
        print("Evaluating for {} nodes...".format(num_nodes))

        java_env = {
            "num_nodes": num_nodes,
        }

        sjdb_script_env = {
            "timeout": timeout,
            "do_inference_task": True
        }

        results = pd.concat([results, run_single_experiment(num_nodes, java_env, sjdb_script_env)])

    with pd.HDFStore('DoublyLinkedStore.h5') as store:
        store['results'] = results

    print(results.to_string())

if high_range_evaluation:
    start = 500
    step_size = 5000
    max_steps = 10
    num_nodes_options = [start + step * step_size for step in range(0, max_steps)]
    timeout = 60
    print("We will be evaluating for the following node counts:")
    print(num_nodes_options)

    results: pd.DataFrame = pd.DataFrame(np.empty((0, len(resultColumns))), columns=resultColumns)

    for num_nodes in num_nodes_options:
        print("Evaluating for {} nodes...".format(num_nodes))

        java_env = {
            "num_nodes": num_nodes,
        }

        sjdb_script_env = {
            "timeout": timeout,
            "do_inference_task": False
        }

        results = pd.concat([results, run_single_experiment(num_nodes, java_env, sjdb_script_env)])

    with pd.HDFStore('DoublyLinkedHighStore.h5') as store:
        store['results'] = results

    print(results.to_string())
