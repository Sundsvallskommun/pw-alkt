# PwAlkt

<p>Alkt hosts the process models of the alkohol- och tobaksverksamheten and drives them in the Operaton process engine.
Support Management owns the errand, this service owns the process behind it.</p>

<p>The service is a skeleton. The API, the engine integration, the reporting and the test harness are in place, but the
process models are phase structures with almost nothing inside them. The only work step so far is the one that reports a
process as completed, plus the reconciliation described at the end.</p>

<h3>The dialogue with Support Management</h3>

<p>Two conversations in opposite directions. Support Management tells this service that something happened to an errand.
This service tells Support Management where the process now stands. Neither is the answer to the other: the errand event
is answered with <span class="code">202</span> and an empty body.</p>

<pre>
Support Management                    pw-alkt                       Operaton
        |                                |                              |
        |  1. POST .../process/errand-events                            |
        |------------------------------->|                              |
        |                                |  2. start or correlate       |
        |                                |----------------------------->|
        |                                |  3. subscriptions + model    |
        |                                |&lt;-----------------------------|
        |  4. PUT .../processes/{processInstanceId}                     |
        |&lt;-------------------------------|                              |
        |  202 Accepted                  |                              |
        |&lt;-------------------------------|                              |
        |                                |                              |
        |                                |  5. external task polling    |
        |                                |&lt;---------------------------->|
        |  6. PUT .../processes/{processInstanceId}                     |
        |&lt;-------------------------------|                              |
</pre>

<p><strong>Inbound</strong> there is one endpoint,
<span class="code">POST /{municipalityId}/{namespace}/process/errand-events</span>. What it does is decided by the body,
not by the path. <span class="code">DELETE</span> removes every instance of the errand. Anything else starts a process
when none is running, and otherwise correlates a message: the <span class="code">signalName</span> when
<span class="code">eventSubType</span> is <span class="code">SIGNAL</span>, and
<span class="code">errandUpdated</span> in every other case. Only <span class="code">eventId</span> and
<span class="code">eventType</span> are required, so the rest is checked at runtime and a nothing-to-do event is
accepted as well. A key this service does not deploy is answered with <span class="code">422</span>.</p>

<p><strong>Outbound</strong> the state goes to
<span class="code">PUT /{municipalityId}/{namespace}/errands/{errandId}/processes/{processInstanceId}</span>, which
creates the row on the first report and updates it after that. The body carries
<span class="code">processStatus</span> (<span class="code">RUNNING</span>, <span class="code">WAITING</span>,
<span class="code">RETRYING</span>, <span class="code">COMPLETED</span> or <span class="code">FAILED</span>), the
current activity, the signals the process waits for, and any error. Reports come from four places:</p>

<table class="settings">
	<thead>
		<tr>
			<th>Trigger</th>
			<th>Reports</th>
		</tr>
	</thead>
	<tbody>
		<tr>
			<td>A process is started</td>
			<td><span class="code">WAITING</span>, if the instance parked on a gate rather than on a work step</td>
		</tr>
		<tr>
			<td>A correlation moved a process on</td>
			<td><span class="code">WAITING</span>, on the instance the message actually reached</td>
		</tr>
		<tr>
			<td>A work step runs</td>
			<td><span class="code">RUNNING</span> before it, whatever the step returned after it, then
			<span class="code">WAITING</span> unless the process ended. A step that throws reports
			<span class="code">RETRYING</span>, or <span class="code">FAILED</span> once the retries are spent</td>
		</tr>
		<tr>
			<td>The reconciliation</td>
			<td>Only what the work steps could not, see below</td>
		</tr>
	</tbody>
</table>

<p>Nothing compares against the row before writing it, except the reconciliation. A retried step reports
<span class="code">RUNNING</span> again, and idempotence at the receiver is cheaper than a read before every write.
Today a full six-gate process is eight reports over its whole life, since the models hold almost no work steps yet.</p>

<p>Reports carry no sequence number, and a wait state is read after the task that led to it was completed. Two reports
about one instance can therefore land out of order. The window is short and the next report corrects the row, because
every report reads the engine anew instead of replaying something remembered. Worth knowing before trusting a row that
disagrees with Cockpit.</p>

<h3>Process definitions</h3>

<p>Every model in <span class="code">src/main/resources/processmodels</span> is deployed to the tenant
<span class="code">ALKT</span> at startup, so a new schema is picked up by adding the file. The
<span class="code">id</span> of the <span class="code">bpmn:process</span> element is the process key, and it has a
matching constant in <span class="code">se.sundsvall.alkt.Constants</span>.</p>

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
			<td class="code">external-inspection.bpmn</td>
			<td class="code">external-inspection</td>
			<td>Yttre tillsyn, dvs. tillsyn på serveringsstället</td>
		</tr>
		<tr>
			<td class="code">internal-inspection.bpmn</td>
			<td class="code">internal-inspection</td>
			<td>Inre tillsyn, dvs. prövning av tillståndshavarens lämplighet</td>
		</tr>
		<tr>
			<td class="code">reconciliation/process-reconciliation.bpmn</td>
			<td class="code">process-reconciliation</td>
			<td>The scheduler of the service, see below. Belongs to no errand, is not in <span class="code">PROCESS_KEYS</span>
			and cannot be started from an errand event</td>
		</tr>
	</tbody>
</table>

