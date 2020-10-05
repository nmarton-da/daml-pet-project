# Overview

Pet project is implementing a realestate development facilitator service.

This involves 3 important roles:
* Service Provider: she/he provides the service, and governs projects and cash-accounts related. Much like a 
bank and/or a lawyer, aided by this DAML application
* Vendor: she/he is responsible to design new projects, and works on / completes tasks in the project
* Client: she/he is responsible to launch new projects designed by the vendor, and she approves/rejects completed tasks

Main (DAML) entities:
* Cash accounts: both vendor and client need to have cash account at same service provider to enter into contractual 
relationship. Cash accounts hold the balance and a reserved amount: upon start of the project on the clienet's 
cash-account the sufficient amount is reserved to meet the contractual relationships later, as tasks finish
* Pending projects: This is the desing phase of the project, a set of tasks are defined, which form a dependency graph 
(tasks cannot start if their dependency is not completed adn approved), and have an amount associated which will be 
transfered from client reserves to vendor on approval. Pending project need to be signed by all three parties, and then
the client can launch/start it by creating a Project
* Projects: Consist of tasks and state of those. Client and vendor can play out the process together. On approval of a
task cash will be wired from client to vendor. Can be concluded upon all tasks are done.

Solution Components:
* DAML: holds all data in contracts and takes care about validation as well.
* DAML Trigger: automated settlement of in-flight cash
* CLI: a command line interface written in scala, using DAML scala bindings (gRPC interface to ledger, listening to 
exercise events, and contract updates, and issueing commands to the ledger ondemand). Integrates jline CLI library.
* Web-app: simple webapp in typescript and DAML/react to show cash-acounts and projects, and to act on tasks. Uses
HTTP JSON ledger api.  

# Building

DAML: `daml build`

Web-app code gen: `daml codegen js .daml/dist/realestate-facilitator-0.0.1.dar -o daml.js`

Then from ui directory:

`yarn install --force --frozen-lockfile`

Scala app: `./sbt compile`

# Running locally

Main DAML script spawns three parties by default: MrTrust (service provider), CustomerAlice (client) and BuilderBob 
(vendor) and creates a project named 111-house.

- DAML: `daml start`
- DAML Trigger (needs to run as the service provider party, see last parameter): `daml trigger --dar .daml/dist/realestate-facilitator-0.0.1.dar --trigger-name RealFac.Triggers.Settler:settlerTrigger --ledger-host localhost --ledger-port 6865 --ledger-party MrTrust`
- Web-app: from the ui directory `yarn start`, and then from browser: `http://localhost:3000`
- CLI (last parameter is the name of the Party which onbehalf the CLI connect to the ledger): `./sbt "application/runMain com.daml.realfac.CliApp localhost 6865 BuilderBob"`
