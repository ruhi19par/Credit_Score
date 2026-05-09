import { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BadgeCheck,
  BarChart3,
  ClipboardList,
  FileSearch,
  RefreshCw,
  Save,
  Send,
} from "lucide-react";
import "./styles.css";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

const initialForm = {
  fullName: "",
  employmentType: "SALARIED",
  monthlyIncome: "80000",
  monthlyExpenses: "25000",
  existingDebtPayment: "8000",
  repaymentHistory: "GOOD",
  incomeStability: "STABLE",
  requestedAmount: "300000",
  tenureMonths: "24",
};

const statuses = ["SUBMITTED", "SCORED", "UNDER_REVIEW", "APPROVED", "REJECTED"];

function App() {
  const [form, setForm] = useState(initialForm);
  const [applications, setApplications] = useState([]);
  const [score, setScore] = useState(null);
  const [report, setReport] = useState(null);
  const [selectedApplicationId, setSelectedApplicationId] = useState("");
  const [activeTab, setActiveTab] = useState("apply");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    loadApplications();
  }, []);

  const selectedApplication = useMemo(
    () => applications.find((application) => String(application.id) === String(selectedApplicationId)),
    [applications, selectedApplicationId],
  );

  async function request(path, options = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
      ...options,
    });

    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    if (!response.ok) {
      throw new Error(body?.message ?? "Request failed");
    }

    return body;
  }

  async function loadApplications() {
    try {
      const data = await request("/api/applications");
      setApplications(data);
      if (!selectedApplicationId && data.length > 0) {
        setSelectedApplicationId(String(data[0].id));
      }
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function submitApplication(event) {
    event.preventDefault();
    setLoading(true);
    setMessage("");

    try {
      const payload = {
        ...form,
        monthlyIncome: Number(form.monthlyIncome),
        monthlyExpenses: Number(form.monthlyExpenses),
        existingDebtPayment: Number(form.existingDebtPayment),
        requestedAmount: Number(form.requestedAmount),
        tenureMonths: Number(form.tenureMonths),
      };

      const result = await request("/api/applications/basic", {
        method: "POST",
        body: JSON.stringify(payload),
      });

      setScore(result);
      setSelectedApplicationId(String(result.applicationId));
      setMessage("Application submitted and scored.");
      await loadApplications();
      await loadReport(result.applicationId);
      setActiveTab("report");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadReport(applicationId = selectedApplicationId) {
    if (!applicationId) {
      setMessage("Select an application first.");
      return;
    }

    setLoading(true);
    setMessage("");

    try {
      const data = await request(`/api/reports/${applicationId}`);
      setReport(data);
      setActiveTab("report");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function updateStatus(applicationId, status) {
    setLoading(true);
    setMessage("");

    try {
      await request(`/api/applications/${applicationId}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status }),
      });
      setMessage("Application status updated.");
      await loadApplications();
      if (report?.applicationId === applicationId) {
        await loadReport(applicationId);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">
            <BadgeCheck size={22} aria-hidden="true" />
          </span>
          <div>
            <h1>CredBridge AI</h1>
            <p>Basic credit scoring workspace</p>
          </div>
        </div>
        <button className="icon-button" type="button" onClick={loadApplications} title="Refresh applications">
          <RefreshCw size={18} aria-hidden="true" />
        </button>
      </header>

      <nav className="tabs" aria-label="Workspace views">
        <button className={activeTab === "apply" ? "active" : ""} type="button" onClick={() => setActiveTab("apply")}>
          <Send size={17} aria-hidden="true" />
          Apply
        </button>
        <button className={activeTab === "applications" ? "active" : ""} type="button" onClick={() => setActiveTab("applications")}>
          <ClipboardList size={17} aria-hidden="true" />
          Applications
        </button>
        <button className={activeTab === "report" ? "active" : ""} type="button" onClick={() => setActiveTab("report")}>
          <FileSearch size={17} aria-hidden="true" />
          Report
        </button>
      </nav>

      {message && <div className="notice">{message}</div>}

      {activeTab === "apply" && (
        <section className="workspace two-column">
          <form className="panel form-grid" onSubmit={submitApplication}>
            <div className="panel-heading">
              <h2>Basic Application</h2>
              <button className="primary-button" type="submit" disabled={loading}>
                <Save size={17} aria-hidden="true" />
                Submit
              </button>
            </div>

            <label>
              Full name
              <input value={form.fullName} onChange={(event) => updateField("fullName", event.target.value)} required />
            </label>
            <label>
              Employment
              <select value={form.employmentType} onChange={(event) => updateField("employmentType", event.target.value)}>
                <option>SALARIED</option>
                <option>SELF_EMPLOYED</option>
                <option>BUSINESS</option>
                <option>STUDENT</option>
                <option>UNEMPLOYED</option>
              </select>
            </label>
            <NumberField label="Monthly income" field="monthlyIncome" form={form} onChange={updateField} />
            <NumberField label="Monthly expenses" field="monthlyExpenses" form={form} onChange={updateField} />
            <NumberField label="Existing debt payment" field="existingDebtPayment" form={form} onChange={updateField} />
            <NumberField label="Requested amount" field="requestedAmount" form={form} onChange={updateField} />
            <NumberField label="Tenure months" field="tenureMonths" form={form} onChange={updateField} />
            <label>
              Repayment history
              <select value={form.repaymentHistory} onChange={(event) => updateField("repaymentHistory", event.target.value)}>
                <option>EXCELLENT</option>
                <option>GOOD</option>
                <option>AVERAGE</option>
                <option>POOR</option>
                <option>NONE</option>
              </select>
            </label>
            <label>
              Income stability
              <select value={form.incomeStability} onChange={(event) => updateField("incomeStability", event.target.value)}>
                <option>STABLE</option>
                <option>MODERATE</option>
                <option>UNSTABLE</option>
              </select>
            </label>
          </form>

          <ScorePanel score={score} />
        </section>
      )}

      {activeTab === "applications" && (
        <section className="workspace">
          <div className="panel">
            <div className="panel-heading">
              <h2>Applications</h2>
              <span className="count">{applications.length}</span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Name</th>
                    <th>Amount</th>
                    <th>Tenure</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {applications.map((application) => (
                    <tr key={application.id}>
                      <td>#{application.id}</td>
                      <td>{application.fullName}</td>
                      <td>{money(application.requestedAmount)}</td>
                      <td>{application.tenureMonths} mo</td>
                      <td>
                        <select
                          className="status-select"
                          value={application.status}
                          onChange={(event) => updateStatus(application.id, event.target.value)}
                        >
                          {statuses.map((status) => (
                            <option key={status}>{status}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <button
                          className="icon-button"
                          type="button"
                          title="Open report"
                          onClick={() => {
                            setSelectedApplicationId(String(application.id));
                            loadReport(application.id);
                          }}
                        >
                          <FileSearch size={17} aria-hidden="true" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      )}

      {activeTab === "report" && (
        <section className="workspace two-column">
          <div className="panel">
            <div className="panel-heading">
              <h2>Report Lookup</h2>
              <button className="primary-button" type="button" onClick={() => loadReport()} disabled={loading}>
                <FileSearch size={17} aria-hidden="true" />
                Load
              </button>
            </div>
            <label>
              Application
              <select value={selectedApplicationId} onChange={(event) => setSelectedApplicationId(event.target.value)}>
                {applications.map((application) => (
                  <option key={application.id} value={application.id}>
                    #{application.id} {application.fullName}
                  </option>
                ))}
              </select>
            </label>
            {selectedApplication && (
              <dl className="details">
                <div>
                  <dt>Status</dt>
                  <dd>{selectedApplication.status}</dd>
                </div>
                <div>
                  <dt>Requested</dt>
                  <dd>{money(selectedApplication.requestedAmount)}</dd>
                </div>
                <div>
                  <dt>Tenure</dt>
                  <dd>{selectedApplication.tenureMonths} months</dd>
                </div>
              </dl>
            )}
          </div>

          <ReportPanel report={report} />
        </section>
      )}
    </main>
  );
}

function NumberField({ label, field, form, onChange }) {
  return (
    <label>
      {label}
      <input
        type="number"
        min="0"
        step="0.01"
        value={form[field]}
        onChange={(event) => onChange(field, event.target.value)}
        required
      />
    </label>
  );
}

function ScorePanel({ score }) {
  return (
    <aside className="panel score-panel">
      <div className="panel-heading">
        <h2>Score Result</h2>
        <BarChart3 size={20} aria-hidden="true" />
      </div>
      {!score ? (
        <p className="empty">Submit a Basic Mode application to generate the first score.</p>
      ) : (
        <>
          <div className={`score-ring risk-${score.riskLevel.toLowerCase()}`}>
            <strong>{score.score}</strong>
            <span>{score.riskLevel} risk</span>
          </div>
          <MetricGrid
            metrics={[
              ["DTI", `${score.debtToIncomeRatio}%`],
              ["Expenses", `${score.expenseRatio}%`],
              ["Capacity", money(score.repaymentCapacity)],
              ["Limit", money(score.suggestedLoanLimit)],
            ]}
          />
        </>
      )}
    </aside>
  );
}

function ReportPanel({ report }) {
  return (
    <article className="panel report-panel">
      <div className="panel-heading">
        <h2>Credit Report</h2>
        {report && <span className={`risk-badge risk-${report.riskLevel.toLowerCase()}`}>{report.riskLevel}</span>}
      </div>
      {!report ? (
        <p className="empty">Select an application to view report details.</p>
      ) : (
        <>
          <div className="report-head">
            <div>
              <h3>{report.fullName}</h3>
              <p>Application #{report.applicationId}</p>
            </div>
            <strong>{report.score}</strong>
          </div>
          <MetricGrid
            metrics={[
              ["Income", money(report.monthlyIncome)],
              ["Expenses", money(report.monthlyExpenses)],
              ["Debt", money(report.existingDebtPayment)],
              ["Capacity", money(report.repaymentCapacity)],
              ["Suggested limit", money(report.suggestedLoanLimit)],
              ["Status", report.status],
            ]}
          />
          <FactorList title="Positive factors" factors={report.positiveFactors} />
          <FactorList title="Risk factors" factors={report.riskFactors} />
        </>
      )}
    </article>
  );
}

function MetricGrid({ metrics }) {
  return (
    <dl className="metric-grid">
      {metrics.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function FactorList({ title, factors }) {
  return (
    <section className="factor-block">
      <h3>{title}</h3>
      {factors.length === 0 ? (
        <p className="empty compact">None</p>
      ) : (
        <ul>
          {factors.map((factor) => (
            <li key={factor}>{factor}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function money(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0));
}

createRoot(document.getElementById("root")).render(<App />);
