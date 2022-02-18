from project import Project

sjdb = "../../sjdb"


def taskfile(project: Project) -> str:
    return "{}/tasks.sjdb".format(project.projectPath)
