The Native plugin type uses the Wiki interface to communicate with Moab Resource Managers and return
the corresponding data to Moab Workload Manager (MWM) in the Wiki format.

> **Warning:** To use the Native plugin type, Moab Workload Manager must be properly configured as described in the
> Configuring MWM section of the MWS user's guide.

# Available Configuration Parameters

<div class="configuration-table">MWS replaces this section with the configuration parameters table.</div>

# Configuration Notes

* While no URL is specifically required, if no URLs are defined, the plugin will not make any calls or report any resources.
* Do not use `getNodes` in combination with `getCluster`.  If `getCluster` is used, the `getNodes` URLs will never be called.
* If spaces are desired to be used in the attribute value in any "get" URL, the value must be quoted with double quotes. For example:

```
# This value would fail to be parsed correctly and COMMENT would be set to "my"
node1 STATE=IDLE COMMENT=my comment
# This value would work as expected and COMMENT would be set to "my comment"
node1 STATE=IDLE COMMENT="my comment"
```

* For configuration parameters, all URLs can be defined as one of three different types:

Type | Example | File | Action
---- | ------- | ---- | ------
Flat File | file:///nodes.txt | /nodes.txt | Reads the file as a flat text file
Script | exec:///tmp/nodes.pl | /tmp/nodes.pl | Executes the file as a script and uses the output.  Must return a non-zero value for failure.
URL | http://domain.com/nodes | \- | Executes a web call using the defined URL

# Exposed Web Services

<div class="webservice-sections">This section will be replaced by MWS with the exposed web service sections</div>

# How It Works

The Native plugin type imitates the Moab Resource Manager Native interface.  All resources reported through this interface
can be reported as Wiki to the Native plugin through its URLs.  These resources are then reported to Moab Workload
Manager through its Resource Manager interface as explained in the Plugin Introduction in the MWS user guide.

# Native Wiki Interface Comparison

Most of the Native Plugin's functionality is copied from the Native Wiki interface in Moab.  However, there are differences
in both the URL configuration and the Wiki attributes themselves.

## URL Configuration

The following is a table comparing the Moab Native URL and the plugin equivalent configuration entry.  This may be
used to port Native Wiki scripts to the Native Plugin.

Plugin URL | Plugin Config | Notes
---------- | ------------- | -----
ALLOCURL | \- | Deprecated
CREDCTLURL | \- | Deprecated
CLUSTERQUERYURL | getCluster *or* getNodes | See above note in the configuration descriptions.
INFOQUERYURL | \- | Deprecated
JOBCANCELURL | jobCancel | |
JOBMIGRATEURL | \- | Deprecated
JOBMODIFYURL | jobModify | |
JOBREQUEUEURL | jobRequeue | |
JOBRESUMEURL | jobResume | |
JOBSIGNALURL | \- | Deprecated
JOBSTARTURL | jobStart | |
JOBSUBMITURL | jobSubmit | |
JOBSUSPENDURL | jobSuspend | |
JOBVALIDATEURL | \- | Deprecated
NODEMIGRATEURL | \- | Deprecated
NODEMODIFYURL | nodeModify | |
NODEPOWERURL | nodePower | |
PARCREATEURL | \- | Deprecated
PARDESTROYURL | \- | Deprecated
QUEUEQUERYURL | \- | Deprecated
RESOURCECREATEURL | \- | Deprecated
RMINITIALIZEURL | \- | Deprecated
RMSTARTURL | startUrl | This URL is no longer called by Moab, but by the plugin during the `beforeStart` phase.
RMSTOPURL | stopUrl | This URL is no longer called by Moab, but by the Plugin during the `afterStop` phase.
RSVCTLURL | \- | Deprecated
SYSTEMMODIFYURL | \- | Deprecated
SYSTEMQUERYURL | \- | Deprecated
WORKLOADQUERYURL | getJobs | |

## Additional Wiki Attributes

While most of the Wiki attributes have remained the same, there are additional Wiki attributes that have been added
in the Native plugin.  The table below lists these attributes, for which objects they are available, and possible values.

Object | Wiki Attribute | Format | Default | Description
------ | -------------- | ------ | ------- | -----------
Nodes | SLAVE | true or false | false | Support for the MWS plugin slave reports. See the MWS documentation for more details.
Jobs | SLAVE | true or false | false | Support for the MWS plugin slave reports. See the MWS documentation for more details.

# Calling URLs

This section contains a guide to the command line parameters expected and the return data from all URLs that can
be configured.  All URLs are expected to return a line with a status code and an optional message, such as:

```
SC=0 RESPONSE=Custom message here
```

Additionally, if the URL type is a script (`exec` scheme), the exit code must be zero for success and non-zero for
a failure case.

The environment can be set for any scripts run by using the `environment` configuration parameter as described above.

