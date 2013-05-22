The Node Utilization Report plugin creates a MWS report called "node-utilization" and creates samples for this report
from the Nodes REST API.  This report may be used to track or graph historical data of node CPU
utilization.  In order for this plugin to operate correctly, the CPU utilization metric (`cpuUtilization`) **must**
be reported for nodes by plugins (see **Reporting State Data** in the MWS Guide) or MWM Resource Managers.

# Configuration

## Poll Interval

The Poll Interval controls how often samples are created for the report.  It is recommended that this be set to
60 seconds.

## Configuration Parameters

<div class="configuration-table">This section will be replaced by MWS with the configuration parameters table</div>

> In order for the report to be modified correctly, the plugin configuration must be modified and the `recreate-report` custom
> web service (see below) must be used.

# Exposed Web Services

Method | Secured | Parameters | Parameter Types | Description
------ | ------- | ---------- | --------------- | -----------
recreate-report | Yes | None | \- | Destroys (if it exists) and recreates the node-utilization report using the current plugin configuration.

> **Warning:** This web service will destroy any associated samples and datapoints for the node-utilization report.
> Only use this call if the reports parameters must be changed and the historical data does not need to be preserved.

# Report

The first time the plugin is started, a report called "node-utilization" will be created using the plugin's
configuration parameters.  Each polling iteration then performs the following functions:

1. Verify that the "node-utilization" report exists.
2. Query MWS Nodes API version 2 for certain fields.
3. Each node is categorized according to its metric value:
	* If the lastUpdatedDate hasn't changed since the last poll, the node is ignored.
	* If the state is not Running, Busy, or Idle, the node is ignored.
	* If the cpuUtilization is null or zero, the node is ignored.
	* If the total memory on the node is null or zero, the node is ignored.
	* If the available memory on the node is null or cannot be found, the node is ignored.
	* If the total memory on the node is equal to the available memory, the node is ignored.
	* The memoryUtilization is calculated and used with the cpuUtilization as the metric values.
	* If the metric value is less than (not equal to) the low threshold, the node is marked "low".
	* If the metric value is greater than (not equal to) the high threshold, the node is marked "high".
	* Otherwise, the node is marked "medium".
4. The total number of nodes is calculated.
5. The average of all metric values is calculated for all nodes that were not ignored.
6. The metrics are recorded in the "all" section of the sample as well as its datacenter section.
7. A sample for the "node-utilization" report is created with the data generated from the previous steps.

As described in the **Reporting Framework** section of the MWS Guide, the samples are consolidated at a
regular interval into datapoints, which can then be queried using the **Get Datapoints For Single Report** resource.

It is important to note that once the report is created on the first polling interval, the report will not be destroyed
or recreated until the `recreate-report` custom web service is called.

## Example

For the node query `/rest/nodes?api-version=2&fields=metrics.cpuUtilization`, suppose that the output looked like the
following:

```
{
  "resultCount":10,
  "totalCount":10,
  "results":[
    {"metrics":{"cpuUtilization":0}},
    {"metrics":{"cpuUtilization":10}},
    {"metrics":{"cpuUtilization":25}},
    {"metrics":{"cpuUtilization":50}},
    {"metrics":{"cpuUtilization":75}},
    {"metrics":{"cpuUtilization":80}},
    {"metrics":{"cpuUtilization":100}},
    {"metrics":{}},
    {"metrics":{}},
    {"metrics":{"cpuUtilization":null}},
  ]
}
```

The resulting sample created would contain data that looked like the following:

```
{"all":
	{
     "bothHigh": 0,
     "bothLow": 0,
     "bothMedium": 0,
     "cpuHigh": 2,
     "cpuLow": 2,
     "cpuMedium": 3,
     "cpuAverage": 48.571428571428571428571428571429
     "memoryAverage": 0,
     "memoryHigh": 0,
     "memoryLow": 0,
     "memoryMedium": 0,
     "total": 10
	}
}
```