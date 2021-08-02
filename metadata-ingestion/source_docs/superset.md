# Superset

To install this plugin, run `pip install 'acryl-datahub[superset]'`.

This plugin extracts the following:

- List of charts and dashboards

```yml
source:
  type: superset
  config:
    connect_uri: http://localhost:8088

    username: user
    password: pass
    provider: db | ldap

    env: "PROD" # Optional, default is "PROD"
```

See documentation for superset's `/security/login` at https://superset.apache.org/docs/rest-api for more details on superset's login api.

## Questions

If you've got any questions on configuring this source, feel free to ping us on [our Slack](https://slack.datahubproject.io/)!
