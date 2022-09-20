import typing

import pydantic
from pydantic.fields import Field
from sqlalchemy import create_engine, inspect
from sqlalchemy.engine.reflection import Inspector

from datahub.configuration.common import AllowDenyPattern
from datahub.emitter.mcp_builder import PlatformKey
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.ingestion.source.sql.sql_common import (
    BasicSQLAlchemyConfig,
    SQLAlchemySource,
    logger,
    make_sqlalchemy_uri,
)


class TwoTierSQLAlchemyConfig(BasicSQLAlchemyConfig):

    database_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern.allow_all(),
        description="Regex patterns for databases to filter in ingestion.",
    )

    @pydantic.validator("database_pattern")
    def copy_deprecated_schema_pattern_option(cls, v, values):
        if "schema_pattern" in values:
            if v != AllowDenyPattern.allow_all():
                raise ValueError(
                    "Cannot specify both schema_pattern and database_pattern"
                )
            else:
                logger.warning(
                    "schema_pattern is deprecated, please use database_pattern instead."
                )
                return values.pop("schema_pattern")
        return v

    def get_sql_alchemy_url(
        self,
        uri_opts: typing.Optional[typing.Dict[str, typing.Any]] = None,
        current_db: typing.Optional[str] = None,
    ) -> str:
        return self.sqlalchemy_uri or make_sqlalchemy_uri(
            self.scheme,
            self.username,
            self.password.get_secret_value() if self.password else None,
            self.host_port,
            current_db if current_db else self.database,
            uri_opts=uri_opts,
        )


class TwoTierSQLAlchemySource(SQLAlchemySource):
    def __init__(self, config, ctx, platform):
        super().__init__(config, ctx, platform)
        self.current_database = None
        self.config: TwoTierSQLAlchemyConfig = config

    def get_parent_container_key(self, db_name: str, schema: str) -> PlatformKey:
        # Because our overridden get_allowed_schemas method returns db_name as the schema name,
        # the db_name and schema here will be the same. Hence, we just ignore the schema parameter.
        return self.gen_database_key(db_name)

    def get_allowed_schemas(
        self, inspector: Inspector, db_name: str
    ) -> typing.Iterable[str]:
        # This method returns schema names but for 2 tier databases there is no schema layer at all hence passing
        # dbName itself as an allowed schema
        yield db_name

    def get_inspectors(self):
        # This method can be overridden in the case that you want to dynamically
        # run on multiple databases.
        url = self.config.get_sql_alchemy_url()
        logger.debug(f"sql_alchemy_url={url}")
        engine = create_engine(url, **self.config.options)
        with engine.connect() as conn:
            inspector = inspect(conn)
            if self.config.database and self.config.database != "":
                databases = [self.config.database]
            else:
                databases = inspector.get_schema_names()
            for db in databases:
                if self.config.database_pattern.allowed(db):
                    url = self.config.get_sql_alchemy_url(current_db=db)
                    inspector = inspect(
                        create_engine(url, **self.config.options).connect()
                    )
                    self.current_database = db
                    yield inspector

    def gen_schema_containers(
        self, schema: str, db_name: str
    ) -> typing.Iterable[MetadataWorkUnit]:
        return []
