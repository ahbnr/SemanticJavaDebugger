from typing import NamedTuple, List, Optional


class Project(NamedTuple):
    name: str
    projectPath: str
    sourcePath: Optional[str]
    classpaths: List[str]
    breakpoint: str
    main: str
