The Native plugin type uses the Wiki interface to communicate with Moab Resource Managers and return
the corresponding data to Moab Workload Manager (MWM) in the Wiki format.

> **Warning:** To use the Native plugin type, Moab Workload Manager must be properly configured as described in the
> Configuring MWM section of the MWS user's guide.

# Available Configuration Parameters

<div class="configuration-table">MWS replaces this section with the configuration parameters table.</div>

# Configuration Notes

* While no URL is specifically required, if no URLs are defined, the plugin will not make any calls or report any resources.
* Do not use `getNodes` and `getVirtualMachines` in combination with `getCluster`.  If `getCluster` is used, the `getNodes` and `getVirtualMachines` URLs will never be called.
* If `reportImages` is enabled, images will be created in the MWS image catalog based on the `OS`, `VMOSLIST`, and `VARATTR=HVTYPE` attributes for nodes and VMs
** If running more than one native plugin, make sure that each plugin instance uses different `OS` values for nodes as these cannot be matched between plugin instances
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

# Initially Created Plugins

If MWS is configured in the Moab Cloud Suite, a plugin called "cloud-native" will be created on startup.  This
may be used to report additional node and virtual machine information to MWM.

# How It Works

The Native plugin type imitates the Moab Resource Manager Native interface.  All resources reported through this interface
can be reported as Wiki to the Native plugin through its URLs.  These resources are then reported to Moab Workload
Manager through its Resource Manager interface as explained in the Plugin Introduction in the MWS user guide.

# Native Wiki Interface Comparison

Most of the Native Plugin's functionality is copied from the Native Wiki interface in Moab.  The following is a table comparing the two interfaces,
the Moab Native URL vs the plugin equivalent configuration entry, and can be used to port Native Wiki scripts to the Native Plugin.

Plugin URL | Plugin Config | Notes
---------- | ------------- | -----
ALLOCURL | \- | Deprecated
CREDCTLURL | \- | Deprecated
CLUSTERQUERYURL | getCluster *or* getNodes/getVirtualMachines | See above note in the configuration descriptions.
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

### getVirtualMachines

#### Arguments

None

#### Response

Native Wiki for virtual machines, one per line.

> Virtual machines without container nodes (e.g. their `node` property is null) will be ignored and Wiki will not
> be generated for them.  This is to prevent issues with Moab recognizing virtual machines as nodes.

```
SC=0 RESPONSE=Success
vm1 STATE=Busy;CPROC=4;APROC=0;CONTAINERNODE=hv1
vm2 STATE=Idle;CPROC=4;APROC=4;CONTAINERNODE=hv1
```

> While the Wiki interface requires a `STATE` value to be reported, the Native plugin does not have this same requirement.
> If no `STATE` value is provided, `UNKNOWN` will be used.

### getCluster

#### Arguments

None

#### Response

Native Wiki for nodes and virtual machines, one per line.  The key difference between them is that VMs have `CONTAINERNODE`
reported, while nodes do not.  Optionally, you can report a `TYPE` attribute set to `VM` if the object is a virtual machine.
If the `TYPE` is not `VM` and no `CONTAINERNODE` is present, the object will be assumed to be a node.

```
SC=0 RESPONSE=Success
hv1 STATE=Busy;CPROC=4;APROC=0;VMOSLIST=linux
hv2 STATE=Idle;CPROC=4;APROC=4;OS=hyper;VMOSLIST=windows
vm1 STATE=Busy;CPROC=2;APROC=0;CONTAINERNODE=hv1;OS=linux
vm2 STATE=Idle;CPROC=2;APROC=2;CONTAINERNODE=hv1;OS=linux
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
Moab.1 vm1 myuser
Moab.1 node01,node01,node02,node02 myuser
```

#### Response

```
SC=0 RESPONSE=Started job 'Moab.1' on 'vm1' successfully
```

### jobSubmit

#### Arguments

```
# <attribute>=<value> [<attribute>=<value>] ...
# UNAME=<userName> GNAME=<groupName> WCLIMIT=<wallClock> TASKS=<tasksNumber> NAME=<jobId> IWD=<initialWorkingDirectory> EXEC=<executable>
UNAME=myuser GNAME=mygroup WCLIMIT=600 TASKS=2 NAME=Moab.1 IWD=/tmp EXEC=/tmp/script.sh
```

#### Response

```
SC=0 RESPONSE=Submitted job 'Moab.1' successfully
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
# <hypervisorId>:<vmId> --set <attribute>=<value> [<attribute>=<value>] ...
hv1:vm1 --set Message="Powering off" Power=OFF
# node:destroy <hypervisorId>:<vmId> [operationid=<operationId>]
node:destroy hv1:vm1 operationid=vmdestroy-1
```

#### Response

```
SC=0 RESPONSE=Modified node(s) 'node01,node02' successfully
```

### nodePower

#### Arguments

```
# <nodeId>[,<nodeId>,...] <powerState>
vm1 OFF
hv1,hv2,hv3 OFF
```

`powerState` is one of the node power states.

#### Response

```
SC=0 RESPONSE=Changed power state for 'vm1' to 'OFF' successfully
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
