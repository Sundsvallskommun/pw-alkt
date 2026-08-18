Since the IT-tests are using Wiremock-scenarios and there are a lot of integrations in the process
these guidelines and conventions are used in order to make the setup more comprehensible:

****************************
* Mapping files
****************************
* The mappings file name pattern: <Java-task-worker-implementation-name (kebab-case)>---<integration-name>
  Example: "create-errand-task-worker---api-support-management-create-errand.json"

* The attribute "scenarioName" in the mapping file is the same name as the test case name (in the IT-test java file). Kebab-case is used.
  Example: "process-without-deviation"

* The attributes "requiredScenarioState" and "newScenarioState" are using the mappings file names (without file suffix).
  Example: "create-errand-task-worker---api-support-management-create-errand"

****************************
* Response files
****************************
* The response file names are exactly the same as the corresponding mapping file (see chapter "Mapping files"),
  but lives in the responses directory.
  Example: "responses/create-errand-task-worker---api-support-management-create-errand.json"

****************************
* Boot-time stubs
****************************
* Stubs that have to answer before the Spring context is up (see Wiremock/mappings/api-gateway-token.json) live as
  static mapping files, since a programmatically registered stub is created too late for them. Everything else is
  registered programmatically from the test, see apptest.mock.api.