```
config {
	environment = "HOMEDIR=/root/&LIBDIR=/var/lib&EXTRAOPTION"
}
```

The above configuration would set `HOMEDIR`, `LIBDIR`, and `EXTRAOPTION` environment parameters on all scripts executed
through the plugin.

### getJobs

#### Arguments

None

#### Response

Native Wiki for jobs, one per line.

```
SC=0 RESPONSE=Success
Moab.1 STATE=Idle;UNAME=username;GNAME=groupname
Moab.2 STATE=Completed;UNAME=username;GNAME=groupname
```

> While the Wiki interface requires a `STATE` value to be reported, the Native plugin does not have this same requirement.
> If no `STATE` value is provided, `UNKNOWN` will be used.

### getNodes

#### Arguments

None

#### Response

Native Wiki for nodes, one per line.

```
SC=0 RESPONSE=Success
node01 STATE=Busy;CPROC=4;APROC=0
node02 STATE=Idle;CPROC=4;APROC=4
```

> While the Wiki interface requires a `STATE` value to be reported, the Native plugin does not have this same requirement.
> If no `STATE` value is provided, `UNKNOWN` will be used.

### getCluster

#### Arguments

None

#### Response

Native Wiki for nodes, one per line.

```
SC=0 RESPONSE=Success
node1 STATE=Busy;CPROC=2;APROC=0;OS=linux
node2 STATE=Idle;CPROC=2;APROC=2;OS=linux
```

> While the Wiki interface requires a `STATE` value to be reported, the Native plugin does not have this same requirement.
> If no `STATE` value is provided, `UNKNOWN` will be used.

### jobCancel

#### Arguments

```
# <jobId> [<type>]
Moab.1
Moab.1 admin ??
```

#### Response

```
SC=0 RESPONSE=Canceled job 'Moab.1'
```

### jobModify

#### Arguments

```
# -j <jobId> (--set|--clear|--increment|--decrement) <attribute>="<value>"
-j Moab.1 --set Account="newAccount"
-j Moab.1 --clear Account ??
-j Moab.1 --increment Hold="user" ??
-j Moab.1 --decrement Hold="user" ?? 
```

#### Response

```
SC=0 RESPONSE=Modified job 'Moab.1' successfully
```

### jobRequeue

#### Arguments

```
# <jobId> [<type>]
Moab.1
Moab.1 admin ??
```

#### Response

```
SC=0 RESPONSE=Requeued job 'Moab.1' successfully
```

### jobResume

#### Arguments

```
# <jobId> [<type>] ??
Moab.1
Moab.1 admin ??
```

#### Response

```
SC=0 RESPONSE=Resumed job 'Moab.1' successfully
```

### jobStart

#### Arguments

```
# <jobId> <taskList> <userName> ??
Moab.1 node01,node01,node02,node02 myuser
```

#### Response

```
SC=0 RESPONSE=Started job 'Moab.1' on 'node1' successfully
```

### jobSubmit

#### Arguments

```
# <attribute>=<value> [<attribute>=<value>] ...
# UNAME=<userName> GNAME=<groupName> WCLIMIT=<wallClock> TASKS=<tasksNumber> NAME=<jobId> IWD=<initialWorkingDirectory> EXEC=<executable> RMFLAGS="flag1 flag2"
UNAME=myuser GNAME=mygroup WCLIMIT=600 TASKS=2 NAME=Moab.1 IWD=/tmp EXEC=/tmp/script.sh RMFLAGS="flag1 flag2"
```

#### Response

The response may consist of a new job identifier on its own line.  This will be recorded in Moab as the job identifier
for the MWS resource manager.

```
SC=0 RESPONSE=Submitted job 'Moab.1' successfully
```

```
SC=0 RESPONSE=Success
MyJob.1
```

### jobSuspend

#### Arguments

```
# <jobId> [<reason>] ??
Moab.1
Moab.1 "Not ready" ??
```

#### Response

```
SC=0 RESPONSE=Suspended job 'Moab.1' successfully
```

### nodeModify

#### Arguments

```
# <nodeId>[,<nodeId>,...] --set <attribute>=<value> [<attribute>=<value>] ...
node01,node02 --set Message="Powering off" Power=OFF
```

#### Response

```
SC=0 RESPONSE=Modified node(s) 'node01,node02' successfully
```

### nodePower

#### Arguments

```
# <nodeId>[,<nodeId>,...] <powerState>
node1 ON
node2,node3,node4 OFF
```

`powerState` is one of the node power states.

#### Response

```
SC=0 RESPONSE=Changed power state for 'node1' to 'ON' successfully
```

### startUrl

#### Arguments

None.

#### Response

Exit code of 0 signifies success, other for failure.

### stopUrl

#### Arguments

None.

#### Response

Exit code of 0 signifies success, other for failure.
