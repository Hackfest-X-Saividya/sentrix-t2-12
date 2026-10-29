import React, { useCallback, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const scanApi = (path, options) => fetch(`/api/scan${path}`, options);
const watchApi = (path, options) => fetch(`/api/watch${path}`, options);
const remediateApi = (path, options) => fetch(`/api/remediate${path}`, options);

async function readResponse(response) {
  const raw = await response.text();
  let data = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch { data = { message: raw }; }
  if (!response.ok) {
    const detail = data.message || data.error || data.detail || raw;
    throw new Error(detail ? `HTTP ${response.status}: ${detail}` : `Request failed (HTTP ${response.status})`);
  }
  return data;
}

function Mark({ children }) { return <span className="mark">{children}</span>; }
function Dot({ state = "neutral" }) { return <span className={`dot dot-${state}`} aria-hidden="true" />; }
function Icon({ name }) {
  const common = { width: 18, height: 18, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.7, strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": true };
  const paths = {
    scan: <><path d="M4 7V4h3M17 4h3v3M20 17v3h-3M7 20H4v-3"/><circle cx="12" cy="12" r="4"/><path d="M12 8v8M8 12h8"/></>,
    pulse: <><path d="M3 12h4l3-7 4 14 3-7h4"/></>,
    fix: <><path d="M14 6a5 5 0 0 0-6.8 6.8L3 17l4 4 4.2-4.2A5 5 0 0 0 18 10l-3 3-4-4 3-3Z"/></>,
    activity: <><path d="M4 19V5M4 19h16"/><path d="m7 15 4-4 3 2 5-7"/></>,
    arrow: <><path d="M5 12h14M13 6l6 6-6 6"/></>,
    upload: <><path d="M12 16V4M7 9l5-5 5 5"/><path d="M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"/></>,
    refresh: <><path d="M20 7v5h-5"/><path d="M4 17v-5h5"/><path d="M5.5 9A7 7 0 0 1 18 6l2 6M4 12l2 6a7 7 0 0 0 12.5-3"/></>
  };
  return <svg {...common}>{paths[name] || paths.activity}</svg>;
}

function App() {
  const [active, setActive] = useState("overview");
  const [apiHealth, setApiHealth] = useState({ scan: "checking", watch: "checking", remediate: "checking" });
  const [file, setFile] = useState(null);
  const [scanning, setScanning] = useState(false);
  const [scanMessage, setScanMessage] = useState({ text: "Choose a Java project ZIP to begin.", kind: "neutral" });
  const [scan, setScan] = useState(null);
  const [findings, setFindings] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [events, setEvents] = useState([]);
  const [watchBusy, setWatchBusy] = useState(false);
  const [watchMessage, setWatchMessage] = useState({ text: "Waiting for event input.", kind: "neutral" });
  const [remBusy, setRemBusy] = useState(false);
  const [remMessage, setRemMessage] = useState({ text: "Fixture provider · No files will be changed.", kind: "neutral" });
  const [proposal, setProposal] = useState(null);
  const [verification, setVerification] = useState(null);

  const healthCheck = useCallback(async () => {
    const checks = [
      ["scan", () => scanApi("/health")],
      ["watch", () => watchApi("/health")],
      ["remediate", () => remediateApi("/health")]
    ];
    const next = {};
    await Promise.all(checks.map(async ([key, call]) => {
      try {
        const res = await call();
        next[key] = res.ok ? "online" : "offline";
      } catch { next[key] = "offline"; }
    }));
    setApiHealth(next);
  }, []);

  const refreshAlerts = useCallback(async (quiet = false) => {
    try {
      const data = await readResponse(await watchApi("/alerts?limit=10"));
      const rows = Array.isArray(data) ? data : Array.isArray(data.alerts) ? data.alerts : Array.isArray(data.content) ? data.content : [];
      setAlerts(rows);
      if (!quiet) setWatchMessage({ text: `${rows.length} alert(s) loaded from ZeroWatch.`, kind: "success" });
    } catch (error) {
      setWatchMessage({ text: `Could not load alerts: ${error.message}`, kind: "error" });
    }
  }, []);

  useEffect(() => {
    healthCheck();
    refreshAlerts(true);
    const timer = window.setInterval(healthCheck, 20000);
    return () => window.clearInterval(timer);
  }, [healthCheck, refreshAlerts]);

  const totalEvidence = useMemo(() => alerts.reduce((sum, alert) => sum + (alert.eventIds?.length || 0), 0), [alerts]);

  async function runScan(event) {
    event.preventDefault();
    if (!file) {
      setScanMessage({ text: "Choose a ZIP file first.", kind: "error" });
      return;
    }
    if (!file.name.toLowerCase().endsWith(".zip")) {
      setScanMessage({ text: "Only ZIP archives are supported.", kind: "error" });
      return;
    }
    setScanning(true);
    setScan(null);
    setFindings([]);
    setScanMessage({ text: "Uploading source and running static analysis…", kind: "neutral" });
    try {
      const form = new FormData();
      form.append("file", file);
      const data = await readResponse(await scanApi("/scans", { method: "POST", body: form }));
      setScan(data);
      let rows = data.findings;
      if (!Array.isArray(rows) && data.scanId) {
        const findingsData = await readResponse(await scanApi(`/scans/${encodeURIComponent(data.scanId)}/findings`));
        rows = Array.isArray(findingsData) ? findingsData : (findingsData.findings || findingsData.items || []);
      }
      if (rows && !Array.isArray(rows) && Array.isArray(rows.findings)) rows = rows.findings;
      setFindings(Array.isArray(rows) ? rows : []);
      setScanMessage({ text: "Scan finished. Treat findings as leads for human review.", kind: "success" });
      setActive("scan");
    } catch (error) {
      setScanMessage({ text: `Scan failed: ${error.message}`, kind: "error" });
    } finally {
      setScanning(false);
      healthCheck();
    }
  }

  async function runWatchDemo() {
    setWatchBusy(true);
    setEvents([]);
    setWatchMessage({ text: "Submitting three synthetic access-denied events…", kind: "neutral" });
    const results = [];
    const base = Date.now();
    try {
      for (let i = 0; i < 3; i++) {
        // Use a canonical UUID and timestamps in the recent past. The API accepts
        // only canonical event IDs and rejects events more than 5 minutes ahead.
        const payload = {
          eventId: crypto.randomUUID(),
          schemaVersion: "1.0",
          timestamp: new Date(base - (2 - i) * 30000).toISOString(),
          serviceName: "zerotrace-demo",
          eventType: "ACCESS_DENIED",
          requestId: `demo-request-${base}-${i}`,
          traceId: `demo-trace-${base}`,
          attributes: { actorRef: "actor-a1b2c3d4", resource: "admin-console" }
        };
        const result = await readResponse(await watchApi("/events", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        }));
        results.push(result);
        setEvents([...results]);
      }
      await refreshAlerts(true);
      setWatchMessage({ text: `Accepted ${results.length} of 3 synthetic events. Alerts refreshed from the API.`, kind: "success" });
    } catch (error) {
      setWatchMessage({ text: `Demo stopped after ${results.length}/3 event(s): ${error.message}. Accepted responses are retained below; alerts were refreshed.`, kind: "error" });
      await refreshAlerts(true);
      // Load persisted events too, so an accepted request is not hidden if a later
      // request in the burst fails.
      try {
        const saved = await readResponse(await watchApi("/events?limit=10"));
        if (Array.isArray(saved)) setEvents(saved.map(event => ({ event, alerts: [] })));
      } catch { /* Preserve the ingestion responses already received. */ }
    } finally {
      setWatchBusy(false);
      healthCheck();
    }
  }

  async function runRemediationDemo() {
    setRemBusy(true);
    setProposal(null);
    setVerification(null);
    setRemMessage({ text: "Requesting the predefined SQL-injection fixture…", kind: "neutral" });
    const id = `ui-demo-sqli-${Date.now()}`;
    const finding = {
      findingId: id,
      ruleId: "zerotrace.java.sql-injection",
      category: "sql-injection",
      severity: "HIGH",
      filePath: "UnsafeUserLookup.java",
      lineStart: 8,
      lineEnd: 8,
      evidence: ["Synthetic example: user-controlled input is concatenated into SQL."],
      explanation: "Demonstration finding for the predefined SQL-injection remediation fixture.",
      codeContext: `String sql = "SELECT * FROM users WHERE name = '" + userInput + "'";\nstatement.executeQuery(sql);`
    };
    try {
      const submitted = await readResponse(await remediateApi("/findings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(finding)
      }));
      const findingId = submitted.findingId || id;
      const proposalData = await readResponse(await remediateApi(`/findings/${encodeURIComponent(findingId)}/proposals`, { method: "POST" }));
      if (!proposalData.proposalId) throw new Error("The API accepted the finding but did not return a proposalId.");
      setProposal(proposalData);
      setRemMessage({ text: `Proposal generated · provider ${proposalData.providerMode || "unknown"} · not applied to source files.`, kind: "success" });
    } catch (error) {
      setRemMessage({ text: `Could not generate proposal: ${error.message}`, kind: "error" });
    } finally {
      setRemBusy(false);
      healthCheck();
    }
  }

  async function verifyProposal() {
    if (!proposal?.proposalId) return;
    setRemBusy(true);
    setVerification(null);
    setRemMessage({ text: "Running trusted synthetic fixture checks…", kind: "neutral" });
    try {
      const result = await readResponse(await remediateApi(`/proposals/${encodeURIComponent(proposal.proposalId)}/verify`, { method: "POST" }));
      setVerification(result);
      setRemMessage({ text: `Verification outcome: ${result.outcome || "returned"} · synthetic fixtures only.`, kind: "success" });
    } catch (error) {
      setRemMessage({ text: `Verification failed: ${error.message}`, kind: "error" });
    } finally {
      setRemBusy(false);
    }
  }

  const navItems = [
    { id: "overview", label: "Overview", icon: "activity" },
    { id: "scan", label: "Source scan", icon: "scan" },
    { id: "watch", label: "ZeroWatch", icon: "pulse" },
    { id: "remediate", label: "ZeroRemediate", icon: "fix" }
  ];

  return <div className="app-shell">
    <aside className="rail">
      <a className="wordmark" href="#overview" onClick={() => setActive("overview")} aria-label="ZeroTrace AI overview">
        <span className="wordmark-symbol">ZT<span>/</span></span>
        <span className="wordmark-name">ZeroTrace<small>SECURITY WORKBENCH</small></span>
      </a>
      <div className="rail-label">WORKSPACE</div>
      <nav className="nav-list" aria-label="Main navigation">
        {navItems.map(item => <button key={item.id} className={`nav-item ${active === item.id ? "active" : ""}`} onClick={() => setActive(item.id)}>
          <Icon name={item.icon}/><span>{item.label}</span>
          {item.id === "watch" && alerts.length > 0 && <span className="nav-count">{alerts.length}</span>}
        </button>)}
      </nav>
      <div className="rail-bottom">
        <div className="rail-label">LOCAL SERVICES</div>
        {[
          ["scan", "Source analysis", "8080"],
          ["watch", "Event monitor", "8081"],
          ["remediate", "Remediation API", "8083"]
        ].map(([key, label, port]) => <div className="service-row" key={key}>
          <Dot state={apiHealth[key] === "online" ? "online" : apiHealth[key] === "offline" ? "offline" : "neutral"}/>
          <span>{label}</span><code>{port}</code>
        </div>)}
        <p className="rail-note">Local prototype · Data resets when services restart.</p>
      </div>
    </aside>

    <main className="main-area">
      <header className="topbar">
        <div><span className="crumb">WORKSPACE</span><span className="crumb-slash">/</span><strong>{navItems.find(x => x.id === active)?.label}</strong></div>
        <div className="topbar-right"><span className="local-tag"><Dot state="online"/> LOCAL ENVIRONMENT</span><button className="icon-button" onClick={() => { healthCheck(); refreshAlerts(); }} title="Refresh service status"><Icon name="refresh"/></button></div>
      </header>

      {active === "overview" && <section className="page overview-page">
        <div className="intro-grid">
          <div className="intro-copy">
            <div className="eyebrow"><span className="eyebrow-rule"/>APPLICATION SECURITY / 01—03</div>
            <h1>See the signal.<br/><em>Follow the evidence.</em></h1>
            <p className="lede">Inspect Java source for risky patterns, correlate runtime events, and review proposed fixes from one local workbench.</p>
            <div className="intro-actions"><button className="button button-primary" onClick={() => setActive("scan")}>Open source scan <Icon name="arrow"/></button><span className="microcopy">Built for review, not blind trust.</span></div>
          </div>
          <div className="incident-panel">
            <div className="panel-kicker">OPEN RUNTIME ALERTS <span className="live-mark"><Dot state="online"/> LIVE QUERY</span></div>
            <div className="incident-number">{String(alerts.length).padStart(2, "0")}</div>
            <div className="incident-caption">{alerts.length === 1 ? "alert requires review" : "alerts require review"}</div>
            <div className="incident-divider"/>
            <div className="incident-meta"><span>Evidence events</span><strong>{totalEvidence}</strong></div>
            <div className="incident-meta"><span>Highest visible severity</span><strong>{alerts.some(a => a.severity === "CRITICAL") ? "CRITICAL" : alerts.some(a => a.severity === "HIGH") ? "HIGH" : alerts.some(a => a.severity === "MEDIUM") ? "MEDIUM" : "—"}</strong></div>
            <button className="text-link" onClick={() => setActive("watch")}>Inspect ZeroWatch alerts <Icon name="arrow"/></button>
          </div>
        </div>

        <div className="section-heading"><div><span className="section-index">01</span><h2>Three stages, one review trail</h2></div><p>Each stage adds context. None replaces an analyst's judgement.</p></div>
        <div className="stage-list">
          <button className="stage-row" onClick={() => setActive("scan")}>
            <span className="stage-icon"><Icon name="scan"/></span><span className="stage-num">01</span>
            <span className="stage-main"><strong>Source scan</strong><small>Upload a Java ZIP and inspect Semgrep findings.</small></span>
            <span className="stage-status"><Dot state={apiHealth.scan === "online" ? "online" : "offline"}/>{apiHealth.scan === "online" ? "API reachable" : "Check API"}</span><Icon name="arrow"/>
          </button>
          <button className="stage-row" onClick={() => setActive("watch")}>
            <span className="stage-icon"><Icon name="pulse"/></span><span className="stage-num">02</span>
            <span className="stage-main"><strong>ZeroWatch</strong><small>Correlate synthetic runtime events against explicit rules.</small></span>
            <span className="stage-status"><Dot state={apiHealth.watch === "online" ? "online" : "offline"}/>{apiHealth.watch === "online" ? "API reachable" : "Check API"}</span><Icon name="arrow"/>
          </button>
          <button className="stage-row" onClick={() => setActive("remediate")}>
            <span className="stage-icon"><Icon name="fix"/></span><span className="stage-num">03</span>
            <span className="stage-main"><strong>ZeroRemediate</strong><small>Review a fixture proposal and run bounded synthetic checks.</small></span>
            <span className="stage-status"><Dot state={apiHealth.remediate === "online" ? "online" : "offline"}/>{apiHealth.remediate === "online" ? "API reachable" : "Check API"}</span><Icon name="arrow"/>
          </button>
        </div>
        <div className="note-strip"><span className="note-mark">NOTE</span><p>This prototype uses local services and in-memory stores. Runtime events in this workspace are synthetic; remediation proposals are not applied to uploaded code.</p></div>
      </section>}

      {active === "scan" && <section className="page">
        <div className="page-head">
          <div><div className="eyebrow"><span className="eyebrow-rule"/>PHASE 01 / STATIC ANALYSIS</div><h1>Source scan</h1><p className="lede">Upload a ZIP of Java source. Findings are review signals, not proof of exploitability.</p></div>
          <div className="head-side"><span className="label-small">ENGINE</span><strong>{scan?.engine || "Semgrep"}</strong><span className="muted">Java ruleset</span></div>
        </div>
        <div className="scan-layout">
          <form className="upload-workspace" onSubmit={runScan}>
            <div className="block-heading"><span>01</span><h2>Submit a project</h2></div>
            <label className="dropzone">
              <Icon name="upload"/><strong>{file ? file.name : "Choose project archive"}</strong>
              <span>{file ? `${(file.size / 1024).toFixed(1)} KB · ready to scan` : "ZIP format · Java source files"}</span>
              <input type="file" accept=".zip,application/zip" onChange={e => setFile(e.target.files?.[0] || null)}/>
            </label>
            <button className="button button-primary full" type="submit" disabled={scanning || !file}>{scanning ? "Scanning source…" : "Run security scan"} <Icon name="arrow"/></button>
            <p className={`message ${scanMessage.kind}`} role="status">{scanMessage.text}</p>
            <div className="process-line"><span>ZIP validation</span><i/><span>Semgrep</span><i/><span>Finding review</span></div>
          </form>
          <div className="scan-summary">
            <div className="block-heading"><span>02</span><h2>Latest scan</h2></div>
            <div className="summary-hero"><span className="label-small">STATUS</span><strong>{scan?.status || "—"}</strong><span className="summary-sub">{scan?.scanId ? `ID ${scan.scanId}` : "No scan submitted in this session"}</span></div>
            <div className="summary-stats"><div><strong>{scan?.filesAnalyzed ?? "—"}</strong><span>Files analyzed</span></div><div><strong>{scan?.findingsCount ?? (scan ? findings.length : "—")}</strong><span>Findings</span></div></div>
            <div className="small-note">Review source-to-sink evidence and confirm whether input is attacker-controlled before treating a result as a vulnerability.</div>
          </div>
        </div>
        <div className="section-heading compact"><div><span className="section-index">03</span><h2>Findings register</h2></div><p>{findings.length} result(s) in current scan</p></div>
        <div className="table-shell"><div className="table-scroll"><table><thead><tr><th>Rule / ID</th><th>Status</th><th>Severity</th><th>Location</th><th>Details</th></tr></thead><tbody>
          {findings.length ? findings.map((f, i) => <tr key={f.id || `${f.ruleId}-${i}`}><td className="mono">{f.ruleId || f.id || "—"}</td><td><span className="status-text">{f.status || "Review"}</span></td><td><span className={`severity severity-${String(f.severity || "info").toLowerCase()}`}>{f.severity || "—"}</span></td><td className="mono">{f.file || "—"}{f.startLine ? `:${f.startLine}` : ""}</td><td>{f.message || f.recommendation || f.remediation || f.description || "Review finding details."}</td></tr>) : <tr><td colSpan="5" className="empty-cell">No findings loaded. Run a scan to populate this register.</td></tr>}
        </tbody></table></div></div>
      </section>}

      {active === "watch" && <section className="page">
        <div className="page-head watch-head">
          <div><div className="eyebrow"><span className="eyebrow-rule"/>PHASE 02 / EVENT CORRELATION</div><h1>ZeroWatch</h1><p className="lede">A small, explicit rule set over structured events. No agent is attached to a live application in this demo.</p></div>
          <div className="rule-stamp"><span>ACTIVE RULE</span><strong>ACCESS DENIED BURST</strong><code>zerowatch.access-denied-burst</code></div>
        </div>
        <div className="watch-toolbar"><div><span className="label-small">DEMO INPUT</span><p>Three denials · same pseudonymous actor · same resource · 30 seconds apart</p></div><button className="button button-primary" onClick={runWatchDemo} disabled={watchBusy}>{watchBusy ? "Submitting events…" : "Generate event burst"} <Icon name="arrow"/></button><button className="button button-quiet" onClick={() => refreshAlerts()}><Icon name="refresh"/> Refresh alerts</button></div>
        <p className={`message ${watchMessage.kind}`} role="status">{watchMessage.text}</p>
        <div className="watch-metrics"><div><span>OPEN ALERTS</span><strong>{alerts.filter(a => a.status === "OPEN").length}</strong></div><div><span>EVENTS IN ALERTS</span><strong>{totalEvidence}</strong></div><div><span>RULE WINDOW</span><strong>05:00</strong></div><div><span>INPUT MODE</span><strong className="accent-text">SYNTHETIC</strong></div></div>
        <div className="section-heading compact"><div><span className="section-index">01</span><h2>Alert register</h2></div><p>Latest 10 alerts · refreshed from API</p></div>
        <div className="table-shell"><div className="table-scroll"><table><thead><tr><th>Severity</th><th>Rule</th><th>Explanation</th><th>Evidence IDs</th><th>Status</th></tr></thead><tbody>
          {alerts.length ? alerts.map(a => <tr key={a.alertId}><td><span className={`severity severity-${String(a.severity || "info").toLowerCase()}`}>{a.severity || "—"}</span></td><td className="mono">{a.ruleId}</td><td>{a.explanation}</td><td className="mono evidence-list">{(a.eventIds || []).map(id => <span key={id}>{id}</span>)}</td><td><span className={`state-label ${String(a.status).toLowerCase()}`}>{a.status || "—"}</span></td></tr>) : <tr><td colSpan="5" className="empty-cell">No alerts returned. Generate a synthetic event burst to exercise the rule.</td></tr>}
        </tbody></table></div></div>
        <div className="section-heading compact"><div><span className="section-index">02</span><h2>Ingestion trace</h2></div><p>{events.length} event response(s) from current demo</p></div>
        <div className="event-trace">
          {events.length ? events.map((item, i) => <div className="trace-row" key={item.event?.eventId || i}><span className="trace-index">0{i + 1}</span><div className="trace-main"><strong>{item.event?.eventType || "Runtime event"}</strong><span>{item.event?.eventId || "—"}</span></div><span className={`trace-result ${(item.alerts || []).length ? "triggered" : ""}`}>{(item.alerts || []).length ? `${item.alerts.length} alert generated` : "Accepted · no alert"}</span></div>) : <div className="empty-cell">Event responses appear here after you run the demo.</div>}
        </div>
        <div className="note-strip"><span className="note-mark">SCOPE</span><p>Alerting is deterministic and rule-based. It demonstrates event validation and correlation, not universal attack detection or real-world instrumentation.</p></div>
      </section>}

      {active === "remediate" && <section className="page">
        <div className="page-head">
          <div><div className="eyebrow"><span className="eyebrow-rule"/>PHASE 03 / REVIEWABLE PROPOSAL</div><h1>ZeroRemediate</h1><p className="lede">Inspect a predefined SQL-injection fix and verify two trusted synthetic checks. The proposal is never applied automatically.</p></div>
          <div className="provider-stamp"><span>PROVIDER MODE</span><strong>FIXTURE</strong><small>Deterministic example</small></div>
        </div>
        <div className="remediation-action"><div><span className="label-small">DEMONSTRATION CASE</span><h2>SQL query concatenates untrusted input</h2><p>Replace string concatenation with a bound parameter.</p></div><button className="button button-primary" onClick={runRemediationDemo} disabled={remBusy}>{remBusy ? "Generating proposal…" : "Generate proposal"} <Icon name="arrow"/></button></div>
        <p className={`message ${remMessage.kind}`} role="status">{remMessage.text}</p>
        {proposal && <div className="proposal-layout">
          <div className="proposal-explanation">
            <div className="block-heading"><span>01</span><h2>Root cause</h2></div><p>{proposal.rootCause || "Untrusted input is concatenated into SQL syntax."}</p>
            <div className="block-heading"><span>02</span><h2>Recommended change</h2></div><p>{proposal.remediation || "Bind the input as a query parameter so it is treated as data."}</p>
            <div className="small-note">Proposal ID <code>{proposal.proposalId}</code><br/>Finding ID <code>{proposal.findingId}</code></div>
          </div>
          <div className="code-panel"><div className="code-panel-head"><span>PROPOSED JAVA</span><span>NOT APPLIED</span></div><pre>{proposal.proposedSnippet || "No proposed snippet returned."}</pre><div className="code-panel-head diff-head"><span>UNIFIED DIFF</span><span>REVIEW BEFORE USE</span></div><pre>{proposal.unifiedDiff || "No diff returned."}</pre></div>
          <div className="verification-panel"><div><span className="label-small">CONTROLLED VERIFICATION</span><h2>Run the synthetic checks</h2><p>These checks do not compile or test the uploaded repository.</p></div><button className="button button-primary" onClick={verifyProposal} disabled={remBusy}>{remBusy ? "Verifying…" : "Verify fixtures"} <Icon name="arrow"/></button></div>
          {verification && <div className="verification-result"><div className="verification-top"><span>VERIFICATION RESULT</span><strong>{verification.outcome || "Recorded"}</strong></div><pre>{JSON.stringify(verification, null, 2)}</pre></div>}
        </div>}
        {!proposal && <div className="empty-remediation"><span className="empty-number">03</span><div><strong>No proposal in this session</strong><p>Generate the fixture proposal to inspect the root cause, suggested code and diff.</p></div></div>}
        <div className="note-strip"><span className="note-mark">BOUNDARY</span><p>The current provider is a deterministic fixture, not a live AI model. Verification means the named synthetic checks passed; it does not establish that a real repository vulnerability is fixed.</p></div>
      </section>}

      <footer className="footer"><span>ZEROTRACE AI</span><span>LOCAL PROTOTYPE / HUMAN REVIEW REQUIRED</span><span>BUILD 0.1</span></footer>
    </main>
  </div>;
}

createRoot(document.getElementById("root")).render(<App />);