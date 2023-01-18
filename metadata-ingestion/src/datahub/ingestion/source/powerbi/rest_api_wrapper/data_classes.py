from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, List, Optional


@dataclass
class Workspace:
    """
    PowerBi Workspace
    """

    id: str
    name: str
    dashboards: List[Any]
    datasets: Dict[str, "PowerBIDataset"]


@dataclass
class DataSource:
    """
    PowerBi
    """

    id: str
    type: str
    raw_connection_detail: Dict

    def __members(self):
        return (self.id,)

    def __eq__(self, instance):
        return (
            isinstance(instance, DataSource)
            and self.__members() == instance.__members()
        )

    def __hash__(self):
        return hash(self.__members())


@dataclass
class Table:
    name: str
    full_name: str
    expression: Optional[str]


# dataclasses for PowerBi Dashboard
@dataclass
class PowerBIDataset:
    id: str
    name: str
    webUrl: Optional[str]
    workspace_id: str
    # Table in datasets
    tables: List["Table"]

    def get_urn_part(self):
        return f"datasets.{self.id}"

    def __members(self):
        return (self.id,)

    def __eq__(self, instance):
        return (
            isinstance(instance, PowerBIDataset)
            and self.__members() == instance.__members()
        )

    def __hash__(self):
        return hash(self.__members())


@dataclass
class Page:
    id: str
    displayName: str
    name: str
    order: int

    def get_urn_part(self):
        return f"pages.{self.id}"


@dataclass
class User:
    id: str
    displayName: str
    emailAddress: str
    graphId: str
    principalType: str

    def get_urn_part(self):
        return f"users.{self.id}"

    def __members(self):
        return (self.id,)

    def __eq__(self, instance):
        return isinstance(instance, User) and self.__members() == instance.__members()

    def __hash__(self):
        return hash(self.__members())


@dataclass
class Report:
    id: str
    name: str
    webUrl: str
    embedUrl: str
    description: str
    dataset: Optional["PowerBIDataset"]
    pages: List["Page"]
    users: List["User"]

    def get_urn_part(self):
        return f"reports.{self.id}"


@dataclass
class Tile:
    class CreatedFrom(Enum):
        REPORT = "Report"
        DATASET = "Dataset"
        VISUALIZATION = "Visualization"
        UNKNOWN = "UNKNOWN"

    id: str
    title: str
    embedUrl: str
    dataset: Optional["PowerBIDataset"]
    report: Optional[Any]
    createdFrom: CreatedFrom

    def get_urn_part(self):
        return f"charts.{self.id}"


@dataclass
class Dashboard:
    id: str
    displayName: str
    embedUrl: str
    webUrl: str
    isReadOnly: Any
    workspace_id: str
    workspace_name: str
    tiles: List["Tile"]
    users: List["User"]

    def get_urn_part(self):
        return f"dashboards.{self.id}"

    def __members(self):
        return (self.id,)

    def __eq__(self, instance):
        return (
            isinstance(instance, Dashboard) and self.__members() == instance.__members()
        )

    def __hash__(self):
        return hash(self.__members())
