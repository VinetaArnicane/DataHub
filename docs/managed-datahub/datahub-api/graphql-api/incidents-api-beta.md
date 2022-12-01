---
description: This page provides an overview of working with the DataHub Incidents API.
---

# Incidents API (Beta)

## Introduction

**Incidents** are a concept used to flag particular Data Assets as being in an unhealthy state. Each incident has an independent lifecycle and details including a state (active, resolved), a title, a description, & more.&#x20;

A couple scenarios in which incidents can be useful are

1. **Pipeline Circuit Breaking:** You can use Incidents as the basis for intelligent data pipelines that verify upstream inputs (e.g. datasets) are free of any active incidents before executing.&#x20;
2. \[Coming Soon] **Announcing Known-Bad Assets**: You can mark a known-bad data asset as under an ongoing incident so consumers and stakeholders can be informed about the health status of a data asset via the DataHub UI. Moreover, they can follow the incident as it progresses toward resolution.&#x20;

&#x20;In the next section, we'll show you how to&#x20;

1. Create a new incident
2. Fetch all incidents for a data asset
3. Resolve an incident

for **Datasets** using the Acryl [GraphQL API](./).

Let's get started!&#x20;

## Creating an Incident&#x20;

:::info
Creating incidents is currently only supported against **Dataset** assets.&#x20;
:::

To create (i.e. raise) a new incident for a data asset, simply create a GraphQL request using the `raiseIncident` mutation.&#x20;

```
type Mutation {
    """
    Raise a new incident for a data asset 
    """
    raiseIncident(input: RaiseIncidentInput!): String! # Returns new Incident URN.
}

input RaiseIncidentInput {
  """
  The type of incident, e.g. OPERATIONAL
  """
  type: IncidentType!

  """
  A custom type of incident. Present only if type is 'CUSTOM'
  """
  customType: String

  """
  An optional title associated with the incident
  """
  title: String

  """
  An optional description associated with the incident
  """
  description: String

  """
  The resource (dataset, dashboard, chart, dataFlow, etc) that the incident is associated with.
  """
  resourceUrn: String!

  """
  The source of the incident, i.e. how it was generated
  """
  source: IncidentSourceInput
}
```

### Examples&#x20;

First, we'll create a demo GraphQL query, then show how to represent it via CURL & Python.&#x20;

Imagine we want to raise a new incident on a Dataset with URN `urn:li:dataset:(abc)` because it's failed automated quality checks. To do so, we could make the following GraphQL query:

_Request_

```
mutation raiseIncident {
  raiseIncident(input: {
    type: OPERATIONAL
    title: "Dataset Failed Quality Checks"
    description: "Dataset failed 2/6 Quality Checks for suite run id xy123mksj812pk23."
    resourceUrn: "urn:li:dataset:(urn:li:dataPlatform:kafka,SampleKafkaDataset,PROD)"
  })
}
```

After we make this query, we will get back a unique URN for the incident.

_Response_

```
{
  "data": {
    "raiseIncident": "urn:li:incident:bfecab62-dc10-49a6-a305-78ce0cc6e5b1"
  }
}
```

Now we'll see how to issue this query using a CURL or Python.&#x20;

#### CURL

To issue the above GraphQL as a CURL:&#x20;

```
curl --location --request POST 'https://your-account.acryl.io/api/graphql' \
--header 'Authorization: Bearer your-access-token' \
--header 'Content-Type: application/json' \
--data-raw '{"query":"mutation raiseIncident {\n  raiseIncident(input: {\n    type: OPERATIONAL\n    title: \"Dataset Failed Quality Checks\"\n    description: \"Dataset failed 2/6 Quality Checks for suite run id xy123mksj812pk23.\"\n    resourceUrn: \"urn:li:dataset:(urn:li:dataPlatform:kafka,SampleKafkaDataset,PROD)\"\n  })\n}","variables":{}}'
```

#### Python

To issue the above GraphQL query in Python (requests):&#x20;

```
import requests

datahub_session = requests.Session()

headers = {
    "Content-Type": "application/json",
    "Authorization": "Bearer your-personal-access-token",
}

json = {
    "query": """mutation raiseIncident {\n
      raiseIncident(input: {\n
        type: OPERATIONAL\n
        resourceUrn: \"urn:li:dataset:(urn:li:dataPlatform:kafka,SampleKafkaDataset,PROD)\"\n
      })}""",
    "variables": {},
}

response = datahub_session.post(f"https://your-account.acryl.io/api/graphql", headers=headers, json=json)
response.raise_for_status()
res_data = response.json() # Get result as JSON
```

## Retrieving Active Incidents&#x20;

To fetch the the ongoing incidents for a data asset, we can use the `incidents` GraphQL field on the entity of interest.&#x20;

### Datasets

To retrieve all incidents for a Dataset with a particular [URN](../concepts/urns.md), you can reference the 'incidents' field of the Dataset type:&#x20;

```
type Dataset {
  ....
  """
  Incidents associated with the Dataset
  """
  incidents(
    """
    Optional incident state to filter by, defaults to any state.
    """
    state: IncidentState,
    """
    Optional start offset, defaults to 0.
    """
    start: Int,
    """
    Optional start offset, defaults to 20.
    """
    count: Int): EntityIncidentsResult # Returns a list of incidents. 
}
```

### Examples&#x20;

Now that we've raised an incident on it, imagine we want to fetch the first 10 "active" incidents for the Dataset with URN `urn:li:dataset:(abc`). To do so, we could issue the following request:&#x20;

_Request_

```
query dataset {
  dataset(urn: "urn:li:dataset:(abc)") {
    incidents(state: ACTIVE, start: 0, count: 10) {
      total
      incidents {
        urn
        title
        description
        status {
          state
        }
      }
    }
  }
}
```

