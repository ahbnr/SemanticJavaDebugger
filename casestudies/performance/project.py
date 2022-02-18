from typing import NamedTuple


class Project(NamedTuple):
    projectPath: str
    breakpoint: str
    main: str
