from datahub.ingestion.api.registry import Registry
from datahub.ingestion.api.transform import Transformer
from datahub.ingestion.transformer.add_dataset_ownership import (
    AddDatasetOwnership,
    SimpleAddDatasetOwnership,
)
from datahub.ingestion.transformer.add_dataset_tags import (
    AddDatasetTags,
    SimpleAddDatasetTags,
)
from datahub.ingestion.transformer.clear_dataset_ownership import (
    SimpleClearDatasetOwnership,
)
from datahub.ingestion.transformer.mark_dataset_status import MarkDatasetStatus
from datahub.ingestion.transformer.set_dataset_browse_path import (
    SetDatasetBrowsePathTransformer,
)

transform_registry = Registry[Transformer]()

transform_registry.register(
    "simple_clear_dataset_ownership", SimpleClearDatasetOwnership
)
transform_registry.register("mark_dataset_status", MarkDatasetStatus)
transform_registry.register("set_browse_path", SetDatasetBrowsePathTransformer)

transform_registry.register("add_dataset_ownership", AddDatasetOwnership)
transform_registry.register("simple_add_dataset_ownership", SimpleAddDatasetOwnership)

transform_registry.register("add_dataset_tags", AddDatasetTags)
transform_registry.register("simple_add_dataset_tags", SimpleAddDatasetTags)
