# PwAlkt

<p>Alkt is a service which integrates with the Operaton process engine for starting and updating processes. It hosts the
process definitions of the alkohol- och tobaksverksamheten - the permit processes and the supervision process - together
with the business logic and integrations they need.</p>

<p>The service is a skeleton: the API, the engine integration, the reporting to Support Management and the integration
test harness are in place, while the process models are empty phase structures. The only work steps so far are the one
that reports a process as completed and the reconciliation described below. Support Management does see where a process
stands from the moment it starts, though, and - once it keeps the list this service sends - what the case worker can do
to move it on. See manual gates below.</p>

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
			<td class="code">alcohol-serving.bpmn</td>
			<td class="code">alcohol-serving</td>
			<td>Servering av alkoholdrycker</td>
		</tr>
		<tr>
			<td class="code">alcohol-serving-change.bpmn</td>
			<td class="code">alcohol-serving-change</td>
			<td>Förändring av befintligt serveringstillstånd</td>
		</tr>
		<tr>
			<td class="code">alcohol-serving-addition.bpmn</td>
			<td class="code">alcohol-serving-addition</td>
			<td>Tillägg av befintligt serveringstillstånd</td>
		</tr>
		<tr>
			<td class="code">tobacco-sales.bpmn</td>
			<td class="code">tobacco-sales</td>
			<td>Försäljning av tobaksvaror</td>
		</tr>
		<tr>
			<td class="code">tobacco-sales-change.bpmn</td>
			<td class="code">tobacco-sales-change</td>
			<td>Ändring av tillståndspliktig försäljning av tobaksvaror</td>
		</tr>
		<tr>
			<td class="code">tobacco-sales-closure.bpmn</td>
			<td class="code">tobacco-sales-closure</td>
			<td>Avslut av tillståndspliktig försäljning av tobaksvaror</td>
		</tr>
		<tr>
			<td class="code">e-cigarette-sales.bpmn</td>
			<td class="code">e-cigarette-sales</td>
			<td>Försäljning av elektroniska cigaretter och påfyllnadsbehållare</td>
		</tr>
		<tr>
			<td class="code">low-alcohol-beer-serving.bpmn</td>
			<td class="code">low-alcohol-beer-serving</td>
			<td>Servering av folköl, dvs. öl med högst 3,5 volymprocent alkohol</td>
		</tr>
		<tr>
			<td class="code">low-alcohol-beer-sales.bpmn</td>
			<td class="code">low-alcohol-beer-sales</td>
			<td>Försäljning av folköl, dvs. öl med högst 3,5 volymprocent alkohol</td>
		</tr>
		<tr>
			<td class="code">supervision.bpmn</td>
			<td class="code">supervision</td>
			<td>Tillsyn</td>
		</tr>
		<tr>
			<td class="code">reconciliation/process-reconciliation.bpmn</td>
			<td class="code">process-reconciliation</td>
			<td>The scheduler of the service, see below. Belongs to no errand, is not in <span class="code">PROCESS_KEYS</span>
			and cannot be started from an errand event</td>
		</tr>
	</tbody>
</table>

<p>A phase waits for a case worker by default. Each one holds a message catch event that the user interface opens.
The two folköl models are the exception. A notification of low-alcohol beer sale or serving needs no case worker before the
follow up, so Registration through Decision hold no wait state and the process passes straight through them. Only Follow
up and Closure keep their catch events, so that is where Support Management sees the errand stop.</p>

<p>The six phases are the same in every process, and stay that way. An errand moves through Registration, Review,
Investigation, Decision, Follow up and Closure whether it is a permit or a supervision; what separates one process from
another is what happens inside a phase and whether the phase waits for a case worker, as the folköl models show. The
work inside the phases is not described yet, so the models hold the phases and little else.</p>

<p>A process starts from an errand event that Support Management publishes with
<span class="code">startAllowed</span>. The supervision is the one started by hand: the Starta handläggning command of
the case worker arrives as an event with the sub type <span class="code">PROCESS</span>, and this service never reads
the sub type. A key it deploys, no instance already running and a permission given is all a start takes, so a manual
start and an automatic one follow the same path. <span class="code">ProcessWithoutDeviationIT</span> drives every key in
<span class="code">PROCESS_KEYS</span> from that event to the end of the process, and starts the supervision the way the
command arrives.</p>

<p>Which process an errand gets, and whether it starts by itself, is metadata on the label in Support Management:
<span class="code">processKey</span> and <span class="code">processStartMode</span>, set per environment through its
<span class="code">metadata/labels</span> API and not in this repository. The key has to be the exact string in the
table above. The supervision process is <span class="code">supervision</span>, not
<span class="code">alkt-tillsyn</span> as the solution document exemplifies with, and an event naming a key this service
does not deploy is answered with <span class="code">422</span>.</p>

<h3>Manual gates and awaiting signals</h3>

<p>A phase ends when a case worker says so. Support Management publishes that as an errand event with the sub type
<span class="code">SIGNAL</span> and a <span class="code">signalName</span>, and this service correlates a message of
that name against the process instance of the errand. Every other event correlates
<span class="code">errandUpdated</span> instead, which is how a process can be built to react to an attachment or a
change without a case worker pressing anything. A correlation that matches no wait state is answered with
<span class="code">202</span> and logged: the process was between two gates when the change arrived, and redelivering
the event would not change that.</p>

