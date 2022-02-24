from typing import NamedTuple


class Project(NamedTuple):
    name: str
    projectPath: str
    breakpoint: str
    main: str
