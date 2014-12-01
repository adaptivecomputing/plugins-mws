The OpenStack plugin allows elastic compute instances to be provisioned and de-provisioned using an OpenStack 
service provider.  This enables Moab Workload Manager to provision elastic computing instances on an OpenStack cloud 
in order to add or remove nodes dynamically based on policies and workload. See the Moab Workload Manager 
documentation on Elastic Computing for more information on how to configure a solution.

> **Warning:** To use the OpenStack plugin, Moab Workload Manager (MWM) must be properly configured
> as described in the **Elastic Computing** section of the MWM user guide.

# Create an OpenStack Plugin

To create an OpenStack plugin, see **Creating a plugin** in the MWS user guide.  During plugin creation, refer to the
**Configuration** section below.

# Configuration Parameters

<div class="configuration-table">MWS replaces this section with the configuration parameters table at run-time.</div>

# Web Services

<div class="webservice-sections">This section will be replaced by MWS with the web services documentation at run-time.</div>

# Troubleshooting

The OpenStack plugin logs all errors and warnings to the MWS log file, which is `/opt/mws/log/mws.log` by default.
The `stacktrace.log` file located in the same directory as `mws.log` may also be helpful in diagnosing problems.
Also, see the **Troubleshooting installation** section of the MWS user guide.