<p>All eleven errand processes run the same six phases: Registration, Review, Investigation, Decision, Follow up and
Closure. What differs is what happens inside a phase and whether the phase waits for a case worker. The two folköl
models are the only ones that do not wait early: a notification needs no case worker before the follow up, so their
first four phases pass straight through and Support Management first hears from them at Follow up.</p>

<p>Which process an errand gets, and whether it starts by itself, is metadata on the label in Support Management
(<span class="code">processKey</span> and <span class="code">processStartMode</span>), set per environment through its
<span class="code">metadata/labels</span> API and not in this repository. The key has to be the exact string in the
table above. Tillsyn is two keys, <span class="code">external-inspection</span> and
<span class="code">internal-inspection</span>, and neither is <span class="code">alkt-tillsyn</span> as the solution
document exemplifies with.</p>

<p>The two inspections are the ones a case worker starts by hand. Starta handläggning arrives as an event with the sub
type <span class="code">PROCESS</span>, which this service never reads, so a manual start and an automatic one follow
the same path.</p>

<h3>Manual gates and awaiting signals</h3>

<p>A phase ends when a case worker says so. Support Management publishes that as a
<span class="code">SIGNAL</span> event, and this service correlates a message of that name against the instance. A
correlation that matches no wait state is answered with <span class="code">202</span> and logged, since the process was
between two gates when the change arrived and redelivering would not help.</p>

<p><strong>An errand runs one process at a time.</strong> Nothing in this service enforces that. All eleven models
declare the same message names, and every correlation is made on the errand id, so two live processes on one errand
make every signal match two executions at once. Operaton answers that with <span class="code">400</span> and the
signal is lost. Starting a second process on an errand that already has one is therefore a mistake, and the place it
can happen is the label metadata in Support Management.</p>

<p>The gates an instance stands at are reported as <span class="code">awaitingSignals</span>. The list is read from the
message subscriptions in the engine, so adding a gate is a change to the BPMN file and nothing else. The signal name is
the message name and the label is the <span class="code">name</span> attribute of the catch event.
<span class="code">errandUpdated</span> is left out, because it is no button anyone should see. The activity reported
alongside is the phase, read from the model as the subprocess enclosing the gate.</p>

<p>The model of a definition is read once through <span class="code">GET /process-definition/{id}/xml</span> and kept
in memory per pod, keyed by definition id. A deployed definition never changes, so entries never go stale and nothing
is evicted. If the model cannot be read the report still goes out with the message name as the label, because a button
with a technical name beats no button.</p>

<p><strong>Support Management stores the list.</strong> Every report replaces it whole, so a report that leaves the
field out says the process waits for no one. Support Management reads no meaning into the names: it hands them back to
the user interface as buttons, and compares them exactly, case included. A process that has ended shows none, whichever
way it ended. A name longer than 128 characters is refused, so
<span class="code">AwaitingSignal</span> cuts it there.</p>

<p>Pressing a button posts to
<span class="code">POST /errands/{errandId}/processes/{processInstanceId}/signals</span>, which reaches this service as
a <span class="code">SIGNAL</span> event carrying the message name. Support Management does not consume the signal, so
the same name is accepted again until the next report closes the gate. A second press correlates against nothing and is
answered with <span class="code">202</span>.</p>

<p>What this needs from a model:</p>

<ul>
	<li>A gate is a named message catch event. No user tasks, since the case worker never logs into the engine.</li>
	<li>Several ways forward are an event-based gateway with one catch event per alternative. A time limit belongs in
	the same gateway as a timer catch event.</li>
	<li>No <span class="code">asyncBefore</span> or <span class="code">asyncAfter</span> between a wakeup and the next
	wait state. The report is built from what the engine answers once the REST call returns, so a process still on its
	way looks like one waiting for nothing and no report goes out at all.</li>
	<li>The <span class="code">name</span> of a catch event is the button text, and the outermost subprocess around it
	is the phase. A phase without a name is reported by its id, and a boundary event hangs on a phase rather than inside
	it, so it is reported under its own id.</li>
</ul>

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

<p>Two things leave a process row wrong for good: an incident, where the engine gave up on a step and no worker runs any
more, and an instance that ended without a final report. The reconciliation is the net under both. Each run reports
every incident in the tenant as <span class="code">FAILED</span> with code <span class="code">INCIDENT</span>, and
settles each instance that ended within <span class="code">reconciliation.lookback</span> whose row is still live:
<span class="code">COMPLETED</span> for an end the model chose, <span class="code">FAILED</span> with code
<span class="code">TERMINATED</span> for one cancelled from outside. Rows that already say so are skipped, and an errand
that is gone (404) is left alone.</p>

<p>It runs as a process of its own, <span class="code">process-reconciliation.bpmn</span>: a timer start event every
five minutes followed by the external task <span class="code">ReconcileProcessesTask</span>. That is what makes a run
happen once even though the service runs in two pods, since the engine fires the timer once per cycle and locks the task
for one worker. No database and no shedlock. Each run leaves an instance in the engine history, kept for one day, so
history cleanup has to be on in Operaton. The history level has to be <span class="code">audit</span> or above, since
the errand identity of an instance is read from its historic variables.</p>

<p>Runs show up in Cockpit as instances of <span class="code">process-reconciliation</span>. A run that fails is logged
and completed anyway, because the next cycle is the retry and an incident would only leave an instance standing. One
failing errand never stops the rest of a run.</p>

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
