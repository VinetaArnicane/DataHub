# Ingestion

Acryl Metadata Ingestion functions similarly to that in open source DataHub. Sources are configured via the[ UI Ingestion](https://datahubproject.io/docs/ui-ingestion/) or via a [Recipe](https://datahubproject.io/docs/metadata-ingestion/#recipes), ingestion recipes can be scheduled using your system of choice, and metadata can be pushed from anywhere.&#x20;

This document will describe the steps required to ingest metadata from your data sources.&#x20;

## Batch Ingestion&#x20;

Batch ingestion involves extracting metadata from a source system in bulk. Typically, this happens on a predefined schedule using the [Metadata Ingestion ](https://datahubproject.io/docs/metadata-ingestion#install-from-pypi)framework.&#x20;

The metadata that is extracted includes point-in-time instances of dataset, chart, dashboard, pipeline, user, group, usage, and task metadata.&#x20;

### Step 1: Install DataHub CLI&#x20;

Regardless of how you ingest metadata, you'll need your account subdomain and API key handy.

#### **Install from Gemfury Private Repository**

**Installing from command line with pip**

Determine the version you would like to install and obtain a read access token by requesting a one-time-secret from the Acryl team then run the following command:

`python3 -m pip install acryl-datahub==<VERSION> --index-url https://<TOKEN>:@pypi.fury.io/acryl-data/`

#### Install from PyPI for OSS Release

Run the following commands in your terminal:

```
python3 -m pip install --upgrade pip wheel setuptools
python3 -m pip install --upgrade acryl-datahub
python3 -m datahub version
```

_Note: Requires Python 3.6+_

Your command line should return the proper version of DataHub upon executing these commands successfully.

### Step 2: Install Connector Plugins

Our CLI follows a plugin architecture. You must install connectors for different data sources individually. For a list of all supported data sources, see [the open source docs](https://datahubproject.io/docs/metadata-ingestion/#installing-plugins).&#x20;

Once you've found the connectors you care about, simply install them using `pip install`.&#x20;

For example, to install the `mysql` connector, you can run&#x20;

```python
pip install --upgrade acryl-datahub[mysql]
```

### Step 3: Write a Recipe

[Recipes](https://datahubproject.io/docs/metadata-ingestion/#recipes) are yaml configuration files that serve as input to the Metadata Ingestion framework. Each recipe file define a single source to read from and a single destination to push the metadata.&#x20;

The two most important pieces of the file are the _source_ and _sin_k configuration blocks.&#x20;

The _source_ configuration block defines where to extract metadata from. This can be an OLTP database system, a data warehouse, or something as simple as a file. Each source has custom configuration depending on what is required to access metadata from the source. To see configurations required for each supported source, refer to the [Sources](https://datahubproject.io/docs/metadata-ingestion/#sources) documentation. &#x20;

The _sink_ configuration block defines where to push metadata into. Each sink type requires specific configurations, the details of which are detailed in the [Sinks](https://datahubproject.io/docs/metadata-ingestion/#sinks) documentation.&#x20;

In Acryl DataHub deployments, you _must_ use a sink of type `datahub-rest`, which simply means that metadata will be pushed to the REST endpoints exposed by your DataHub instance. The required configurations for this sink are&#x20;

1. **server**: the location of the REST API exposed by your instance of DataHub
2. **token**: a unique API key used to authenticate requests to your instance's REST API

The token can be retrieved by logging in as admin. You can go to Settings page and generate a Personal Access Token with your desired expiration date.&#x20;

![](../../.gitbook/assets/home (1).png)

![](../../.gitbook/assets/settings.png)

To configure your instance of DataHub as the destination for ingestion, set the "server" field of your recipe to point to your Acryl instance's domain suffixed by the path  `/gms`, as shown below.&#x20;

A complete example of a DataHub recipe file, which reads from MySQL and writes into a DataHub instance:

```yaml
# example-recipe.yml

# MySQL source configuration 
source:
  type: mysql
  config:
    username: root
    password: password
    host_port: localhost:3306

# Recipe sink configuration.
sink:
  type: "datahub-rest"
  config:
    server: "https://<your domain name>.acryl.io/gms"
    token: <Your API key>
```

{% hint style="info" %}
Your API key is a signed JSON Web Token that is valid for 6 months from the date of issuance. Please keep this key secure & avoid sharing it.&#x20;

If your key is compromised for any reason, please reach out to the Acryl team at support@acryl.io.&#x20;
{% endhint %}

### Step 4: Running Ingestion&#x20;

The final step requires invoking the DataHub CLI to ingest metadata based on your recipe configuration file.&#x20;

To do so, simply run `datahub ingest` with a pointer to your YAML recipe file:

```
datahub ingest -c ./example-recipe.yml 
```

### Step 5: Scheduling Ingestion&#x20;

Ingestion can either be run in an ad-hoc manner by a system administrator or scheduled for repeated executions. Most commonly, ingestion will be run on a daily cadence.&#x20;

To schedule your ingestion job, we recommend using a job schedule like [Apache Airflow](https://airflow.apache.org/). In cases of simpler deployments, a CRON job scheduled on an always-up machine can also work.&#x20;

Note that each source system will require a separate recipe file. This allows you to schedule ingestion from different sources independently or together.&#x20;



_Looking for information on real-time ingestion? Click_ [_here_](advanced-ingestion.md#real-time-ingestion)_._

_Note: Real-time ingestion setup is not recommended for an initial POC as it generally takes longer to configure and is prone to inevitable system errors._