<p>Which gates a process stands at is reported back as <span class="code">awaitingSignals</span> on the process row. The
list is read from the message subscriptions of the instance in the engine, so a new gate is a change to the BPMN file
and nothing else. The name of the signal is the name of the message, the label is the <span class="code">name</span>
attribute of the catch event in the model, and <span class="code">errandUpdated</span> is left out - it is no button
anyone should see. A phase that waits only for that message is reported as <span class="code">WAITING</span> with an
empty list, and an activity the model does not name at all is reported with the name of the message as its label.</p>

<p>Support Management does not store the list yet. The field is hand-added to our copy of its specification ahead of
SM-A10 (see the comment on <span class="code">ErrandProcess.awaitingSignals</span> in
<span class="code">src/main/resources/integrations/support-management.yaml</span>), so today the list is sent and
dropped. Once SM-A10 lands, Support Management keeps the list and refuses a signal that is not in it.</p>

<p>Three reports carry the list: the one after a process starts, the one after a wakeup that moved it on, and the one
after a work step that did not end the process. Every other report replaces the list with an empty one, since the
process is then working rather than waiting. The activity of such a report is the phase rather than the catch event,
read from the model as the subprocess enclosing it.</p>

<p>The model of a process definition is read once through
<span class="code">GET /process-definition/{id}/xml</span> and kept in memory, keyed by definition id. A deployed
definition never changes, so the entries never go stale; they are gone at restart. If the model cannot be read the
report still goes out, with the name of the message as the label - a button with a technical name beats no button at
all.</p>

<p>What the code above needs from a model:</p>

<ul>
	<li>A manual gate is a named message catch event. No user tasks: the case worker works in Support Management and
	never logs into the engine, so a task list there would be one nobody opens.</li>
	<li>Several ways forward are an event-based gateway with one catch event per alternative, so that the choice of the
	case worker is also the choice of the process. A time limit belongs in the same gateway as a timer catch event.</li>
	<li>No <span class="code">asyncBefore</span> or <span class="code">asyncAfter</span> between a wakeup and the next
	wait state. The report is built from what the engine answers once the REST call returns, so a process still on its
	way somewhere looks like a process waiting for nothing, and no report goes out at all: Support Management keeps the
	row it already has until something else reports.</li>
	<li>The <span class="code">name</span> of a catch event is what the case worker reads on the button, and the
	outermost subprocess around it is the phase Support Management shows. A phase without a name is reported by its id
	alone, and a boundary event hangs on a phase rather than inside it, so it is reported under its own id.</li>
	<li>How many phases hold a gate is up to the model. The folköl models pass straight through their first four
	phases, so the first thing Support Management hears from them is that they wait at Follow up.</li>
</ul>

<p>Reports carry no sequence number, and a wait state is read after the task that led to it was completed. Two reports
about the same instance can therefore land out of order: a step completes, the engine parks the instance, and before
that <span class="code">WAITING</span> report is sent another thread has already reported the next step as
<span class="code">RUNNING</span>. The window is short and the next report corrects the row, since every report reads
the state of the instance anew rather than replaying a remembered one. It is worth knowing about before reading a row
that disagrees with Cockpit.</p>

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
			<td class="code">integration.operaton.url</td>
			<td>URL address to the Operaton rest api that process resources are deployed to. The same rest client is used
			when starting and updating process instances; the external task client's poll url is a separate setting</td>
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
			<td><strong>null</strong> (behaves as an empty list)</td>
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
	</thead>
	<tbody>
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
	</tbody>
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

<h3>Process reconciliation</h3>

<p>Support Management only learns the state of a process through reports, and reports come from work steps and from the
start and wakeup paths. Two things leave the row wrong for good: an incident (the engine gave up on a step, no worker
runs any more) and an instance that ended without a final report. The reconciliation is the net under both. Every run reports each incident in the tenant
as <span class="code">FAILED</span> with code <span class="code">INCIDENT</span>, unless the row already says so, and
settles each instance that ended within <span class="code">reconciliation.lookback</span> and whose row is still live:
<span class="code">COMPLETED</span> for an end the model chose, <span class="code">FAILED</span> with code
<span class="code">TERMINATED</span> for one cancelled from outside. An errand that is gone (404) is left alone.</p>

<p>It runs as a process of its own, <span class="code">process-reconciliation.bpmn</span>: a timer start event
(<span class="code">0 0/5 * * * ?</span>, every five minutes) followed by the external task
<span class="code">ReconcileProcessesTask</span>. That is what makes a run happen once even though the service runs in
two pods: the engine fires the timer once per cycle and locks the task for one worker. No database and no shedlock.
Each run leaves an instance in the history of the engine, kept for one day through
<span class="code">historyTimeToLive</span>, so history cleanup has to be on in Operaton. History level
<span class="code">audit</span> or above is needed, since the errand identity of an instance is read from its historic
variables.</p>

<p>Runs show up in Cockpit as instances of <span class="code">process-reconciliation</span>. A run that fails is
logged as an error and completed anyway: the next cycle is the retry, and an incident would only leave an instance
standing in the engine. The failure of a single errand never stops the rest of a run.</p>

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
			<td class="code">reconciliation.lookback</td>
			<td>How far back in the history of the engine each run looks for instances that ended. Every instance in the window
			costs a lookup in Support Management per run, so the window is kept short; an end further back than this while
			the service was down is not settled</td>
			<td><strong>PT2H</strong></td>
		</tr>
		<tr>
			<td class="code">reconciliation.worker.enabled</td>
			<td>Set to <strong>false</strong> to stop polling for the task. The timer keeps firing in the engine and the
			tasks wait there. The integration test profile uses it to keep the shared engine quiet</td>
			<td><strong>true</strong></td>
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
