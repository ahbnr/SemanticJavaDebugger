import os

from project import Project

sjdb = os.path.abspath("../../sjdb")


def taskfile(project: Project) -> str:
    return "{}/tasks.sjdb".format(project.projectPath)
