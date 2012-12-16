The ONE w/modifications
=======================

This project is, I guess, a branch of The ONE, a simulation environment for mobile, opportunistic networks. It includes a bunch of additions and modifications I've made to the simulator while I was working with it. The most notable seems to be an implementation of BubbleRap, a routing protocol from The Haggle Project at the University of Cambridge. There's also a route building utility, secondary namespaces for settings, a command line option for changing a single setting, and improvements to the WorkingDayMovement model... the last of which may or may not be in version 1.5 of The ONE.

Social Framework
================
BubbleRap is implemented in a fairly generic way, such that competing algorithms can be inserted in its place (hopefully with ease). Here's the major classes:

Social Routing
--------------
* routing.community.CommunityDecisionEngine (Interface)
* routing.community.DistributedBubbleRap
* routing.community.LABELDecisionEngine

Community Detection
-------------------
* routing.community.CommunityDetection (Interface)
* routing.community.SimpleCommunityDetection
* routing.community.KCliqueCommunityDetection

Centrality Algorithms
---------------------
* routing.community.Centrality (Interface)
* routing.community.DegreeCentrality
* routing.community.AvgDegreeCentrality
* routing.community.SWindowCentrality
* routing.community.CWindowCentrality

Added Reports
-------------
* report.CommunityDetectionReport
* report.DeliveryCentralityReport
* report.SimpleCommunityDetectionReport

Example Settings for BubbleRap with KClique community detection and CWindow centrality
--------------------------------------------------------------------------------------
```
Group.router = DecisionEngineRouter
DecisionEngineRouter.decisionEngine = community.DistributedBubbleRap
DecisionEngineRouter.communityDetectAlg = routing.community.KCliqueCommunityDetection
DecisionEngineRouter.K = 5
DecisionEngineRouter.familiarThreshold = 700
DecisionEngineRouter.centralityAlg = routing.community.CWindowCentrality
```

Decision Engine Router
======================
* routing.DecisionEngineRouter
  
  This is just a routing framework that supports my usual M.O. when developing a routing protocol on ONE.
  I had noticed that much of the simulation time was spent calling MessageRouter.update() and that many of 
  the decisions made in the function didn't really change until an event occurred, like a connection going 
  up or down. This router is designed to remove as much code from the update() call as possible by keeping 
  a list of premade routing decisions (message paired with connections to forward them across) that gets 
  updated when events occur. To make this generic, when an event occurs, another object is consulted to 
  make the actual decisions, which must implement the routing.RoutingDecisionEngine interface.

* routing.RoutingDecisionEngine
  
  An interface that abstracts routing logic from the DecisionEngineRouter. Each method generally corresponds to
  an event that occurs in the router that requires a decision to be made.

Spray and Focus Routing
=======================
* routing.SprayAndFocusRouter
* routing.decisionengine.SnFDecisionEngine

Movement Models
===============
* movement.FixedMovement
* movement.EveningActivityControlSystem
* movement.EveningActivityMovement
* movement.EveningTrip
* movement.BusTravellerMovement

Network Interfaces
==================
* core.Activeness

  An interface to abstract the method by which a node or some other entity determines whether or not it is currently active.

* core.NetworkInterface
  
  A version of NetworkInterface augmented to support interfaces.APInterface and interfaces.SmartphoneActiveness. It 
  defines its own concept of activeness where a given network interface can be actively trying to create connections
  with other hosts, in which case isActive() returns true, can be accepting connections from other hosts, in which
  case a newly added method, acceptingConnections(), returns true, or both.

* interfaces.APInterface
  
  A NetworkInterface subclass that acts like an access point. Collectively, all the APInterfaces in the simulation 
  maintain knowledge of all the hosts within their ranges. When a new mobile host comes in range of an AP, rather 
  than create a connection between the mobile host and itself, the AP creates multiple connections between the mobile 
  host and every other mobile host in range of any AP. As such, the AP does not act as an endpoint but provides the 
  means for a mobile device to access distant nodes out of its range.

  This is done to avoid an issue with DTN Routing. Routing protocols only update when connections change state 
  (go up or down). If a group of access points created interconnections amongst themselves, as mobile nodes connected 
  and disconnected from them, they would update their routing tables but not send those updates across the simulated 
  wired connections, meaning it would be hard to locate a destination beyond the wired network.

* interfaces.SmartphoneActiveness

  A class that provides functionality akin to the waking and sleeping behavior of a mobile device being used by a 
  human as described in Diversity in Smartphone Usage by Falaki et al. (2010). The authors found that the usage 
  behavior of a smartphone, as it relates to the ONE simulator, can be modelled as a series of epochs oscillating 
  between active and inactive states where the device is available to make and accept connections only in the 
  active intervals. They model the duration of the active intervals by adding an exponential distribution with a 
  Pareto distribution where the minimum value of the Pareto distribution is equivalent to the timeout of the 
  smartphone screen. They model the duration between active intervals as a Weibul distribution.

  The parameters for each of the (component) distributions ranges across the population of users. This class can 
  either configure the population of simulated nodes to use the same distribution parameters or select the 
  distribution parameters from the ranges found in the paper.

Settings
========

Settings.java is modified to support the following:

*  Allows the user to employ the value filling functionality for any setting
*  Adds addSetting method to support settings declarations on the command line
*  Reappropriates secondary namespaces as alternative locations for a given setting. This, in conjunction with the SimScenario
   class, facilitates a settings file such as the following:

```
  People.speed = 3, 5
  People.nrOfInterfaces = 2
  People.interface1 = btInterface
  People.interface2 = 3gInterface
  People.waitTime = 10, 30
  People.movementModel = WorkingDayMovement
     
  Bus.speed = 20, 30
  Bus.nrOfInterfaces = 1
  Bus.interface1 = btInterface
  Bus.waitTime = 30, 60
  Bus.movementModel = BusMovement
  
  Group1.secondaryNamespace = People
  Group2.secondaryNamespace = People
  Group3. ...
  
  Group10.secondaryNamespace = Bus
  Group11.secondaryNamespace = Bus
  Group12. ...
```

The command line then to override a setting looks like this:

```
java core.DTNSim -b 1:1 -d MovementModel.rngSeed=2@@Group.nrofHosts=3 my.settings
```
The argument after the '-d' switch cannot contain any spaces and multiple settings can be delimited by '@@'.

