import os

from project import Project

sjdb = os.path.abspath("../../sjdb")


def taskfile(project: Project) -> str:
    return os.path.abspath("{}/tasks.sjdb".format(project.projectPath))
