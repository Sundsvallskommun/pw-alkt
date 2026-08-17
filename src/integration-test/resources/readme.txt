Since the IT-tests are using Wiremock-scenarios and there are a lot of integrations in the process
these guidelines and conventions are used in order to make the setup more comprehensible:

****************************
* Mapping files
****************************
* The mappings file name pattern: <Java-task-worker-implementation-name (kebab-case)>---<integration-name>
  Example: "send-denial-decision-task-worker---api-casedata-get-errand.json"
  
* The attribute "scenarioName" in the mapping file is the same name as the test case name (in the IT-test java file). Kebab-case is used.
  Example: "create-process-for-non-citizen"
  
* The attributes "requieredName" and "newScenarioState" are using the mappings file names (without file suffix).
  Example: "send-denial-decision-task-worker---api-casedata-get-errand"

****************************
* Response files
****************************
* The response file names are exactly the same as the corresponding mapping file (see chapter "Mapping files"), 
  but lives in the responses directory.
  Example: "responses/send-denial-decision-task-worker---api-casedata-get-errand.json"
