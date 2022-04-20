import os

from project import Project

sjdbJar = os.path.abspath("../../build/libs/sjdb-1.0-SNAPSHOT-all.jar")


def taskfile(project: Project) -> str:
    return os.path.abspath("{}/tasks.sjdb".format(project.projectPath))
