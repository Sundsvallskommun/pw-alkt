# PwAlkt

<p>Alkt is a service which integrates with the Operaton process engine for starting and updating processes. It hosts the
process definitions of the alkohol domain - <strong>ansokan</strong> today, with <strong>anmalan</strong> and
<strong>tillsyn</strong> to follow - together with the business logic and integrations they need.</p>

<p>The service is a skeleton: the API, the engine integration and the integration test harness are in place, while the
process models are empty phase structures and no task workers have been implemented yet.</p>

<h3>Process definitions</h3>

<p>Every process model in <span class="code">src/main/resources/processmodels</span> is deployed to the tenant
<span class="code">ALKT</span> at startup, so a new schema is picked up by adding the file. The key of a process (the
<span class="code">id</span> attribute of its <span class="code">bpmn:process</span> element) has a matching constant in
<span class="code">se.sundsvall.alkt.Constants</span>.</p>

<table class="settings">
	<thead>
		<tr>
			<th>File</th>
			<th>Process key</th>
			<th>Description</th>
		</tr>
	</thead>
	<tbody>
		<tr>
			<td class="code">alkt-ansokan.bpmn</td>
			<td class="code">alkt-ansokan</td>
			<td>Ansökan</td>
		</tr>
	</tbody>
</table>

<h3>Automatic deployment</h3>

<p>The automatic deployment interprets properties present in the application yaml file. The following settings are used to configure the automatic deployment mechanism:</p>

<table class="settings">
	<thead>
		<tr>
			<th>Setting</th>
			<th>Description</th>
			<th>Default&nbsp;value</th>
		</tr>
	</thead>
	<tbody>
		<tr>
			<td class="code">camunda.bpm.client.base-url</td>
			<td>URL address to the process engine rest api the external task client polls. Points at the same Operaton
			engine as the rest client, so polling always hits the engine this instance targets</td>
			<td><strong>null</strong></td>
		</tr>
		<tr>
			<td class="code">process-engine.deployment</td>
			<td>The node contains information about the processes that shall be deployed</td>
			<td><strong>null</strong></td>
		</tr>
		<tr>
			<td class="code">process-engine.deployment.autoDeployEnabled</td>
			<td>When set to <strong>false</strong> then autodeploy is disabled</td>
			<td><strong>true</strong></td>
		</tr>
		<tr>
			<td class="code">process-engine.deployment.processes</td>
			<td>When deployment node is present, the processes node should contain a list<br />
			of one or more processes to deploy (in one or more tenant namespaces)</td>
			<td><strong>emtpy list</strong></td>
		</tr>
	</tbody>
</table>

<p>The following attributes are possible to configure for each entry in the list of processes:</p>

<table class="settings">
	<thead>
		<tr>
			<th>Setting</th>
			<th>Description</th>
			<th>Default&nbsp;value</th>
		</tr>
		<tr>
			<td class="code">name</td>
			<td>Human readable name of the process, must not be null or empty</td>
			<td><strong>null</strong></td>
		</tr>
		<tr>
			<td class="code">tenant</td>
			<td>
				The tenant id that owns the process which will affect in which namespace the process will be deployed.<br />
				If no id is present, the process will be deployed to the default namespace (making it a shared process, usable<br />
				for all tenants in the engine)
			</td>
			<td><strong>null</strong></td>
		</tr>
		<tr>
			<td class="code">bpmnResourcePattern</td>
			<td>
				Pattern to match when searching for bpmn resources in the service.<br />
				For example&nbsp;<span class="code">classpath*:processmodels/*.bpmn</span>
			</td>
			<td><strong>classpath*:**/*.bpmn</strong></td>
		</tr>
		<tr>
			<td class="code">dmnResourcePattern</td>
			<td>
				Pattern to match when searching for dmn resources in the service.<br />
				For example&nbsp;<span class="code">classpath*:processmodels/*.dmn</span>
			</td>
			<td><strong>classpath*:**/*.dmn</strong></td>
		</tr>
		<tr>
			<td class="code">formResourcePattern</td>
			<td>
				Pattern to match when searching for form resources in the service.<br />
				For example&nbsp;<span class="code">classpath*:processmodels/*.form</span>
			</td>
			<td><strong>classpath*:**/*.form</strong></td>
		</tr>
	</thead>
</table>

<p>Below is an example definition for a single process for tenant id "my_namespace" with defined process models in the awesome directory:</p>

<table class="settings">
	<tbody>
		<tr>
			<th>
				Example
			</th>
		</tr>
		<tr>
			<td class="code">
			<span class="code">
				process-engine:<br />
				&nbsp; deployment:<br />
				&nbsp; &nbsp; processes:<br />
				&nbsp; &nbsp; &nbsp; - name: My awesome process<br />
				&nbsp; &nbsp; &nbsp; &nbsp; tenant: my_namespace<br />
				&nbsp; &nbsp; &nbsp; &nbsp; bpmnResourcePattern: classpath*:processmodels/awesome/*.bpmn<br />
				&nbsp; &nbsp; &nbsp; &nbsp; dmnResourcePattern: classpath*:processmodels/awesome/*.dmn<br />
				&nbsp; &nbsp; &nbsp; &nbsp; formResourcePattern: classpath*:processmodels/awesome/*.form
			</span>
			</td>
		</tr>
	</tbody>
</table>

## Status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)

## 

Copyright &copy; 2026 Sundsvalls kommun
