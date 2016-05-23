#
# Copyright 2015 LinkedIn Corp. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#

import sys
from org.slf4j import LoggerFactory
from com.ziclix.python.sql import zxJDBC
from wherehows.common import Constant


class HiveLoad:
  def __init__(self):
    self.logger = LoggerFactory.getLogger('jython script : ' + self.__class__.__name__)

  def load_metadata(self):
    cursor = self.conn_mysql.cursor()
    load_cmd = """
        DELETE FROM stg_dict_dataset WHERE db_id = {db_id};

        LOAD DATA LOCAL INFILE '{source_file}'
        INTO TABLE stg_dict_dataset
        FIELDS TERMINATED BY '\Z' ESCAPED BY '\0'
        (`name`, `schema`, properties, fields, urn, source, @sample_partition_full_path, source_created_time, @source_modified_time)
        SET db_id = {db_id},
        storage_type = 'Table', dataset_type = 'hive',
        source_modified_time=nullif(@source_modified_time,''),
        sample_partition_full_path=nullif(@sample_partition_full_path,''),
        wh_etl_exec_id = {wh_etl_exec_id};

        -- SELECT COUNT(*) FROM stg_dict_dataset;
        -- clear
        DELETE FROM stg_dict_dataset where db_id = {db_id}
          AND (length(`name`)) = 0
           OR `name` like 'tmp\_%'
           OR `name` like 't\_%'
        ;

        update stg_dict_dataset
        set location_prefix = substring_index(substring_index(urn, '/', 4), '/', -2) /* hive location_prefix is it's schema name*/
        WHERE db_id = {db_id} and location_prefix is null;

        update stg_dict_dataset
        set parent_name = substring_index(substring_index(urn, '/', 4), '/', -1) /* hive parent_name is it's schema name*/
        where db_id = {db_id} and parent_name is null;

        -- insert into final table
        INSERT INTO dict_dataset
        ( `name`,
          `schema`,
          schema_type,
          fields,
          properties,
          urn,
          source,
          location_prefix,
          parent_name,
          storage_type,
          ref_dataset_id,
          status_id,
          dataset_type,
          hive_serdes_class,
          is_partitioned,
          partition_layout_pattern_id,
          sample_partition_full_path,
          source_created_time,
          source_modified_time,
          created_time,
          wh_etl_exec_id
        )
        select s.name, s.schema, s.schema_type, s.fields,
          s.properties, s.urn,
          s.source, s.location_prefix, s.parent_name,
          s.storage_type, s.ref_dataset_id, s.status_id,
          s.dataset_type, s.hive_serdes_class, s.is_partitioned,
          s.partition_layout_pattern_id, s.sample_partition_full_path,
          s.source_created_time, s.source_modified_time, UNIX_TIMESTAMP(now()),
          s.wh_etl_exec_id
        from stg_dict_dataset s
        where s.db_id = {db_id}
        on duplicate key update
          `name`=s.name, `schema`=s.schema, schema_type=s.schema_type, fields=s.fields,
          properties=s.properties, source=s.source, location_prefix=s.location_prefix, parent_name=s.parent_name,
            storage_type=s.storage_type, ref_dataset_id=s.ref_dataset_id, status_id=s.status_id,
                     dataset_type=s.dataset_type, hive_serdes_class=s.hive_serdes_class, is_partitioned=s.is_partitioned,
          partition_layout_pattern_id=s.partition_layout_pattern_id, sample_partition_full_path=s.sample_partition_full_path,
          source_created_time=s.source_created_time, source_modified_time=s.source_modified_time,
            modified_time=UNIX_TIMESTAMP(now()), wh_etl_exec_id=s.wh_etl_exec_id
        ;
        """.format(source_file=self.input_schema_file, db_id=self.db_id, wh_etl_exec_id=self.wh_etl_exec_id)

    for state in load_cmd.split(";"):
      self.logger.debug(state)
      cursor.execute(state)
      self.conn_mysql.commit()
    cursor.close()

  def load_field(self):
    """
    Load fields
    :return:
    """
    cursor = self.conn_mysql.cursor()
    load_cmd = """
        DELETE FROM stg_dict_field_detail WHERE db_id = {db_id};

        LOAD DATA LOCAL INFILE '{source_file}'
        INTO TABLE stg_dict_field_detail
        FIELDS TERMINATED BY '\Z'
        (urn, sort_id, parent_sort_id, parent_path, field_name, data_type,
         @is_nullable, @default_value, @data_size, @namespace, @description)
        SET db_id = {db_id}
        , is_nullable=nullif(@is_nullable,'null')
        , default_value=nullif(@default_value,'null')
        , data_size=nullif(@data_size,'null')
        , namespace=nullif(@namespace,'null')
        , description=nullif(@description,'null')
        , last_modified=NULL;

        -- update dataset_id
        update stg_dict_field_detail sf, dict_dataset d
        set sf.dataset_id = d.id where sf.urn = d.urn
        and sf.db_id = {db_id};

        -- delete old record if it does not exist in this load batch anymore (but have the dataset id)
        -- join with dict_dataset to avoid right join using index. (using index will slow down the query)
        create temporary table if not exists t_deleted_fields (primary key (field_id))
          select x.field_id
            from stg_dict_field_detail s
              join dict_dataset i
                on s.urn = i.urn
                and s.db_id = {db_id}
              right join dict_field_detail x
                on i.id = x.dataset_id
                and s.field_name = x.field_name
                and s.parent_path = x.parent_path
          where s.field_name is null
            and x.dataset_id in (
                       select d.id dataset_id
                       from stg_dict_field_detail k join dict_dataset d
                         on k.urn = d.urn
                        and k.db_id = {db_id}
            )
        ; -- run time : ~2min

        delete from dict_field_detail where field_id in (select field_id from t_deleted_fields);

        -- update the old record if some thing changed. e.g. sort id changed
        update dict_field_detail t join
        (
          select x.field_id, s.*
          from stg_dict_field_detail s
               join dict_field_detail x
           on s.field_name = x.field_name
          and coalesce(s.parent_path, '*') = coalesce(x.parent_path, '*')
          and s.dataset_id = x.dataset_id
          where s.db_id = {db_id}
            and (x.sort_id <> s.sort_id
                or x.parent_sort_id <> s.parent_sort_id
                or x.data_type <> s.data_type
                or x.data_size <> s.data_size or (x.data_size is null XOR s.data_size is null)
                or x.data_precision <> s.data_precision or (x.data_precision is null XOR s.data_precision is null)
                or x.is_nullable <> s.is_nullable or (x.is_nullable is null XOR s.is_nullable is null)
                or x.is_partitioned <> s.is_partitioned or (x.is_partitioned is null XOR s.is_partitioned is null)
                or x.is_distributed <> s.is_distributed or (x.is_distributed is null XOR s.is_distributed is null)
                or x.default_value <> s.default_value or (x.default_value is null XOR s.default_value is null)
                or x.namespace <> s.namespace or (x.namespace is null XOR s.namespace is null)
            )
        ) p
          on t.field_id = p.field_id
        set t.sort_id = p.sort_id,
            t.parent_sort_id = p.parent_sort_id,
            t.data_type = p.data_type,
            t.data_size = p.data_size,
            t.data_precision = p.data_precision,
            t.is_nullable = p.is_nullable,
            t.is_partitioned = p.is_partitioned,
            t.is_distributed = p.is_distributed,
            t.default_value = p.default_value,
            t.namespace = p.namespace,
            t.modified = now();

        -- insert new ones
        CREATE TEMPORARY TABLE IF NOT EXISTS t_existed_field
        ( primary key (urn, sort_id, db_id) )
        AS (
        SELECT sf.urn, sf.sort_id, sf.db_id, count(*) field_count
        FROM stg_dict_field_detail sf
        JOIN dict_field_detail t
          ON sf.dataset_id = t.dataset_id
         AND sf.field_name = t.field_name
         AND sf.parent_path = t.parent_path
        WHERE sf.db_id = {db_id}
          and sf.dataset_id IS NOT NULL
        group by 1,2,3
        );


       insert into dict_field_detail (
          dataset_id, fields_layout_id, sort_id, parent_sort_id, parent_path,
          field_name, namespace, data_type, data_size, is_nullable, default_value,
           modified
        )
        select
          sf.dataset_id, 0, sf.sort_id, sf.parent_sort_id, sf.parent_path,
          sf.field_name, sf.namespace, sf.data_type, sf.data_size, sf.is_nullable, sf.default_value, now()
        from stg_dict_field_detail sf
        where sf.db_id = {db_id} and sf.dataset_id is not null
          and (sf.urn, sf.sort_id, sf.db_id) not in (select urn, sort_id, db_id from t_existed_field)
        ;

        -- delete old record in stagging
        delete from stg_dict_dataset_field_comment where db_id = {db_id};

        -- insert
        insert ignore into stg_dict_dataset_field_comment
        select t.field_id field_id, fc.id comment_id,  sf.dataset_id, {db_id}
                from stg_dict_field_detail sf
                      join field_comments fc
                  on sf.description = fc.comment
                      join dict_field_detail t
                  on sf.dataset_id = t.dataset_id
                 and sf.field_name = t.field_name
                 and sf.parent_path = t.parent_path
        where sf.db_id = {db_id};

        -- have default comment, insert it set default to 0
        insert ignore into dict_dataset_field_comment
        select field_id, comment_id, dataset_id, 0 is_default from stg_dict_dataset_field_comment where field_id in (
          select field_id from dict_dataset_field_comment
          where field_id in (select field_id from stg_dict_dataset_field_comment)
        and is_default = 1 ) and db_id = {db_id};


        -- doesn't have this comment before, insert into it and set as default
        insert ignore into dict_dataset_field_comment
        select sd.field_id, sd.comment_id, sd.dataset_id, 1 from stg_dict_dataset_field_comment sd
        left join dict_dataset_field_comment d
        on d.field_id = sd.field_id
         and d.comment_id = sd.comment_id
        where d.comment_id is null
        and sd.db_id = {db_id};


        insert into field_comments (
          user_id, comment, created, modified, comment_crc32_checksum
        )
        select 0 user_id, description, now() created, now() modified, crc32(description) from
        (
          select sf.description
          from stg_dict_field_detail sf left join field_comments fc
            on sf.description = fc.comment
          where sf.description is not null
            and fc.id is null
            and sf.db_id = {db_id}
          group by 1 order by 1
        ) d

        """.format(source_file=self.input_field_file, db_id=self.db_id)

    # didn't load into final table for now

    for state in load_cmd.split(";"):
      self.logger.debug(state)
      cursor.execute(state)
      self.conn_mysql.commit()
    cursor.close()


if __name__ == "__main__":
  args = sys.argv[1]

  l = HiveLoad()

  # set up connection
  username = args[Constant.WH_DB_USERNAME_KEY]
  password = args[Constant.WH_DB_PASSWORD_KEY]
  JDBC_DRIVER = args[Constant.WH_DB_DRIVER_KEY]
  JDBC_URL = args[Constant.WH_DB_URL_KEY]

  l.input_schema_file = args[Constant.HIVE_SCHEMA_CSV_FILE_KEY]
  l.input_field_file = args[Constant.HIVE_FIELD_METADATA_KEY]
  l.db_id = args[Constant.DB_ID_KEY]
  l.wh_etl_exec_id = args[Constant.WH_EXEC_ID_KEY]
  l.conn_mysql = zxJDBC.connect(JDBC_URL, username, password, JDBC_DRIVER)

  if Constant.INNODB_LOCK_WAIT_TIMEOUT in args:
    lock_wait_time = args[Constant.INNODB_LOCK_WAIT_TIMEOUT]
    l.conn_mysql.cursor().execute("SET innodb_lock_wait_timeout = %s;" % lock_wait_time)

  try:
    l.load_metadata()
    l.load_field()
  finally:
    l.conn_mysql.close()
