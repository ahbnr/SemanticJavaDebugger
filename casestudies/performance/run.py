import runner
import tasks
from project import Project

hello_world = Project(
    projectPath="java/minimal",
    breakpoint="HelloWorld:Hello World",
    main="HelloWorld"
)

runner.compileProject(hello_world)
tasks.genTaskFile(hello_world)

result = runner.runSJDB(hello_world)
print(result["time"])