After we make this query, we will get back a unique URN for the incident.

_Response_

```
{
  "data": {
    "dataset": {
      "incidents": {
        "total": 1,
        "incidents": [
          {
            "urn": "urn:li:incident:bfecab62-dc10-49a6-a305-78ce0cc6e5b1",
            "title": "Dataset Failed Quality Check",
            "description": "Dataset failed 2/6 Quality Checks for suite run id xy123mksj812pk23.",
            "status": {
              "state": "ACTIVE"
            }
          }
        ]
      }
    }
  }
}
```

Now we'll see how to issue this query using a CURL or Python.&#x20;

#### CURL

To issue the above GraphQL as a CURL:&#x20;

```
curl --location --request POST 'https://your-account.acryl.io/api/graphql' \
--header 'Authorization: Bearer your-access-token' \
--header 'Content-Type: application/json' \
--data-raw '{"query":"query dataset {\n dataset(urn: "urn:li:dataset:(abc)") {\n incidents(state: ACTIVE, start: 0, count: 10) {\n total\n incidents {\n urn\n title\n description\n status {\n state\n }\n }\n }\n }\n}","variables":{}}'Python
```

To issue the above GraphQL query in Python (requests):&#x20;

```
import requests

datahub_session = requests.Session()

headers = {
    "Content-Type": "application/json",
    "Authorization": "Bearer your-personal-access-token",
}

json = {
    "query": """query dataset {\n
                  dataset(urn: "urn:li:dataset:(abc)") {\n
                    incidents(state: ACTIVE, start: 0, count: 10) {\n
                      total\n
                      incidents {\n
                        urn\n
                        title\n
                        description\n
                        status {\n
                          state\n
                        }\n
                      }\n
                    }\n
                  }\n
                }""",
    "variables": {},
}

response = datahub_session.post(f"https://your-account.acryl.io/api/graphql", headers=headers, json=json)
response.raise_for_status()
res_data = response.json() # Get result as JSON
```

## Resolving an Incident

To resolve an incident for a data asset, simply create a GraphQL request using the `updateIncidentStatus` mutation. To mark an incident as resolved, simply update its state to `RESOLVED`.&#x20;

```
type Mutation {
    """
    Update an existing incident for a resource (asset)
    """
    updateIncidentStatus(
      """
      The urn for an existing incident
      """
      urn: String!

      """
      Input required to update the state of an existing incident
      """
      input: UpdateIncidentStatusInput!): String
}

"""
Input required to update status of an existing incident
"""
input UpdateIncidentStatusInput {
  """
  The new state of the incident
  """
  state: IncidentState!

  """
  An optional message associated with the new state
  """
  message: String
}
```

### Examples&#x20;

Imagine that we've fixed our Dataset with urn `urn:li:dataset:(abc)` so that it's passing validation. Now we want to mark the Dataset as healthy, so stakeholders and downstream consumers know it's ready to use.&#x20;

To do so, we need the URN of the Incident that we raised previously.&#x20;

_Request_

```
mutation updateIncidentStatus {
  updateIncidentStatus(urn: "urn:li:incident:bfecab62-dc10-49a6-a305-78ce0cc6e5b1", 
  input: {
    state: RESOLVED
    message: "Dataset is now passing validations. Verified by John Joyce on Data Platform eng."
  })
}
```

_Response_

```
{
  "data": {
    "updateIncidentStatus": "true"
  }
}
```

True is returned if the incident's was successfully marked as resolved.&#x20;

#### CURL

To issue the above GraphQL as a CURL:&#x20;

```
curl --location --request POST 'https://your-account.acryl.io/api/graphql' \
--header 'Authorization: Bearer your-access-token' \
--header 'Content-Type: application/json' \
--data-raw '{"query":"mutation updateIncidentStatus {\n updateIncidentStatus(urn: "urn:li:incident:bfecab62-dc10-49a6-a305-78ce0cc6e5b1", \n input: {\n state: RESOLVED\n message: "Dataset is now passing validations. Verified by John Joyce on Data Platform eng."\n })\n}","variables":{}}'Python
```

To issue the above GraphQL query in Python (requests):&#x20;

```
import requests

datahub_session = requests.Session()

headers = {
    "Content-Type": "application/json",
    "Authorization": "Bearer your-personal-access-token",
}

json = {
    "query": """mutation updateIncidentStatus {\n
                  updateIncidentStatus(urn: \"urn:li:incident:bfecab62-dc10-49a6-a305-78ce0cc6e5b1\",\n
                  input: {\n
                    state: RESOLVED\n
                    message: \"Dataset is now passing validations. Verified by John Joyce on Data Platform eng.\"\n
                  })\n
                }""",
    "variables": {},
}

response = datahub_session.post(f"https://your-account.acryl.io/api/graphql", headers=headers, json=json)
response.raise_for_status()
res_data = response.json() # Get result as JSON  
```

## Tips

:::info
**Authorization**

Remember to always provide a DataHub Personal Access Token when calling the GraphQL API. To do so, just add the 'Authorization' header as follows:

```
Authorization: Bearer <personal-access-token>
```

**Exploring GraphQL API**

Also, remember that you can play with an interactive version of the Acryl GraphQL API at `https://your-account-id.acryl.io/api/graphiql`
:::

## Enabling Slack Notifications

You can configure Acryl to send slack notifications to a specific channel when incidents are raised or their status is changed.&#x20;

These notifications are also able to tag the immediate asset's owners, along with the owners of downstream assets consuming it.&#x20;

![](../../imgs/saas/Screen-Shot-2022-03-22-at-6.46.41-PM.png)

To do so, simply follow the [Slack Integration Guide](../../integrations/slack-integration.md) and contact your Acryl customer success team to enable the feature! &#x20;

