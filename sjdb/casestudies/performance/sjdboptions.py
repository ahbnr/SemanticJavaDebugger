from typing import NamedTuple


class MappingOptions(NamedTuple):
    limitSdk: bool
    closeReferenceTypes: bool

    def id(self) -> str:
        return "{}{}".format(
            "L" if self.limitSdk else "U",
            "C" if self.closeReferenceTypes else "O",
        )
